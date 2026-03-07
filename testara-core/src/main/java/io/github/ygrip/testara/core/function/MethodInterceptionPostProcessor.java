package io.github.ygrip.testara.core.function;

import io.github.ygrip.testara.core.context.TestContext;
import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.core.factory.InstancePostProcessor;
import io.github.ygrip.testara.core.model.RetryableMethod;
import lombok.extern.log4j.Log4j2;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.matcher.ElementMatchers;
import org.apache.commons.lang3.reflect.FieldUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Framework-agnostic post processor that applies ByteBuddy interception
 * to classes with @RetryableMethod annotated methods.
 * 
 * This implementation:
 * - Uses ByteBuddy to create proxy classes that intercept annotated methods
 * - Caches class analysis results for performance
 * - Uses dynamic collector lookup to support TEST-scoped collectors
 * - Works with both Spring and non-Spring environments
 * 
 * The collector is looked up dynamically from TestFramework.context() each time,
 * allowing this post processor to be a singleton while using test-scoped collectors.
 */
@Log4j2
public class MethodInterceptionPostProcessor implements InstancePostProcessor {
  
  private static final ReentrantLock INTERCEPTOR_LOCK = new ReentrantLock();
  
  // Cache to avoid repeated reflection checks
  private static final ConcurrentMap<Class<?>, Boolean> HAS_RETRYABLE_CACHE = new ConcurrentHashMap<>(256);
  
  // Optional explicit supplier - if set, uses this instead of dynamic lookup
  private Supplier<MethodInvocationCollector> explicitCollectorSupplier;
  private Set<String> whitelistedPackages;
  
  // Shared interceptor that uses dynamic collector lookup
  private volatile DynamicRetryableMethodInterceptor dynamicInterceptor;

  /**
   * Default constructor for ServiceLoader.
   * Uses dynamic collector lookup via TestFramework.context().
   */
  public MethodInterceptionPostProcessor() {
    // For ServiceLoader - uses dynamic collector lookup
  }

  /**
   * Configure the post processor with explicit dependencies.
   * If not called, uses dynamic lookup via TestFramework.context().
   * 
   * @param collectorSupplier explicit supplier for MethodInvocationCollector (can be null for dynamic lookup)
   * @param whitelistedPackages packages to scan for retryable methods (can be null for all packages)
   */
  public void configure(Supplier<MethodInvocationCollector> collectorSupplier, Set<String> whitelistedPackages) {
    this.explicitCollectorSupplier = collectorSupplier;
    this.whitelistedPackages = whitelistedPackages;
    log.debug("MethodInterceptionPostProcessor configured with {} whitelisted packages, explicit supplier: {}", 
        whitelistedPackages != null ? whitelistedPackages.size() : 0,
        collectorSupplier != null);
  }

  @Override
  public int priority() {
    return 100; // High priority - run early
  }

