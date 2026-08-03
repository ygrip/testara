package io.github.ygrip.testara.core.scan;

import io.github.ygrip.testara.core.support.Stopwatch;
import io.github.ygrip.testara.core.time.DurationParser;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.ScanResult;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * <p>ClassScanner class.</p>
 * Enhanced version with persistent caching, async initialization, and memory optimization.
 *
 * @author yunaz.ramadhan on 12/12/2024
 * @version $Id: $Id
 */
@Log4j2
public final class ClassScanner {

  /**
   * Deterministic, collision-safe cache key. Replaces the old {@code String} key that was built
   * by concatenating an optional explicit key with a {@code Set<String>.hashCode()} — that never
   * included {@code interfaceOrSuperclass}/{@code annotationType} and folded an unstable-iteration
   * package set into a lossy int hash, so different scans could collide and equivalent scans
   * (same inputs, different package-set iteration order) could miss the cache.
   * <p>
   * The explicit {@code key} parameter (used e.g. by {@code DataHolderInstance} for
   * "request-data"/"response-data") is retained as an extra discriminator for backward
   * source-compatibility, even though base type + annotation type now make collisions
   * structurally impossible without it.
   */
  private record ScanKey(String explicitKey, ClassLoader classLoader, Class<?> baseType,
                         Class<? extends Annotation> annotationType, List<String> packages) {
    ScanKey {
      packages = packages == null ? List.of() : List.copyOf(new TreeSet<>(packages));
    }
  }

