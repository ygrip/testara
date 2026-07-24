package io.github.ygrip.testara.core.factory;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import io.github.ygrip.testara.core.BaseTests;
import io.github.ygrip.testara.core.TestWith;
import io.github.ygrip.testara.core.config.TestConfiguration;
import io.github.ygrip.testara.core.context.Inject;
import io.github.ygrip.testara.core.context.TestContext;
import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.core.converter.ObjectConverter;
import io.github.ygrip.testara.core.error.AmbiguousConstructorException;
import io.github.ygrip.testara.core.error.CircularDependencyException;
import io.github.ygrip.testara.core.function.MethodInterceptionPostProcessor;
import io.github.ygrip.testara.core.model.RetryableMethod;

@Tag("factory")
@TestWith(properties = {"classpath:application.properties", "classpath:configuration.properties"})
@Execution(ExecutionMode.SAME_THREAD)
class InstanceResolverTests extends BaseTests {

  private RequiredDependency requiredDependency;

  @BeforeEach
  void setUp() {
    PostProcessorRegistry.instance()
      .clear();
    ConstructorInjectedRetryableService.CONSTRUCTIONS.set(0);
    requiredDependency = new RequiredDependency("ready");
    TestFramework.initialize(new ResolverTestContext(Map.of(RequiredDependency.class, requiredDependency)));
  }

  @AfterEach
  void tearDown() {
    PostProcessorRegistry.instance()
      .shutdown();
    TestFramework.clear();
  }

  @Test
  void usesTheOnlyConstructorByDefault() {
    SingleConstructorComponent component = new InstanceResolver().resolve(SingleConstructorComponent.class);

    assertThat(component.created, is(true));
  }

  @Test
  void usesTheSingleInjectAnnotatedConstructor() {
    ExplicitConstructorComponent component = new InstanceResolver().resolve(ExplicitConstructorComponent.class);

    assertThat(component.selected, is("injected"));
  }

  @Test
  void rejectsMultipleUnannotatedConstructors() {
    AmbiguousConstructorException exception =
      assertThrows(AmbiguousConstructorException.class, () -> new InstanceResolver().resolve(AmbiguousComponent.class));

    assertThat(exception.getMessage(), containsString("Annotate exactly one constructor with @Inject"));
  }

  @Test
  void reportsCircularDependenciesInResolutionOrder() {
    CircularDependencyException exception =
      assertThrows(CircularDependencyException.class, () -> new InstanceResolver().resolve(CycleA.class));

    assertThat(exception.getMessage(), containsString("CycleA -> CycleB -> CycleA"));
  }

  @Test
  void constructsProxyOnceWithResolvedDependencies() {
    MethodInterceptionPostProcessor processor = new MethodInterceptionPostProcessor();
    processor.configure(() -> null, null);
    PostProcessorRegistry.instance()
      .register(processor);

    ConstructorInjectedRetryableService service =
      new InstanceResolver().resolve(ConstructorInjectedRetryableService.class);

    assertThat(
      service.getClass()
        .getName(), containsString("$ByteBuddy$")
    );
    assertThat(service.dependency(), is(sameInstance(requiredDependency)));
    assertThat(service.execute(), is("ready"));
    assertThat(ConstructorInjectedRetryableService.CONSTRUCTIONS.get(), is(1));
  }

  @Test
  void reusesGeneratedProxyClass() throws NoSuchMethodException {
    MethodInterceptionPostProcessor processor = new MethodInterceptionPostProcessor();

    processor.configure(() -> null, null);

    var constructor = ConstructorInjectedRetryableService.class.getDeclaredConstructor(RequiredDependency.class);

    Class<? extends ConstructorInjectedRetryableService> first =
      processor.processType(ConstructorInjectedRetryableService.class, constructor);

    Class<? extends ConstructorInjectedRetryableService> second =
      processor.processType(ConstructorInjectedRetryableService.class, constructor);

    assertThat(first.getName(), containsString("$ByteBuddy$"));
    assertThat(first, sameInstance(second));

    // processType generates a class; it must not create an instance.
    assertThat(ConstructorInjectedRetryableService.CONSTRUCTIONS.get(), is(0));
  }

  static final class ResolverTestContext implements TestContext {
    private final Map<Class<?>, Object> fixedInstances;

    ResolverTestContext(Map<Class<?>, Object> fixedInstances) {
      this.fixedInstances = fixedInstances;
    }

    @Override
    public ObjectFactory factory() {
      return new DefaultObjectFactory();
    }

    @Override
    public ObjectConverter converter() {
      return null;
    }

    @Override
    public TestConfiguration configuration() {
      return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Class<T> type) {
      Object fixed = fixedInstances.get(type);
      if (fixed != null) {
        return (T) fixed;
      }
      return new InstanceResolver().resolve(type);
    }

    @Override
    public boolean has(Class<?> type) {
      return fixedInstances.containsKey(type);
    }
  }


  static final class SingleConstructorComponent {
    final boolean created;

    SingleConstructorComponent() {
      this.created = true;
    }
  }


  static final class ExplicitConstructorComponent {
    final String selected;

    ExplicitConstructorComponent(String ignored) {
      this.selected = "wrong";
    }

    @Inject
    ExplicitConstructorComponent() {
      this.selected = "injected";
    }
  }


  static final class AmbiguousComponent {
    AmbiguousComponent() {
    }

    AmbiguousComponent(String ignored) {
    }
  }


  static final class CycleA {
    CycleA(CycleB cycleB) {
    }
  }


  static final class CycleB {
    CycleB(CycleA cycleA) {
    }
  }


  public static final class RequiredDependency {
    private final String value;

    RequiredDependency(String value) {
      this.value = value;
    }
  }


  public static class ConstructorInjectedRetryableService {
    static final AtomicInteger CONSTRUCTIONS = new AtomicInteger();
    private final RequiredDependency dependency;

    public ConstructorInjectedRetryableService(RequiredDependency dependency) {
      this.dependency = Objects.requireNonNull(dependency, "dependency");
      CONSTRUCTIONS.incrementAndGet();
    }

    RequiredDependency dependency() {
      return dependency;
    }

    @RetryableMethod
    public String execute() {
      return dependency.value;
    }
  }
}
