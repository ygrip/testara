package io.github.ygrip.testara.core.factory;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Parameter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

import io.github.ygrip.testara.core.context.Inject;
import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.core.error.AmbiguousConstructorException;
import io.github.ygrip.testara.core.error.CircularDependencyException;
import io.github.ygrip.testara.core.error.ComponentCreationException;
import io.github.ygrip.testara.core.error.DependencyResolutionException;
import io.github.ygrip.testara.core.error.MissingComponentException;

/**
 * Resolves object instances using constructor injection.
 *
 * <p>Constructor selection is deliberately strict: a class must have one
 * constructor, or exactly one of its constructors must be annotated with
 * {@link Inject}. This keeps resolution deterministic.</p>
 */
public final class InstanceResolver {
  private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

  private static final ThreadLocal<Deque<Class<?>>> RESOLUTION_PATH = ThreadLocal.withInitial(ArrayDeque::new);

  private static final ClassValue<ConstructorPlan> CONSTRUCTOR_PLANS = new ClassValue<>() {
    @Override
    protected ConstructorPlan computeValue(Class<?> type) {
      return createConstructorPlan(type);
    }
  };

  private static void detectCircularDependency(Class<?> candidate, Deque<Class<?>> path) {
    if (!path.contains(candidate)) {
      return;
    }

    List<Class<?>> completePath = new ArrayList<>(path);
    int cycleStart = completePath.indexOf(candidate);
    List<Class<?>> cycle = new ArrayList<>(completePath.subList(cycleStart, completePath.size()));
    cycle.add(candidate);
    throw new CircularDependencyException(cycle);
  }

  private static ConstructorPlan createConstructorPlan(Class<?> type) {
    Constructor<?> constructor = selectConstructor(type);
    MethodHandle constructorHandle = unreflectConstructor(type, constructor.getParameterTypes());
    return new ConstructorPlan(constructor, constructor.getParameters(), constructorHandle);
  }

  private static Constructor<?> selectConstructor(Class<?> type) {
    Constructor<?>[] constructors = type.getDeclaredConstructors();
    if (constructors.length == 1) {
      return constructors[0];
    }

    List<Constructor<?>> injectedConstructors = Arrays.stream(constructors)
      .filter(constructor -> constructor.isAnnotationPresent(Inject.class))
      .toList();

    if (injectedConstructors.size() == 1) {
      return injectedConstructors.getFirst();
    }

    throw new AmbiguousConstructorException(type, constructors);
  }

  private static MethodHandle unreflectConstructor(Class<?> implementationType, Class<?>[] parameterTypes) {
    try {
      Constructor<?> implementationConstructor = implementationType.getDeclaredConstructor(parameterTypes);
      MethodHandles.Lookup privateLookup = MethodHandles.privateLookupIn(implementationType, LOOKUP);
      return privateLookup.unreflectConstructor(implementationConstructor);
    } catch (NoSuchMethodException | IllegalAccessException e) {
      throw new ComponentCreationException(implementationType, List.of(), e);
    }
  }

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
    Deque<Class<?>> path = RESOLUTION_PATH.get();
    detectCircularDependency(type, path);
    path.addLast(type);

    try {
      ConstructorPlan plan = CONSTRUCTOR_PLANS.get(type);
      Class<? extends T> implementationType = PostProcessorRegistry.instance()
        .resolveImplementationType(type, plan.constructor());

      Object[] arguments = Arrays.stream(plan.parameters())
        .map(Parameter::getType)
        .map(this::resolveDependency)
        .toArray();

      MethodHandle constructor = plan.constructorHandleFor(implementationType);
      T instance = (T) constructor.invokeWithArguments(arguments);
      return PostProcessorRegistry.instance()
        .applyPostProcessors(instance, type);
    } catch (DependencyResolutionException e) {
      throw e;
    } catch (Throwable e) {
      throw new ComponentCreationException(type, List.copyOf(path), e);
    } finally {
      path.removeLast();
      if (path.isEmpty()) {
        RESOLUTION_PATH.remove();
      }
    }
  }

  private Object resolveDependency(Class<?> dependencyType) {
    Deque<Class<?>> path = RESOLUTION_PATH.get();
    detectCircularDependency(dependencyType, path);

    try {
      return TestFramework.context()
        .get(dependencyType);
    } catch (MissingComponentException e) {
      if (e.componentType() != dependencyType || !e.dependencyPath()
        .isEmpty()) {
        throw e;
      }
      List<Class<?>> dependencyPath = new ArrayList<>(path);
      dependencyPath.add(dependencyType);
      throw new MissingComponentException(dependencyType, dependencyPath, e);
    }
  }


  private record ConstructorPlan(Constructor<?> constructor, Parameter[] parameters, MethodHandle constructorHandle) {
    private MethodHandle constructorHandleFor(Class<?> implementationType) {
      if (implementationType == constructor.getDeclaringClass()) {
        return constructorHandle;
      }
      return unreflectConstructor(implementationType, constructor.getParameterTypes());
    }
  }
}