  private static final ConcurrentMap<ScanKey, CompletableFuture<List<Class<?>>>> CACHE = new ConcurrentHashMap<>();
  private static final ExecutorService SCAN_EXECUTOR =
      Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("classgraph-scan-", 0).factory());

  private static final Semaphore SCAN_LIMIT = new Semaphore(Math.max(4, Runtime.getRuntime().availableProcessors()));

  // Performance monitoring and adaptive settings
  private static final int MAX_CACHE_SIZE = 1000;
  // Configurable reject packages - loaded from configuration
  private static final ConcurrentMap<ScanKey, Long> CACHE_USAGE_TIMESTAMPS = new ConcurrentHashMap<>();

  static {

    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      log.debug("Shutting down class loader executors...");

      // Shutdown both executors
      SCAN_EXECUTOR.shutdown();

      try {
        if (!SCAN_EXECUTOR.awaitTermination(5, TimeUnit.SECONDS)) {
          SCAN_EXECUTOR.shutdownNow();
        }
      } catch (InterruptedException e) {
        SCAN_EXECUTOR.shutdownNow();
        Thread.currentThread().interrupt();
      }
    }));
  }

  private final ClassScannerConfig config;

  public ClassScanner(ClassScannerConfig config) {
    this.config = config;
  }

  private static void cleanupOldCacheEntries() {
    if (CACHE_USAGE_TIMESTAMPS.size() <= MAX_CACHE_SIZE) {
      return;
    }

    // Find oldest entries
    long cutoffTime = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1); // 1 hour cutoff

    Set<ScanKey> toRemove = CACHE_USAGE_TIMESTAMPS.entrySet()
        .stream()
        .filter(entry -> entry.getValue() < cutoffTime)
        .map(Map.Entry::getKey)
        .collect(Collectors.toSet());

    // Remove old entries
    toRemove.forEach(key -> {
      CACHE.remove(key);
      CACHE_USAGE_TIMESTAMPS.remove(key);
    });

    log.debug("Cleaned up {} old cache entries", toRemove.size());
  }

  private CompletableFuture<List<Class<?>>> scanAsync(ScanKey key, Supplier<List<Class<?>>> scan) {
    boolean[] cacheMiss = {false};
    CompletableFuture<List<Class<?>>> future = CACHE.computeIfAbsent(key, k -> {
      cacheMiss[0] = true;
      return CompletableFuture.supplyAsync(() -> {
        try {
          SCAN_LIMIT.acquire();
          return scan.get();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new RuntimeException(e);
        } finally {
          SCAN_LIMIT.release();
        }
      }, SCAN_EXECUTOR);
    });
    log.debug("Scan cache {} for baseType={}, annotationType={}, packages={}",
        cacheMiss[0] ? "miss" : "hit", key.baseType(), key.annotationType(), key.packages());
    return future;
  }

  /**
   * Async version with custom packages
   */
  public CompletableFuture<List<Class<?>>> scanOnPackages(String key,
      Class<?> interfaceOrSuperclass,
      Class<? extends Annotation> annotationClass,
      Set<String> customScanPackages) {
    String explicitKey = StringUtils.isBlank(key) ? null : key;
    ClassLoader classLoader = interfaceOrSuperclass != null
        ? interfaceOrSuperclass.getClassLoader()
        : Thread.currentThread().getContextClassLoader();
    ScanKey cacheKey = new ScanKey(explicitKey, classLoader, interfaceOrSuperclass, annotationClass,
        customScanPackages == null ? List.of() : List.copyOf(customScanPackages));

    // Update cache usage timestamp
    CACHE_USAGE_TIMESTAMPS.put(cacheKey, System.currentTimeMillis());

    // Create async scan
    return scanAsync(cacheKey,
        () -> performOptimizedScan(cacheKey, interfaceOrSuperclass, annotationClass, customScanPackages));
  }

  /**
   * Async version with custom packages
   */
  public CompletableFuture<List<Class<?>>> scanOnPackages(Class<?> interfaceOrSuperclass,
      Class<? extends Annotation> annotationClass,
      Set<String> customScanPackages) {
    return scanOnPackages(null, interfaceOrSuperclass, annotationClass, customScanPackages);
  }

  public CompletableFuture<List<Class<?>>> scanOnPackages(Class<? extends Annotation> annotationClass,
      Set<String> customScanPackages) {
    return scanOnPackages(null, null, annotationClass, customScanPackages);
  }

  /**
   * Perform optimized scan with custom package locations
   */
  private List<Class<?>> performOptimizedScan(ScanKey cacheKey,
      Class<?> interfaceOrSuperclass,
      Class<? extends Annotation> annotationClass,
      Set<String> packages) {

    Stopwatch stopwatch = Stopwatch.start();

    // Clean up old cache entries if needed
    if (CACHE.size() > MAX_CACHE_SIZE) {
      cleanupOldCacheEntries();
    }

    // Enhanced ClassGraph configuration with custom scan packages
    ClassGraph classGraph = new ClassGraph().enableClassInfo()
        .enableAnnotationInfo()
        .ignoreParentClassLoaders()
        .disableNestedJarScanning()
        .disableRuntimeInvisibleAnnotations()
        .rejectPackages(config.rejectPackages().toArray(new String[0]))
        .acceptPackages(packages.toArray(new String[0]))
        .setMaxBufferedJarRAMSize(config.maxBuffer())
        .removeTemporaryFilesAfterScan();

    // Add performance optimizations based on configuration
    if (config.enableParallelScanning()) {
      classGraph = classGraph.enableMultiReleaseVersions();
    }

    List<Class<?>> result;

    try (ScanResult scanResult = classGraph.scan()) {
      ClassInfoList classInfoList = scanResult.getAllClasses();

      // Apply filters in order of selectivity (most restrictive first)
      classInfoList =
          classInfoList.filter(classInfo -> !classInfo.isAbstract() && !classInfo.isInterface() && !classInfo.isEnum()
              && !classInfo.isAnnotation());


      if (annotationClass != null) {
        classInfoList = classInfoList.filter(classInfo -> classInfo.hasAnnotation(annotationClass));
      }

      if (interfaceOrSuperclass != null) {
        // Apply inheritance/interface filter
        if (interfaceOrSuperclass.isInterface()) {
          classInfoList = classInfoList.filter(classInfo -> classInfo.implementsInterface(interfaceOrSuperclass));
        } else {
          classInfoList = classInfoList.filter(classInfo -> classInfo.extendsSuperclass(interfaceOrSuperclass));
        }
      }

      // Load classes efficiently in batch
      result = classInfoList.directOnly().loadClasses(true);

      // Filter out any null classes that failed to load
      result = result.stream().filter(Objects::nonNull).collect(Collectors.toList());

    } catch (Exception e) {
      log.warn("Error during optimized scan for {}: {}", cacheKey, e.getMessage());
      result = Collections.emptyList();
    }

    CACHE.put(cacheKey, CompletableFuture.completedFuture(result));

    log.info("#Optimized scan for baseType={}, annotationType={} completed in {}, found {} classes in packages: {}",
        cacheKey.baseType(),
        cacheKey.annotationType(),
        DurationParser.formatDuration(stopwatch.stop().elapsed(TimeUnit.NANOSECONDS)),
        result.size(),
        packages);

    return result;
  }

  public CompletableFuture<List<Class<?>>> scan(String key,
      Class<?> interfaceOrSuperclass,
      Class<? extends Annotation> annotationClass) {
    return scanOnPackages(key, interfaceOrSuperclass, annotationClass, config.scanLocations(key));
  }

  public CompletableFuture<List<Class<?>>> scan(String key, Class<? extends Annotation> annotationClass) {
    return scanOnPackages(key, null, annotationClass, config.scanLocations(key));
  }

  public CompletableFuture<List<Class<?>>> scan(Class<? extends Annotation> annotationClass) {
    return scan(null, null, annotationClass);
  }

  public CompletableFuture<List<Class<?>>> scan(Class<?> interfaceOrSuperclass,
      Class<? extends Annotation> annotationClass) {
    return scan(null, interfaceOrSuperclass, annotationClass);
  }
} 