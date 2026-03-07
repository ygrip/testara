package io.github.ygrip.testara.engine.descriptor;

import io.github.ygrip.testara.engine.support.TestDescriptorOrderUtils;
import io.github.ygrip.testara.engine.extension.TestaraExtension;
import io.github.ygrip.testara.engine.extension.TestaraExtensionContext;
import io.github.ygrip.testara.engine.context.TestaraCucumberEngineExecutionContext;
import io.github.ygrip.testara.engine.option.TestaraCucumberEngineOptions;
import io.cucumber.core.gherkin.Pickle;
import io.cucumber.core.resource.ClasspathSupport;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.junit.platform.engine.ConfigurationParameters;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.TestTag;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.support.config.PrefixedConfigurationParameters;
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor;
import org.junit.platform.engine.support.descriptor.ClasspathResourceSource;
import org.junit.platform.engine.support.hierarchical.ExclusiveResource;
import org.junit.platform.engine.support.hierarchical.Node;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Comparator.comparing;

@Log4j2
public abstract class TestaraNodeDescriptor extends AbstractTestDescriptor implements Node<TestaraCucumberEngineExecutionContext> {

  private final ExecutionMode executionMode;
  private final TestaraCucumberEngineOptions options;
  private final TestaraFeatureOrigin origin;

  TestaraNodeDescriptor(TestaraCucumberEngineOptions options, TestaraFeatureOrigin origin, UniqueId uniqueId, String name, TestSource source) {
    super(uniqueId, name, source);
    this.options = options;
    this.origin = origin;
    this.executionMode = options.getExecutionMode();
  }

  Integer getOrder(){
    return TestDescriptorOrderUtils.getOrder(this);
  }

  public TestaraFeatureOrigin getOrigin(){
    return this.origin;
  }

  public Optional<TestaraNodeDescriptor> findChildById(UniqueId id) {
    return getChildren().stream()
        .map(node -> (TestaraNodeDescriptor) node)
        .filter(child -> child.getUniqueId().equals(id))
        .findAny();
  }

  void clearChildren() {
    List<TestDescriptor> children = new ArrayList<>(getChildren());
    children.forEach(this::removeChild);
  }

  void setChildren(List<TestDescriptor> children) {
    children.forEach(this::addChild);
  }

  TestaraNodeDescriptor sortChildren() {
    if (this instanceof ExamplesDescriptor
        || this instanceof ScenarioOutlineDescriptor) {
      List<TestDescriptor> sortedChild = new ArrayList<>(getChildren());
      sortedChild.sort(comparing(TestDescriptorOrderUtils::getOrder).thenComparing(TestDescriptorOrderUtils::getExclusiveResourceCount)
          .thenComparing(TestDescriptor::getDisplayName));
      clearChildren();
      setChildren(sortedChild);
    }
    return this;
  }

  public TestaraCucumberEngineOptions getOptions() {
    return this.options;
  }

  public ExecutionMode getExecutionMode() {
    return this.executionMode;
  }

  public static final class PickleDescriptor extends TestaraNodeDescriptor implements Node<TestaraCucumberEngineExecutionContext> {
    private final Pickle pickle;
    private final Set<TestTag> tags;
    private final Set<ExclusiveResource> exclusiveResources = new LinkedHashSet<>(0);

    public PickleDescriptor(TestaraCucumberEngineOptions options,
        TestaraFeatureOrigin origin,
        UniqueId uniqueId,
        String name,
        TestSource source,
        Pickle pickle) {
      super(options, origin, uniqueId, name, source);
      this.pickle = pickle;
      this.tags = this.getTags(pickle);
      this.tags.forEach((tag) -> {
        ExclusiveResourceOptions exclusiveResourceOptions =
            new ExclusiveResourceOptions(getOptions().getConfigurationParameters(),
                tag);
        Stream<ExclusiveResource> resources = exclusiveResourceOptions.exclusiveReadWriteResource()
            .map((resource) -> new ExclusiveResource(resource, ExclusiveResource.LockMode.READ_WRITE));
        resources.forEach(this.exclusiveResources::add);
        resources = exclusiveResourceOptions.exclusiveReadResource()
            .map((resource) -> new ExclusiveResource(resource, ExclusiveResource.LockMode.READ));
        resources.forEach(this.exclusiveResources::add);
      });
      this.exclusiveResources.addAll(getExclusiveResourcesByTags(getOptions().getConfigurationParameters(),
          pickle).values());
    }

