package io.github.ygrip.testara.core.factory;

import io.github.ygrip.testara.core.context.TestFramework;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.ref.SoftReference;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Resolves object instances using constructor-based instantiation.
 * Handles recursive dependency resolution and circular dependency detection.
 * 
 * After instantiation, applies all registered InstancePostProcessors to enable
 * method interception, AOP, and other cross-cutting concerns.
 */
public final class InstanceResolver {
  private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

  private final static ThreadLocal<Set<Class<?>>> resolving = ThreadLocal.withInitial(HashSet::new);
  private final static Map<Class<?>, SoftReference<MethodHandle>> constructorHandleCache = new IdentityHashMap<>();
  
  // Flag to prevent infinite recursion during post-processing
  private final static ThreadLocal<Set<Class<?>>> postProcessing = ThreadLocal.withInitial(HashSet::new);

  /**
   * Resolve an instance of the given type using constructor injection.
   * After creation, applies registered post processors for method interception etc.
   *
   * @param type the class to instantiate
   * @param <T>  the type
   * @return a new instance (possibly a proxy)
   * @throws IllegalStateException if circular dependency is detected
   * @throws RuntimeException      if instantiation fails
   */
  @SuppressWarnings("unchecked")
  public <T> T resolve(Class<T> type) {
    // Detect circular dependencies
    if (resolving.get().contains(type)) {
      throw new IllegalStateException("Circular dependency detected: " + resolving.get() + " -> " + type);
    }

    resolving.get().add(type);
    try {
      MethodHandle candidate;
      SoftReference<MethodHandle> cache =
          constructorHandleCache.computeIfAbsent(type, v -> new SoftReference<>(selectConstructor(type)));

      if (cache.get() == null) {
        candidate = selectConstructor(type);
        constructorHandleCache.put(type, new SoftReference<>(candidate));
      } else {
        candidate = cache.get();
      }
      // Resolve all constructor parameters
      // For each parameter, check registry first, then delegate to factory
      Object[] params =
          Objects.requireNonNull(candidate).type().parameterList().stream().map(this::resolveDependency).toArray();

      T instance = (T) candidate.invokeWithArguments(params);
      
      // Apply post processors (e.g., method interception)
      // Skip if we're already post-processing this type to avoid infinite recursion
      if (!postProcessing.get().contains(type)) {
        postProcessing.get().add(type);
        try {
          instance = PostProcessorRegistry.instance().applyPostProcessors(instance, type);
        } finally {
          postProcessing.get().remove(type);
        }
      }
      
      return instance;
    } catch (Throwable e) {
      throw new RuntimeException("Failed to resolve instance of " + type.getName(), e);
    } finally {
      resolving.get().remove(type);
    }
  }

  /**
   * Resolve a single dependency.
   */
  private Object resolveDependency(Class<?> paramType) {
    // Delegate to factory (which will use constructor resolution)
    return TestFramework.context().get(paramType);
  }

  /**
   * Select the best constructor for instantiation.
   * Prefers constructors with more parameters (greedy resolution).
   *
   * @param type the class
   * @return the selected constructor
   * @throws IllegalStateException if no public constructor is found
   */
  private MethodHandle selectConstructor(Class<?> type) {
    Constructor<?> candidate = findUsableConstructor(type);
    Class<?>[] parameters = candidate.getParameterTypes();
    MethodHandle cons;
    try {
      cons = LOOKUP.findConstructor(type, MethodType.methodType(void.class, parameters));
    } catch (NoSuchMethodException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }

    return cons;
  }

  private Constructor<?> findUsableConstructor(Class<?> type) {
    return Arrays.stream(type.getDeclaredConstructors())
        .max(Comparator.comparingInt(Constructor::getParameterCount))
        .map(c -> {
          c.setAccessible(true);
          return c;
        })
        .orElseThrow(() -> new IllegalStateException("No constructor found for " + type.getName()));
  }
}
