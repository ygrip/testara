package io.github.ygrip.testara.core.scan;

import io.github.ygrip.testara.core.BaseTests;
import io.github.ygrip.testara.core.TestWith;
import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.core.model.DefaultData;
import io.github.ygrip.testara.core.model.RequestData;
import io.github.ygrip.testara.core.model.ResponseData;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;

/**
 * Regression coverage for Bug 4: {@link ClassScanner}'s cache key must be deterministic and must
 * never let unrelated scans collide, while equivalent scans (same base type, same annotation,
 * same package set regardless of iteration order) must always hit the same cache entry.
 */
@Tag("scan")
@Execution(ExecutionMode.SAME_THREAD)
@TestWith(properties = {"classpath:application.properties", "classpath:configuration.properties"})
public class ClassScannerCacheKeyTests extends BaseTests {

  @Test
  void packageOrderingDoesNotChangeCacheKey() {
    ClassScanner scanner = TestFramework.context().get(ClassScanner.class);

    Set<String> insertionOrderA = new LinkedHashSet<>(
        List.of("io.github.ygrip.testara.core.data", "io.github.ygrip.testara.core.model"));
    Set<String> insertionOrderB = new LinkedHashSet<>(
        List.of("io.github.ygrip.testara.core.model", "io.github.ygrip.testara.core.data"));

    CompletableFuture<List<Class<?>>> first =
        scanner.scanOnPackages("pkg-order-key", DefaultData.class, RequestData.class, insertionOrderA);
    CompletableFuture<List<Class<?>>> second =
        scanner.scanOnPackages("pkg-order-key", DefaultData.class, RequestData.class, insertionOrderB);

    assertThat("same base type/annotation/explicit key with the same package SET (different "
        + "iteration order) must hit the same cache entry, never re-trigger ClassGraph",
        second, sameInstance(first));
  }

  @Test
  void differentBaseTypesProduceDifferentCacheEntries() {
    ClassScanner scanner = TestFramework.context().get(ClassScanner.class);
    Set<String> packages = Set.of("io.github.ygrip.testara.core.model");

    CompletableFuture<List<Class<?>>> withBaseType =
        scanner.scanOnPackages("base-type-key", DefaultData.class, RequestData.class, packages);
    CompletableFuture<List<Class<?>>> withoutBaseType =
        scanner.scanOnPackages("base-type-key", null, RequestData.class, packages);

    assertThat(withoutBaseType, not(sameInstance(withBaseType)));
  }

  @Test
  void differentAnnotationsProduceDifferentCacheEntries() {
    ClassScanner scanner = TestFramework.context().get(ClassScanner.class);
    Set<String> packages = Set.of("io.github.ygrip.testara.core.model");

    CompletableFuture<List<Class<?>>> requestScan =
        scanner.scanOnPackages("annotation-key", DefaultData.class, RequestData.class, packages);
    CompletableFuture<List<Class<?>>> responseScan =
        scanner.scanOnPackages("annotation-key", DefaultData.class, ResponseData.class, packages);

    assertThat(responseScan, not(sameInstance(requestScan)));
  }

  @Test
  void repeatedIdenticalScansReturnTheSameCachedFuture() {
    ClassScanner scanner = TestFramework.context().get(ClassScanner.class);

    CompletableFuture<List<Class<?>>> first = scanner.scan("repeat-key", DefaultData.class, RequestData.class);
    CompletableFuture<List<Class<?>>> second = scanner.scan("repeat-key", DefaultData.class, RequestData.class);

    assertThat(second, sameInstance(first));
  }

  @Test
  void concurrentIdenticalScansTriggerOnlyOneClassGraphExecution() throws Exception {
    ClassScanner scanner = TestFramework.context().get(ClassScanner.class);
    Set<String> packages = Set.of("io.github.ygrip.testara.core.data");
    int concurrency = 16;

    ExecutorService pool = Executors.newFixedThreadPool(concurrency);
    CountDownLatch ready = new CountDownLatch(concurrency);
    CountDownLatch go = new CountDownLatch(1);
    List<CompletableFuture<List<Class<?>>>> results = Collections.synchronizedList(new ArrayList<>());

    try {
      for (int i = 0; i < concurrency; i++) {
        pool.submit(() -> {
          ready.countDown();
          try {
            go.await(10, TimeUnit.SECONDS);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          results.add(scanner.scanOnPackages("concurrent-key", DefaultData.class, RequestData.class, packages));
        });
      }
      ready.await(10, TimeUnit.SECONDS);
      go.countDown();
    } finally {
      pool.shutdown();
      assertThat(pool.awaitTermination(10, TimeUnit.SECONDS), equalTo(true));
    }

    assertThat(results.size(), equalTo(concurrency));
    CompletableFuture<List<Class<?>>> reference = results.get(0);
    for (CompletableFuture<List<Class<?>>> result : results) {
      // Every concurrent caller got the exact same future instance - proving only one
      // ClassGraph scan supplier was ever created (computeIfAbsent ran its factory once).
      assertThat(result, sameInstance(reference));
    }
  }
}