    @Override
    public boolean mayRegisterTests() {
      return getOptions().stepNotifications();
    }

    private Map<String, ExclusiveResource> getExclusiveResourcesByTags(ConfigurationParameters parameters,
        Pickle pickle) {
      Map<String, ExclusiveResource> exclusiveResourceMap = new HashMap<>();

      String prefix = "cucumber.execution.group.by.tags.";
      int prefixLength = prefix.toCharArray().length;
      Set<String> keys = parameters.keySet();
      Map<String, Optional<String>> mappedKeys = new HashMap<>();
      for (String key : keys) {
        if (key.startsWith(prefix)) {
          mappedKeys.put(key.substring(prefixLength), parameters.get(key));
        }
      }

      if (!mappedKeys.isEmpty()) {
        mappedKeys.keySet().forEach(key -> {
          if (mappedKeys.get(key).isPresent()) {
            String filter = mappedKeys.get(key).get();
            if (getOptions().isValidTags(filter, pickle.getTags())) {
              exclusiveResourceMap.put(key, new ExclusiveResource(key, ExclusiveResource.LockMode.READ_WRITE));
            }
          }
        });
      }

      return exclusiveResourceMap;
    }

    Pickle getPickle() {
      return this.pickle;
    }

    private Set<TestTag> getTags(Pickle pickleEvent) {
      return pickleEvent.getTags()
          .stream()
          .map((tag) -> tag.substring(1))
          .filter(TestTag::isValid)
          .map(TestTag::create)
          .collect(Collectors.collectingAndThen(Collectors.toCollection(LinkedHashSet::new),
              Collections::unmodifiableSet));
    }

    public Type getType() {
      return getOptions().stepNotifications() ? Type.CONTAINER_AND_TEST : Type.TEST;
    }

    public SkipResult shouldBeSkipped(TestaraCucumberEngineExecutionContext context) {
      return Stream.of(this.shouldBeSkippedByTagFilter(context), this.shouldBeSkippedByNameFilter(context))
          .flatMap(Optional::stream)
          .filter(SkipResult::isSkipped)
          .findFirst()
          .orElseGet(SkipResult::doNotSkip);
    }

    private Optional<SkipResult> shouldBeSkippedByTagFilter(TestaraCucumberEngineExecutionContext context) {
      return context.getOptions()
          .tagFilter()
          .map((expression) -> expression.evaluate(this.pickle.getTags()) ?
              SkipResult.doNotSkip() :
              SkipResult.skip("'cucumber.filter.tags=" + expression + "' did not match this scenario"));
    }

    private Optional<SkipResult> shouldBeSkippedByNameFilter(TestaraCucumberEngineExecutionContext context) {
      return context.getOptions()
          .nameFilter()
          .map((pattern) -> pattern.matcher(this.pickle.getName()).matches() ?
              SkipResult.doNotSkip() :
              SkipResult.skip("'cucumber.filter.name=" + pattern + "' did not match this scenario"));
    }

    @SneakyThrows
    public TestaraCucumberEngineExecutionContext execute(TestaraCucumberEngineExecutionContext context,
        DynamicTestExecutor dynamicTestExecutor) {
      // Check for thread interruption before starting
      if (Thread.currentThread().isInterrupted()) {
        log.debug("Thread interrupted, skipping test execution: {}", this.pickle.getName());
        return context;
      }

      TestaraExtensionContext extensionContext =
          new TestaraExtensionContext(this, context.getOptions());

      List<TestaraExtension> extensions = context.getExtensions();

      try {
        for (TestaraExtension extension : extensions) {
          // Check for interruption before each extension
          if (Thread.currentThread().isInterrupted()) {
            log.debug("Thread interrupted during beforeEach");
            return context;
          }
          try {
            log.trace("Running beforeEach for {}", extension.getClass().getSimpleName());
            extension.beforeEach(extensionContext);
          } catch (Throwable t) {
            // Check if this is a shutdown exception
            if (isShutdownException(t)) {
              log.debug("beforeEach cancelled due to shutdown");
              return context;
            }
            log.trace("beforeEach failed for {}", extension.getClass().getSimpleName(), t);
          }
        }

        context.runTestCase(this.pickle, this);

      } catch (Throwable t) {
        // Check if this is a shutdown/interruption exception
        if (isShutdownException(t)) {
          log.debug("Test execution cancelled due to shutdown: {}", this.pickle.getName());
          return context;
        }
        log.trace("Error during test execution : {}", t.getMessage());
        throw t; // rethrow to mark test as failed
      } finally {
        for (TestaraExtension extension : extensions) {
          try {
            log.trace("Running afterEach for {}", extension.getClass().getSimpleName());
            extension.afterEach(extensionContext);
          } catch (Throwable t) {
            // Don't log shutdown exceptions as failures
            if (!isShutdownException(t)) {
              log.trace("afterEach failed for {}", extension.getClass().getSimpleName(), t);
            }
          }
        }
      }

      return context;
    }

