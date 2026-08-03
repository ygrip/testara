package io.github.ygrip.testara.engine.descriptor;

import io.github.ygrip.testara.engine.option.TestaraCucumberEngineOptions;
import io.github.ygrip.testara.engine.parser.TestaraCachingFeatureParser;
import io.github.ygrip.testara.engine.suites.TestSuite;
import io.github.ygrip.testara.engine.model.TestaraNamingStrategy;
import io.cucumber.core.feature.FeatureIdentifier;
import io.cucumber.core.feature.FeatureParser;
import io.cucumber.core.feature.FeatureWithLines;
import io.cucumber.core.gherkin.Feature;
import io.cucumber.core.gherkin.Pickle;
import io.cucumber.core.logging.Logger;
import io.cucumber.core.logging.LoggerFactory;
import io.cucumber.core.resource.ClassLoaders;
import io.cucumber.core.resource.ResourceScanner;
import io.cucumber.plugin.event.Node;
import org.junit.platform.engine.ConfigurationParameters;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.discovery.ClassSelector;
import org.junit.platform.engine.discovery.ClasspathResourceSelector;
import org.junit.platform.engine.discovery.ClasspathRootSelector;
import org.junit.platform.engine.discovery.DirectorySelector;
import org.junit.platform.engine.discovery.FileSelector;
import org.junit.platform.engine.discovery.PackageSelector;
import org.junit.platform.engine.discovery.UniqueIdSelector;
import org.junit.platform.engine.discovery.UriSelector;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static io.github.ygrip.testara.engine.model.TestaraDefaultNamingStrategy.CUSTOM;
import static java.util.Comparator.comparing;

public final class TestaraFeatureResolver {
  private static final Logger log = LoggerFactory.getLogger(TestaraFeatureResolver.class);
  private final ResourceScanner<Feature> featureScanner;
  private final TestaraCucumberEngineDescriptor engineDescriptor;
  private final Predicate<String> packageFilter;
  private final TestaraNamingStrategy namingStrategy;
  private final TestaraCucumberEngineOptions options;

  private TestaraFeatureResolver(ConfigurationParameters parameters,
      TestaraCucumberEngineDescriptor engineDescriptor,
      Predicate<String> packageFilter) {
    Supplier<ClassLoader> supplier = ClassLoaders::getDefaultClassLoader;
    Predicate<Path> predicate = FeatureIdentifier::isFeature;
    TestaraCachingFeatureParser cachingFeatureParser = new TestaraCachingFeatureParser(new FeatureParser(UUID::randomUUID));
    Objects.requireNonNull(cachingFeatureParser);
    this.featureScanner = new ResourceScanner<>(supplier, predicate, cachingFeatureParser::parseResource);
    this.engineDescriptor = engineDescriptor;
    this.packageFilter = packageFilter;
    this.options = new TestaraCucumberEngineOptions(parameters);
    this.namingStrategy = this.options.namingStrategy();
  }

  public static TestaraFeatureResolver create(ConfigurationParameters parameters,
      TestaraCucumberEngineDescriptor engineDescriptor,
      Predicate<String> packageFilter) {
    return new TestaraFeatureResolver(parameters, engineDescriptor, packageFilter);
  }

  private static URI stripQuery(URI uri) {
    if (uri.getQuery() == null) {
      return uri;
    } else {
      String uriString = uri.toString();
      return URI.create(uriString.substring(0, uriString.indexOf('?')));
    }
  }

  TestaraCucumberEngineOptions getOptions() {
    return this.options;
  }

  public List<Feature> parseFeatures(FeatureWithLines featureWithLines) {
    return this.featureScanner.scanForResourcesUri(stripQuery(featureWithLines.uri()));
  }

  void resolveFile(FileSelector selector) {
    this.featureScanner.scanForResourcesPath(selector.getPath())
        .stream()
        .sorted(comparing(Feature::getUri))
        .map(this::createFeatureDescriptor)
        .forEach((featureDescriptor) -> {
          featureDescriptor.prune(TestaraTestDescriptorOnLine.from(selector));
          this.engineDescriptor.mergeFeature(featureDescriptor);
        });
  }

