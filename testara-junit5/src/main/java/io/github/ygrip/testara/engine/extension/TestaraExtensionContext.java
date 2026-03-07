package io.github.ygrip.testara.engine.extension;

import io.github.ygrip.testara.engine.option.TestaraCucumberEngineOptions;
import org.junit.jupiter.api.MediaType;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExecutableInvoker;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestInstances;
import org.junit.jupiter.api.function.ThrowingConsumer;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestTag;
import org.junit.platform.engine.support.hierarchical.Node;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class TestaraExtensionContext implements ExtensionContext {
  private final String uniqueId;
  private final String displayName;
  private final Optional<Class<?>> testClass;
  private final Store store = new SimpleStore();
  private final TestaraCucumberEngineOptions options;
  private final Set<String> tags;
  private final ExecutionMode executionMode;

  public TestaraExtensionContext(TestDescriptor testDescriptor, TestaraCucumberEngineOptions options) {
    Set<String> tagsCollected;
    // Use the full unique ID string for proper scenario isolation
    // This ensures each scenario has its own scope for test data isolation
    this.uniqueId = testDescriptor.getUniqueId().toString();
    this.displayName = testDescriptor.getDisplayName();
    try {
      tagsCollected = testDescriptor.getTags()
          .stream()
          .map(TestTag::getName)
          .collect(Collectors.collectingAndThen(Collectors.toCollection(LinkedHashSet::new),
              Collections::unmodifiableSet));
    } catch (Exception ignored) {
      tagsCollected = Collections.emptySet();
    }
    ExecutionMode mode;
    if (testDescriptor instanceof Node) {
      mode = ExecutionMode.valueOf(((Node<?>) testDescriptor).getExecutionMode().name());
    } else {
      mode = null;
    }
    this.executionMode = mode;
    this.tags = tagsCollected;
    this.testClass = Optional.of(testDescriptor.getClass());
    this.options = options;
  }

  @Override
  public Optional<ExtensionContext> getParent() {
    return Optional.empty();
  }

  @Override
  public ExtensionContext getRoot() {
    return this;
  }

  @Override
  public String getUniqueId() {
    return uniqueId;
  }

  @Override
  public String getDisplayName() {
    return displayName;
  }

  @Override
  public Set<String> getTags() {
    return this.tags;
  }

  @Override
  public Optional<Class<?>> getTestClass() {
    return testClass;
  }

  @Override
  public List<Class<?>> getEnclosingTestClasses() {
    return List.of();
  }

  @Override
  public Optional<TestInstance.Lifecycle> getTestInstanceLifecycle() {
    return Optional.empty(); //not implemented
  }

  @Override
  public Optional<Object> getTestInstance() {
    return Optional.empty(); //not implemented
  }

  @Override
  public Optional<TestInstances> getTestInstances() {
    return Optional.empty(); //not implemented
  }

  @Override
  public Optional<Method> getTestMethod() {
    return Optional.empty(); //not implemented
  }

  @Override
  public Optional<AnnotatedElement> getElement() {
    return Optional.ofNullable(testClass.map(Class::getAnnotatedSuperclass).orElse(null));
  }

  @Override
  public Optional<Throwable> getExecutionException() {
    return Optional.empty(); //not implemented
  }

  @Override
  public Optional<String> getConfigurationParameter(String s) {
    return this.options.getConfigurationParameters().get(s);
  }

  @Override
  public <T> Optional<T> getConfigurationParameter(String s, Function<String, T> function) {
    return this.options.getConfigurationParameters().get(s, function);
  }

  @Override
  public void publishReportEntry(Map<String, String> map) {
    //not implemented
  }

  @Override
  public void publishFile(String name, MediaType mediaType, ThrowingConsumer<Path> action) {

  }

  @Override
  public void publishDirectory(String name, ThrowingConsumer<Path> action) {

  }

  @Override
  public Store getStore(Namespace namespace) {
    return store;
  }

  @Override
  public Store getStore(StoreScope scope, Namespace namespace) {
    return null;
  }

  @Override
  public ExecutionMode getExecutionMode() {
    return this.executionMode;
  }

  @Override
  public ExecutableInvoker getExecutableInvoker() {
    return null; //not implemented
  }

  // Minimal store implementation
  static class SimpleStore implements Store {
    private final Map<Object, Object> data = new HashMap<>();

    @Override
    public Object get(Object key) {
      return data.get(key);
    }

    @Override
    public <V> V get(Object key, Class<V> requiredType) {
      Object value = data.get(key);
      if (requiredType.isInstance(value)) {
        return requiredType.cast(value);
      }
      return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <K, V> Object getOrComputeIfAbsent(K k, Function<K, V> function) {
      return data.computeIfAbsent(k, (Function<? super Object, ?>) function.apply(k));
    }

    @Override
    public <K, V> V getOrComputeIfAbsent(K k, Function<K, V> function, Class<V> aClass) {
      Object value = getOrComputeIfAbsent(k, function);

      if (aClass.isInstance(value)) {
        return aClass.cast(value);
      }
      return null;
    }

    @Override
    public void put(Object key, Object value) {
      data.put(key, value);
    }

    @Override
    public Object remove(Object key) {
      return data.remove(key);
    }

    @Override
    public <V> V remove(Object key, Class<V> requiredType) {
      Object value = data.remove(key);
      if (requiredType.isInstance(value)) {
        return requiredType.cast(value);
      }
      return null;
    }
  }
}