  @Override
  public boolean supports(Class<?> type) {
    // Quick rejection checks first
    if (type == null || type.isInterface() || type.isPrimitive() || type.isArray()) {
      return false;
    }
    
    String packageName = type.getPackageName();
    
    // Skip infrastructure classes
    if (packageName.startsWith("java.") || 
        packageName.startsWith("javax.") ||
        packageName.startsWith("sun.") ||
        packageName.startsWith("com.sun.") ||
        packageName.startsWith("net.bytebuddy.")) {
      return false;
    }
    
    // Check whitelist if configured
    if (whitelistedPackages != null && !whitelistedPackages.isEmpty()) {
      boolean inWhitelist = whitelistedPackages.stream().anyMatch(packageName::startsWith);
      if (!inWhitelist) {
        return false;
      }
    }
    
    // Check if class has retryable methods
    return hasRetryableMethodCached(type);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> T postProcess(T instance, Class<T> instanceType) {
    if (instance == null) {
      return instance;
    }
    
    // Skip if already processed by ByteBuddy or is a proxy
    Class<?> actualClass = instance.getClass();
    if (actualClass.getName().contains("$ByteBuddy$") || Proxy.isProxyClass(actualClass)) {
      return instance;
    }
    
    // Handle CGLIB proxies (in case this runs in Spring environment)
    Class<?> targetClass = actualClass;
    if (targetClass.getName().contains("$$EnhancerBySpringCGLIB$$") ||
        targetClass.getName().contains("$$SpringCGLIB$$")) {
      targetClass = targetClass.getSuperclass();
    }
    
    // Double-check for retryable methods on the actual class
    if (!hasRetryableMethod(targetClass)) {
      return instance;
    }
    
    try {
      // Create ByteBuddy enhanced class using dynamic interceptor
      DynamicRetryableMethodInterceptor interceptor = getOrCreateDynamicInterceptor();
      
      Class<?> proxyType = new ByteBuddy()
          .subclass(targetClass)
          .method(ElementMatchers.isAnnotatedWith(RetryableMethod.class))
          .intercept(MethodDelegation.to(interceptor))
          .make()
          .load(actualClass.getClassLoader(), ClassLoadingStrategy.Default.WRAPPER)
          .getLoaded();
      
      // Create instance and copy fields
      Object proxy = createInstance(proxyType);
      copyFields(instance, proxy);
      
      log.debug("Created method interception proxy for: {}", instanceType.getSimpleName());
      return (T) proxy;
      
    } catch (Exception e) {
      log.warn("Failed to create method interception proxy for: {} - {}", 
          instanceType.getSimpleName(), e.getMessage());
      return instance;
    }
  }

  /**
   * Check if a class has any methods annotated with @RetryableMethod
   */
  private boolean hasRetryableMethod(Class<?> targetClass) {
    return Arrays.stream(targetClass.getDeclaredMethods())
        .anyMatch(method -> method.isAnnotationPresent(RetryableMethod.class));
  }

  /**
   * Cached version of hasRetryableMethod to avoid repeated reflection
   */
  private boolean hasRetryableMethodCached(Class<?> clazz) {
    return HAS_RETRYABLE_CACHE.computeIfAbsent(clazz, this::hasRetryableMethod);
  }

  /**
   * Get or create the dynamic interceptor (lazy, thread-safe).
   * The dynamic interceptor looks up the collector from context at invocation time.
   */
  private DynamicRetryableMethodInterceptor getOrCreateDynamicInterceptor() {
    if (dynamicInterceptor == null) {
      INTERCEPTOR_LOCK.lock();
      try {
        if (dynamicInterceptor == null) {
          dynamicInterceptor = new DynamicRetryableMethodInterceptor(this::resolveCollector);
          log.debug("Created DynamicRetryableMethodInterceptor");
        }
      } finally {
        INTERCEPTOR_LOCK.unlock();
      }
    }
    return dynamicInterceptor;
  }
  
  /**
   * Resolve the collector - either from explicit supplier or from TestFramework context.
   * This is called at method invocation time, allowing the collector to be test-scoped.
   */
  private MethodInvocationCollector resolveCollector() {
    // If explicit supplier is configured, use it
    if (explicitCollectorSupplier != null) {
      return explicitCollectorSupplier.get();
    }
    
    // Otherwise, try to get from TestFramework context
    try {
      TestContext context = TestFramework.context();
      return context.get(MethodInvocationCollector.class);
    } catch (IllegalStateException e) {
      // TestContext not initialized - return null, interception will be skipped
      log.trace("TestFramework context not available, skipping collector lookup");
      return null;
    }
  }

  /**
   * Create an instance of the proxy class
   */
  private Object createInstance(Class<?> clazz) throws Exception {
    try {
      // Try default constructor first
      Constructor<?> defaultConstructor = clazz.getDeclaredConstructor();
      defaultConstructor.setAccessible(true);
      return defaultConstructor.newInstance();
    } catch (NoSuchMethodException e) {
      // If no default constructor, try to find any constructor and use nulls
      Constructor<?>[] constructors = clazz.getDeclaredConstructors();
      if (constructors.length > 0) {
        Constructor<?> constructor = constructors[0];
        constructor.setAccessible(true);
        Object[] args = new Object[constructor.getParameterCount()];
        return constructor.newInstance(args);
      }
      throw new RuntimeException("Cannot create instance of " + clazz.getName());
    }
  }

  /**
   * Copy all field values from source to target
   */
  private void copyFields(Object source, Object target) {
    for (Field field : FieldUtils.getAllFieldsList(source.getClass())) {
      try {
        if (Modifier.isStatic(field.getModifiers())) {
          continue;
        }
        Object value = FieldUtils.readField(field, source, true);
        if (value != null) {
          FieldUtils.writeField(field, target, value, true);
        }
      } catch (IllegalAccessException ignored) {
        // Skip fields that can't be copied
      }
    }
  }

  @Override
  public void shutdown() {
    // Clear caches
    HAS_RETRYABLE_CACHE.clear();
    dynamicInterceptor = null;
    log.debug("MethodInterceptionPostProcessor shutdown");
  }
  
  /**
   * Clear the retryable method cache.
   * Useful for testing or when classes are reloaded.
   */
  public static void clearCache() {
    HAS_RETRYABLE_CACHE.clear();
  }
}