  private TestaraFeatureDescriptor createFeatureDescriptor(Feature feature) {
    TestaraFeatureOrigin source = TestaraFeatureOrigin.fromUri(feature.getUri());

    return (TestaraFeatureDescriptor) feature.map(engineDescriptor,
        (Node.Feature self, TestDescriptor parent) -> new TestaraFeatureDescriptor(source.featureSegment(parent.getUniqueId(),
            feature), namingStrategy.name(self), source.featureSource(), feature),
        (Node.Rule node, TestDescriptor parent) -> {
          TestDescriptor descriptor = new TestaraNodeDescriptor.RuleDescriptor(options,
              source,
              source.ruleSegment(parent.getUniqueId(), node),
              namingStrategy.name(node),
              source.nodeSource(node));
          parent.addChild(descriptor);
          return descriptor;
        },
        (Node.Scenario node, TestDescriptor parent) -> {
          Pickle pickle = feature.getPickleAt(node);
          TestDescriptor descriptor = new TestaraNodeDescriptor.PickleDescriptor(options,
              source,
              source.scenarioSegment(parent.getUniqueId(), node),
              namingStrategy == CUSTOM ? pickle.getName() : namingStrategy.name(node),
              source.nodeSource(node),
              pickle);
          parent.addChild(descriptor);
          return descriptor;
        },
        (Node.ScenarioOutline node, TestDescriptor parent) -> {
          TestDescriptor descriptor = new TestaraNodeDescriptor.ScenarioOutlineDescriptor(options,
              source,
              source.scenarioSegment(parent.getUniqueId(), node),
              namingStrategy.name(node),
              source.nodeSource(node));
          if (!options.stepNotifications()) {
            parent.addChild(descriptor);
            return descriptor;
          } else {
            node.elements().forEach(examples -> {
              TestaraNodeDescriptor.ExamplesDescriptor examplesDescriptor = new TestaraNodeDescriptor.ExamplesDescriptor(
                  options,
                  source,
                  source.exampleSegment(descriptor.getUniqueId(), examples),
                  namingStrategy.name(examples),
                  source.nodeSource(examples));
              examples.elements().forEach(example -> {
                Pickle pickle = feature.getPickleAt(example);
                TestaraNodeDescriptor.PickleDescriptor scenarioDescriptor = new TestaraNodeDescriptor.PickleDescriptor(
                    options,
                    source,
                    source.exampleSegment(examplesDescriptor.getUniqueId(), example),
                    namingStrategy == CUSTOM ? pickle.getName() : namingStrategy.name(example),
                    source.nodeSource(example),
                    pickle);
                parent.addChild(scenarioDescriptor);
              });
            });
            return parent;
          }
        },
        (Node.Examples node, TestDescriptor parent) -> {
          TestaraNodeDescriptor descriptor = new TestaraNodeDescriptor.ExamplesDescriptor(options,
              source,
              source.examplesSegment(parent.getUniqueId(), node),
              namingStrategy.name(node),
              source.nodeSource(node));
          if (!options.stepNotifications()) {
            parent.addChild(descriptor);
            return descriptor;
          } else {
            return parent;
          }
        },
        (Node.Example node, TestDescriptor parent) -> {
          Pickle pickle = feature.getPickleAt(node);
          TestDescriptor descriptor = new TestaraNodeDescriptor.PickleDescriptor(options,
              source,
              source.exampleSegment(parent.getUniqueId(), node),
              namingStrategy == CUSTOM ? pickle.getName() : namingStrategy.name(node),
              source.nodeSource(node),
              pickle);
          if (!options.stepNotifications()) {
            parent.addChild(descriptor);
            return descriptor;
          } else {
            return parent;
          }
        });
  }

