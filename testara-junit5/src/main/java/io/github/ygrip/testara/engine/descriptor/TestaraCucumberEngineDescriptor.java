package io.github.ygrip.testara.engine.descriptor;

import io.github.ygrip.testara.engine.context.TestaraCucumberEngineExecutionContext;
import io.github.ygrip.testara.engine.option.TestaraCucumberEngineOptions;
import io.github.ygrip.testara.engine.support.TestDescriptorOrderUtils;
import lombok.extern.log4j.Log4j2;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.support.descriptor.EngineDescriptor;
import org.junit.platform.engine.support.hierarchical.Node;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static java.util.Comparator.comparing;

@Log4j2
public class TestaraCucumberEngineDescriptor extends EngineDescriptor
    implements Node<TestaraCucumberEngineExecutionContext> {
  static final String ENGINE_ID = "testara-cucumber";
  private final TestSource source;
  private TestaraFeatureResolver featureResolver;
  private TestaraCucumberEngineOptions options;

  public TestaraCucumberEngineDescriptor(UniqueId uniqueId) {
    this(uniqueId, null, null);
  }

  public TestaraCucumberEngineDescriptor(UniqueId uniqueId, TestaraCucumberEngineOptions options, TestSource testSource) {
    super(uniqueId, "Testara Cucumber");
    this.options = options;
    this.source = testSource;
  }

  private static void recursivelyMerge(TestDescriptor descriptor, TestDescriptor parent) {
    Optional<? extends TestDescriptor> byUniqueId = parent.findByUniqueId(descriptor.getUniqueId());
    if (byUniqueId.isEmpty()) {
      parent.addChild(descriptor);
    } else {
      byUniqueId.ifPresent((existingParent) -> {
        descriptor.getChildren().forEach((child) -> {
          recursivelyMerge(child, existingParent);
        });
      });
    }
  }

  void clearChildren() {
    List<TestDescriptor> children = new ArrayList<>(getChildren());
    children.forEach(this::removeChild);
  }

  void setChildren(List<TestDescriptor> children) {
    children.forEach(child -> {
      recursivelyMerge(child, this);
    });
  }

  void sortChildren() {
    // Apply feature-level and scenario-level sorting
    // This handles @Order tags at both feature and scenario levels with the following priority:
    // 1. Order (ascending) - lower @Order values run first
    // 2. Exclusive resources (least exclusive first, most exclusive last) 
    // 3. Alphabetical fallback

    // Sort features first
    List<TestDescriptor> sortedFeatures = new ArrayList<>(getChildren());
    sortedFeatures.sort(comparing(TestDescriptorOrderUtils::getOrder).thenComparing(TestDescriptorOrderUtils::getExclusiveResourceCount)
        .thenComparing(TestDescriptor::getDisplayName));
    sortedFeatures = sortedFeatures.stream().map(feature -> {
      if (feature instanceof TestaraFeatureDescriptor) {
        // Sort scenarios within each feature
        return ((TestaraFeatureDescriptor) feature).sortChildren();
      }
      return feature;
    }).collect(Collectors.toList());
    clearChildren();
    setChildren(sortedFeatures);
  }

  TestaraFeatureResolver getFeatureResolver() {
    return this.featureResolver;
  }

  void setFeatureResolver(TestaraFeatureResolver featureResolver) {
    this.featureResolver = featureResolver;
  }

  @Override
  public Optional<TestSource> getSource() {
    return Optional.ofNullable(this.source);
  }

  @Override
  public TestaraCucumberEngineExecutionContext prepare(TestaraCucumberEngineExecutionContext context) {
    return this.ifChildren(context, TestaraCucumberEngineExecutionContext::startTestRun);
  }

  @Override
  public TestaraCucumberEngineExecutionContext before(TestaraCucumberEngineExecutionContext context) {
    return this.ifChildren(context, TestaraCucumberEngineExecutionContext::runBeforeAllHooks);
  }

  @Override
  public void after(TestaraCucumberEngineExecutionContext context) {
    this.ifChildren(context, TestaraCucumberEngineExecutionContext::runAfterAllHooks);
  }

  @Override
  public void cleanUp(TestaraCucumberEngineExecutionContext context) {
    this.ifChildren(context, TestaraCucumberEngineExecutionContext::finishTestRun);
  }

  private TestaraCucumberEngineExecutionContext ifChildren(TestaraCucumberEngineExecutionContext context,
      Consumer<TestaraCucumberEngineExecutionContext> action) {
    if (!this.getChildren().isEmpty()) {
      action.accept(context);
    }

    return context;
  }

  void mergeFeature(TestaraFeatureDescriptor descriptor) {
    recursivelyMerge(descriptor, this);
  }

  public TestaraCucumberEngineOptions getConfiguration() {
    return options;
  }

}
