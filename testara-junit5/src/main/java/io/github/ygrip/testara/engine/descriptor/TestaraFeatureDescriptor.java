package io.github.ygrip.testara.engine.descriptor;

import io.github.ygrip.testara.engine.context.TestaraCucumberEngineExecutionContext;
import io.github.ygrip.testara.engine.support.TestDescriptorOrderUtils;
import io.cucumber.core.gherkin.Feature;
import io.cucumber.messages.types.Envelope;
import io.cucumber.messages.types.GherkinDocument;
import io.cucumber.messages.types.Tag;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.TestTag;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor;
import org.junit.platform.engine.support.hierarchical.Node;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.util.Comparator.comparing;

public class TestaraFeatureDescriptor extends AbstractTestDescriptor
    implements Node<TestaraCucumberEngineExecutionContext> {
  private final Feature feature;
  private final Set<TestTag> tags;

  TestaraFeatureDescriptor(UniqueId uniqueId, String name, TestSource source, Feature feature) {
    super(uniqueId, name, source);
    this.feature = feature;
    List<Tag> testTag = new ArrayList<>();
    Iterator<?> events = feature.getParseEvents().iterator();
    events.forEachRemaining(event -> {
      if(event instanceof Envelope){
        Optional<GherkinDocument> doc = ((Envelope) event).getGherkinDocument();
        doc.flatMap(GherkinDocument::getFeature).ifPresent(data -> testTag.addAll(data.getTags()));
      }
    });
    this.tags = testTag.stream()
        .map(tag -> tag.getName().substring(1))
        .filter(TestTag::isValid)
        .map(TestTag::create)
        .collect(Collectors.collectingAndThen(Collectors.toCollection(LinkedHashSet::new),
            Collections::unmodifiableSet));
  }

  private static void pruneRecursively(TestDescriptor descriptor, Predicate<TestDescriptor> toKeep) {
    if (!toKeep.test(descriptor)) {
      if (descriptor.isTest()) {
        descriptor.removeFromHierarchy();
      }

      List<TestDescriptor> children = new ArrayList<>(descriptor.getChildren());
      children.forEach((child) -> {
        pruneRecursively(child, toKeep);
      });
    }
  }

  void clearChildren() {
    List<TestDescriptor> children = new ArrayList<>(getChildren());
    children.forEach(this::removeChild);
  }

  void setChildren(List<TestDescriptor> children) {
    children.forEach(this::addChild);
  }

  List<TestDescriptor> getAllPickles(TestDescriptor descriptor) {
    List<TestDescriptor> result = new ArrayList<>();
    if (descriptor instanceof TestaraNodeDescriptor.PickleDescriptor) {
      result.add(descriptor);
    } else {
      if (descriptor.isContainer()) {
        descriptor.getChildren().forEach(child -> {
          result.addAll(getAllPickles(child));
        });
      }
    }
    return result;
  }

  TestaraFeatureDescriptor sortChildren() {
    List<TestDescriptor> sortedChild = new ArrayList<>(getChildren());
    sortedChild.sort(comparing(TestDescriptorOrderUtils::getOrder).thenComparing(TestDescriptorOrderUtils::getExclusiveResourceCount)
        .thenComparing(TestDescriptor::getDisplayName));
    sortedChild =
        sortedChild.stream().map(child -> ((TestaraNodeDescriptor) child).sortChildren()).collect(Collectors.toList());
    clearChildren();
    setChildren(sortedChild);
    return this;
  }

  Integer getOrder() {
    return TestDescriptorOrderUtils.getOrder(this);
  }

  Feature getFeature() {
    return this.feature;
  }

  void prune(Predicate<TestDescriptor> toKeep) {
    pruneRecursively(this, toKeep);
  }

  public TestaraCucumberEngineExecutionContext prepare(TestaraCucumberEngineExecutionContext context) {
    context.beforeFeature(this.feature);
    return context;
  }

  public Type getType() {
    return Type.CONTAINER;
  }

  @Override
  public Set<TestTag> getTags() {
   return this.tags;
  }
}
