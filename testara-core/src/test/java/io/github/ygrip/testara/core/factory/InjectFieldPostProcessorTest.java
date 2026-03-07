package io.github.ygrip.testara.core.factory;

import io.github.ygrip.testara.core.BaseTests;
import io.github.ygrip.testara.core.TestWith;
import io.github.ygrip.testara.core.context.Inject;
import io.github.ygrip.testara.core.context.TestComponent;
import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.core.registry.RegistryScope;
import io.github.ygrip.testara.core.registry.RootRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;

@Tag("field")
@TestWith(properties = {"classpath:application.properties", "classpath:configuration.properties"})
class InjectFieldPostProcessorTest extends BaseTests {

  @TestComponent(scope = RegistryScope.TEST)
  public static class SimpleDependency {
    private final String value = "test";

    public String getValue() {
      return value;
    }
  }

  @TestComponent(scope = RegistryScope.TEST)
  public static class AnotherDependency {
    private final int number = 42;

    public int getNumber() {
      return number;
    }
  }

  // Test fixtures

  @TestComponent(scope = RegistryScope.TEST)
  public static class ServiceWithDependencies {
    @Inject
    private SimpleDependency dependency;

    public SimpleDependency getDependency() {
      return dependency;
    }
  }

  @TestComponent(scope = RegistryScope.TEST)
  public static class ServiceWithPresetDependency {
    @Inject
    private final SimpleDependency dependency = new SimpleDependency();

    public SimpleDependency getDependency() {
      return dependency;
    }
  }

  @TestComponent(scope = RegistryScope.TEST)
  public static class SimpleService {
    private final String name = "simple";

    public String getName() {
      return name;
    }
  }


  public static class ParentService {
    @Inject
    private SimpleDependency parentDependency;

    public SimpleDependency getParentDependency() {
      return parentDependency;
    }
  }

  @TestComponent(scope = RegistryScope.TEST)
  public static class ChildService extends ParentService {
    @Inject
    private AnotherDependency childDependency;

    public AnotherDependency getChildDependency() {
      return childDependency;
    }
  }


  @Nested
  @DisplayName("Field Injection")
  class FieldInjection {

    @Test
    @DisplayName("should inject dependencies into @Inject annotated fields")
    void shouldInjectDependenciesIntoFields() {
      // Given: A class with @Inject fields
      ServiceWithDependencies service = TestFramework.context().get(ServiceWithDependencies.class);

      // Then: Dependencies should be injected
      assertThat(service, is(notNullValue()));
      assertThat(service.getDependency(), is(notNullValue()));
    }

    @Test
    @DisplayName("should not overwrite existing field values")
    void shouldNotOverwriteExistingValues() {
      // Given: An instance with pre-set value
      InjectFieldPostProcessor processor = new InjectFieldPostProcessor();
      ServiceWithPresetDependency instance = new ServiceWithPresetDependency();
      SimpleDependency originalDependency = instance.getDependency();

      // When: Post-processing the instance
      ServiceWithPresetDependency processed = processor.postProcess(instance, ServiceWithPresetDependency.class);

      // Then: The original value should be preserved
      assertThat(processed.getDependency(), is(sameInstance(originalDependency)));
    }

    @Test
    @DisplayName("should support inherited @Inject fields")
    void shouldSupportInheritedFields() {
      // Given: A class that extends another with @Inject fields
      ChildService service = TestFramework.context().get(ChildService.class);

      // Then: Both parent and child dependencies should be injected
      assertThat(service, is(notNullValue()));
      assertThat(service.getParentDependency(), is(notNullValue()));
      assertThat(service.getChildDependency(), is(notNullValue()));
    }

    @Test
    @DisplayName("should handle classes without @Inject fields")
    void shouldHandleClassesWithoutInjectFields() {
      // Given: A class without @Inject fields
      InjectFieldPostProcessor processor = new InjectFieldPostProcessor();
      SimpleService instance = new SimpleService();

      // When: Post-processing the instance
      SimpleService processed = processor.postProcess(instance, SimpleService.class);

      // Then: Should return the same instance unchanged
      assertThat(processed, is(sameInstance(instance)));
    }
  }


  @Nested
  @DisplayName("PostProcessor Contract")
  class PostProcessorContract {

    @Test
    @DisplayName("should have correct priority")
    void shouldHaveCorrectPriority() {
      InjectFieldPostProcessor processor = new InjectFieldPostProcessor();

      // Priority 50 - runs after most processors but before method interception (100)
      assertThat(processor.priority(), is(50));
    }

    @Test
    @DisplayName("should support types with @Inject fields")
    void shouldSupportTypesWithInjectFields() {
      InjectFieldPostProcessor processor = new InjectFieldPostProcessor();

      assertThat(processor.supports(ServiceWithDependencies.class), is(true));
      assertThat(processor.supports(SimpleService.class), is(false));
    }

    @Test
    @DisplayName("should handle null instances gracefully")
    void shouldHandleNullInstances() {
      InjectFieldPostProcessor processor = new InjectFieldPostProcessor();

      ServiceWithDependencies result = processor.postProcess(null, ServiceWithDependencies.class);

      assertThat(result, is(nullValue()));
    }
  }
}
