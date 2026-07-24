package io.github.ygrip.testara.core.function;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import io.github.ygrip.testara.core.context.TestContext;
import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.core.error.ProxyCreationException;
import io.github.ygrip.testara.core.factory.InstantiationPostProcessor;
import io.github.ygrip.testara.core.model.RetryableMethod;
import lombok.extern.log4j.Log4j2;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.dynamic.scaffold.subclass.ConstructorStrategy;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.matcher.ElementMatchers;

/**
 * Framework-agnostic post processor that applies ByteBuddy interception
 * to classes with @RetryableMethod annotated methods.
 * <p>
 * This implementation:
 * - Uses ByteBuddy to create proxy classes that intercept annotated methods
 * - Caches class analysis results for performance
 * - Uses dynamic collector lookup to support TEST-scoped collectors
 * - Works with both Spring and non-Spring environments
 * <p>
 * The collector is looked up dynamically from TestFramework.context() each time,
 * allowing this post processor to be a singleton while using test-scoped collectors.
 * <p>
 * Selects a Byte Buddy subclass before component construction so intercepted
 * components are created with their real constructor dependencies.
 */
@Log4j2
public class MethodInterceptionPostProcessor implements InstantiationPostProcessor {

  private static final ReentrantLock INTERCEPTOR_LOCK = new ReentrantLock();
  private static final ConcurrentMap<Class<?>, Boolean> HAS_RETRYABLE_CACHE = new ConcurrentHashMap<>(256);

  private final ConcurrentMap<Class<?>, Class<?>> proxyTypeCache = new ConcurrentHashMap<>(64);

  private Supplier<MethodInvocationCollector> explicitCollectorSupplier;
  private Set<String> whitelistedPackages;
  private volatile DynamicRetryableMethodInterceptor dynamicInterceptor;

  /**
   * Default constructor for programmatic registration.
   */
  public MethodInterceptionPostProcessor() {
  }

  public static void clearCache() {
    HAS_RETRYABLE_CACHE.clear();
  }

  public void configure(Supplier<MethodInvocationCollector> collectorSupplier, Set<String> whitelistedPackages) {
    if (!proxyTypeCache.isEmpty()) {
      throw new IllegalStateException("MethodInterceptionPostProcessor cannot be reconfigured after creating proxies");
    }
    this.explicitCollectorSupplier = collectorSupplier;
    this.whitelistedPackages = whitelistedPackages;
    log.debug(
      "MethodInterceptionPostProcessor configured with {} whitelisted packages, explicit supplier: {}",
      whitelistedPackages != null ? whitelistedPackages.size() : 0,
      collectorSupplier != null
    );
  }

  @Override
  public int priority() {
    return 100;
  }

  @Override
  public boolean supports(Class<?> type) {
    if (type == null || type.isInterface() || type.isPrimitive() || type.isArray()) {
      return false;
    }

    String packageName = type.getPackageName();
    if (packageName.startsWith("java.") || packageName.startsWith("javax.") || packageName.startsWith("sun.")
      || packageName.startsWith("com.sun.") || packageName.startsWith("net.bytebuddy.")) {
      return false;
    }

    if (whitelistedPackages != null && !whitelistedPackages.isEmpty()) {
      boolean inWhitelist = whitelistedPackages.stream()
        .anyMatch(packageName::startsWith);
      if (!inWhitelist) {
        return false;
      }
    }

    return HAS_RETRYABLE_CACHE.computeIfAbsent(type, this::hasRetryableMethod);
  }

  @Override
  public <T> Class<? extends T> processType(Class<T> requestedType, Constructor<?> selectedConstructor) {
    if (!supports(requestedType)) {
      return requestedType;
    }

    validateProxyTarget(requestedType);
    Class<?> proxyType = proxyTypeCache.computeIfAbsent(requestedType, this::createProxyType);

    try {
      proxyType.getDeclaredConstructor(selectedConstructor.getParameterTypes());
    } catch (NoSuchMethodException e) {
      throw new ProxyCreationException(
        "Generated proxy " + proxyType.getName() + " does not expose the selected constructor "
          + selectedConstructor.toGenericString(), e
      );
    }

    return proxyType.asSubclass(requestedType);
  }

  /**
   * Instance processing is now only a guard. A valid proxy has already been
   * constructed by {@code InstanceResolver}; rebuilding an existing instance
   * and copying its fields is intentionally unsupported.
   */
  @Override
  public <T> T postProcess(T instance, Class<T> instanceType) {
    if (instance == null || isGeneratedProxy(instance.getClass())) {
      return instance;
    }
    if (supports(instanceType)) {
      throw new ProxyCreationException("Cannot safely proxy an existing " + instanceType.getName()
        + ". Resolve it through TestContext/ObjectFactory so the proxy is "
        + "selected before constructor invocation.");
    }
    return instance;
  }

  private Class<?> createProxyType(Class<?> targetClass) {
    try {
      DynamicRetryableMethodInterceptor interceptor = getOrCreateDynamicInterceptor();
      MethodHandles.Lookup targetLookup = MethodHandles.privateLookupIn(targetClass, MethodHandles.lookup());

      Class<?> proxyType = new ByteBuddy().subclass(targetClass, ConstructorStrategy.Default.IMITATE_SUPER_CLASS)
        .method(ElementMatchers.isAnnotatedWith(RetryableMethod.class))
        .intercept(MethodDelegation.to(interceptor))
        .make()
        .load(targetClass.getClassLoader(), ClassLoadingStrategy.UsingLookup.of(targetLookup))
        .getLoaded();

      log.debug("Created method interception proxy type for: {}", targetClass.getName());
      return proxyType;
    } catch (ProxyCreationException e) {
      throw e;
    } catch (Exception e) {
      throw new ProxyCreationException("Failed to create method interception proxy for " + targetClass.getName(), e);
    }
  }

  private void validateProxyTarget(Class<?> targetClass) {
    if (Modifier.isFinal(targetClass.getModifiers())) {
      throw new ProxyCreationException("Cannot proxy final class " + targetClass.getName());
    }

    Arrays.stream(targetClass.getDeclaredMethods())
      .filter(method -> method.isAnnotationPresent(RetryableMethod.class))
      .forEach(this::validateRetryableMethod);
  }

  private void validateRetryableMethod(Method method) {
    int modifiers = method.getModifiers();
    if (Modifier.isPrivate(modifiers) || Modifier.isStatic(modifiers) || Modifier.isFinal(modifiers)
      || Modifier.isAbstract(modifiers)) {
      throw new ProxyCreationException(
        "@RetryableMethod cannot intercept non-overridable method " + method.toGenericString());
    }
  }

  private boolean hasRetryableMethod(Class<?> targetClass) {
    return Arrays.stream(targetClass.getDeclaredMethods())
      .anyMatch(method -> method.isAnnotationPresent(RetryableMethod.class));
  }

  private boolean isGeneratedProxy(Class<?> type) {
    return type.getName()
      .contains("$ByteBuddy$");
  }

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

  private MethodInvocationCollector resolveCollector() {
    if (explicitCollectorSupplier != null) {
      return explicitCollectorSupplier.get();
    }

    try {
      TestContext context = TestFramework.context();
      return context.get(MethodInvocationCollector.class);
    } catch (IllegalStateException e) {
      log.trace("TestFramework context not available, skipping collector lookup");
      return null;
    }
  }

  @Override
  public void shutdown() {
    proxyTypeCache.clear();
    HAS_RETRYABLE_CACHE.clear();
    dynamicInterceptor = null;
    log.debug("MethodInterceptionPostProcessor shutdown");
  }
}