  void resolveDirectory(DirectorySelector selector) {
    featureScanner.scanForResourcesPath(selector.getPath())
        .stream()
        .sorted(comparing(Feature::getUri))
        .map(this::createFeatureDescriptor)
        .forEach(engineDescriptor::mergeFeature);
  }

  void resolvePackageResource(PackageSelector selector) {
    this.resolvePackageResource(selector.getPackageName());
  }

  private List<Feature> resolvePackageResource(String packageName) {
    List<Feature> features = featureScanner.scanForResourcesInPackage(packageName, packageFilter);

    features.stream()
        .sorted(comparing(Feature::getUri))
        .map(this::createFeatureDescriptor)
        .forEach(engineDescriptor::mergeFeature);

    return features;
  }

  void resolveClass(ClassSelector classSelector) {
    Class<?> javaClass = classSelector.getJavaClass();
    TestSuite annotation = javaClass.getAnnotation(TestSuite.class);
    if (annotation != null) {
      // We know now the intention is to run feature files in the
      // package of the annotated class.
      resolvePackageResourceWarnIfNone(javaClass.getPackage().getName());
    }
  }

  private void resolvePackageResourceWarnIfNone(String packageName) {
    List<Feature> features = this.resolvePackageResource(packageName);
    if (features.isEmpty()) {
      log.warn(() -> "No features found in package '" + packageName + "'");
    }

  }

  void resolveClasspathResource(ClasspathResourceSelector selector) {
    String classpathResourceName = selector.getClasspathResourceName();

    featureScanner.scanForClasspathResource(classpathResourceName, packageFilter)
        .stream()
        .sorted(comparing(Feature::getUri))
        .map(this::createFeatureDescriptor)
        .forEach(featureDescriptor -> {
          featureDescriptor.prune(TestaraTestDescriptorOnLine.from(selector));
          engineDescriptor.mergeFeature(featureDescriptor);
        });
  }

  void resolveClasspathRoot(ClasspathRootSelector selector) {
    featureScanner.scanForResourcesInClasspathRoot(selector.getClasspathRoot(), packageFilter)
        .stream()
        .sorted(comparing(Feature::getUri))
        .map(this::createFeatureDescriptor)
        .forEach(engineDescriptor::mergeFeature);
  }

  void resolveUniqueId(UniqueIdSelector uniqueIdSelector) {
    UniqueId uniqueId = uniqueIdSelector.getUniqueId();
    // Ignore any ids not from our own engine
    if (!uniqueId.hasPrefix(engineDescriptor.getUniqueId())) {
      return;
    }

    Predicate<TestDescriptor> keepTestWithSelectedId = testDescriptor -> uniqueId.equals(testDescriptor.getUniqueId());

    uniqueId.getSegments()
        .stream()
        .filter(TestaraFeatureOrigin::isFeatureSegment)
        .map(UniqueId.Segment::getValue)
        .map(URI::create)
        .flatMap(this::resolveUri)
        .forEach(featureDescriptor -> {
          featureDescriptor.prune(keepTestWithSelectedId);
          engineDescriptor.mergeFeature(featureDescriptor);
        });
  }

  private Stream<TestaraFeatureDescriptor> resolveUri(URI uri) {
    return this.featureScanner.scanForResourcesUri(uri)
        .stream()
        .sorted(comparing(Feature::getUri))
        .map(this::createFeatureDescriptor);
  }

  void resolveUri(UriSelector selector) {
    resolveUri(stripQuery(selector.getUri())).forEach(featureDescriptor -> {
      featureDescriptor.prune(TestaraTestDescriptorOnLine.from(selector));
      engineDescriptor.mergeFeature(featureDescriptor);
    });
  }

  void resolveFeatureWithLines(FeatureWithLines selector) {
    resolveUri(selector.uri()).forEach(featureDescriptor -> {
      featureDescriptor.prune(TestaraTestDescriptorOnLine.from(selector));
      engineDescriptor.mergeFeature(featureDescriptor);
    });
  }
}