    /**
     * Check if an exception is related to shutdown/interruption.
     */
    private boolean isShutdownException(Throwable t) {
      if (t == null) {
        return false;
      }
      if (t instanceof InterruptedException) {
        return true;
      }
      String className = t.getClass().getName();
      if (className.contains("ClosedByInterruptException") ||
          className.contains("RejectedExecutionException") ||
          className.contains("CancellationException")) {
        return true;
      }
      String message = t.getMessage();
      if (message != null && (message.contains("shutdown") ||
          message.contains("interrupted") ||
          message.contains("Executor is closed"))) {
        return true;
      }
      Throwable cause = t.getCause();
      if (cause != null && cause != t) {
        return isShutdownException(cause);
      }
      return false;
    }

    public Set<ExclusiveResource> getExclusiveResources() {
      return this.exclusiveResources;
    }

    public Set<TestTag> getTags() {
      return this.tags;
    }

    Optional<String> getPackage() {
      Optional<TestSource> source = this.getSource();
      Objects.requireNonNull(ClasspathResourceSource.class);
      source = source.filter(ClasspathResourceSource.class::isInstance);
      Objects.requireNonNull(ClasspathResourceSource.class);
      return source.map(ClasspathResourceSource.class::cast)
          .map(ClasspathResourceSource::getClasspathResourceName)
          .map(ClasspathSupport::packageNameOfResource);
    }

    private static final class ExclusiveResourceOptions {
      private final ConfigurationParameters parameters;

      ExclusiveResourceOptions(ConfigurationParameters parameters, TestTag tag) {
        this.parameters =
            new PrefixedConfigurationParameters(parameters, "cucumber.execution.exclusive-resources." + tag.getName());
      }

      public Stream<String> exclusiveReadWriteResource() {
        return this.parameters.get(".read-write", (s) -> Arrays.stream(s.split(",")).map(String::trim))
            .orElse(Stream.empty());
      }

      public Stream<String> exclusiveReadResource() {
        return this.parameters.get(".read", (s) -> Arrays.stream(s.split(",")).map(String::trim))
            .orElse(Stream.empty());
      }
    }
  }


  public static final class ScenarioOutlineDescriptor extends TestaraNodeDescriptor {
    ScenarioOutlineDescriptor(TestaraCucumberEngineOptions options, TestaraFeatureOrigin origin, UniqueId uniqueId, String name, TestSource source) {
      super(options, origin, uniqueId, name, source);
    }

    public Type getType() {
      return Type.CONTAINER;
    }
  }


  public static final class RuleDescriptor extends TestaraNodeDescriptor {
    RuleDescriptor(TestaraCucumberEngineOptions options, TestaraFeatureOrigin origin, UniqueId uniqueId, String name, TestSource source) {
      super(options, origin, uniqueId, name, source);
    }

    public Type getType() {
      return Type.CONTAINER;
    }
  }


  public static final class ExamplesDescriptor extends TestaraNodeDescriptor {
    ExamplesDescriptor(TestaraCucumberEngineOptions options, TestaraFeatureOrigin origin, UniqueId uniqueId, String name, TestSource source) {
      super(options, origin, uniqueId, name, source);
    }

    public Type getType() {
      return Type.CONTAINER;
    }
  }


  public static final class StepDescriptor extends TestaraNodeDescriptor {

    StepDescriptor(TestaraCucumberEngineOptions options, TestaraFeatureOrigin origin, UniqueId uniqueId, String name, TestSource source) {
      super(options, origin, uniqueId, name, source);
    }

    @Override
    public ExecutionMode getExecutionMode() {
      return ExecutionMode.SAME_THREAD;
    }

    @Override
    public Type getType() {
      return Type.TEST;
    }
  }
}

