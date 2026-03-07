package io.github.ygrip.testara.engine.descriptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ObjectUtils;
import org.junit.platform.engine.ConfigurationParameters;
import org.junit.platform.engine.EngineDiscoveryRequest;
import org.junit.platform.engine.Filter;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.discovery.ClassSelector;
import org.junit.platform.engine.discovery.ClasspathResourceSelector;
import org.junit.platform.engine.discovery.ClasspathRootSelector;
import org.junit.platform.engine.discovery.DirectorySelector;
import org.junit.platform.engine.discovery.FileSelector;
import org.junit.platform.engine.discovery.PackageNameFilter;
import org.junit.platform.engine.discovery.PackageSelector;
import org.junit.platform.engine.discovery.UniqueIdSelector;
import org.junit.platform.engine.discovery.UriSelector;

import io.github.ygrip.testara.engine.option.TestaraCucumberEngineOptions;

import io.cucumber.core.feature.FeatureWithLines;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class TestaraDiscoverySelectorResolver {
  private static boolean warnedWhenCucumberFeaturesPropertyIsUsed = false;

  private static void warnWhenCucumberFeaturesPropertyIsUsed() {
    if (warnedWhenCucumberFeaturesPropertyIsUsed) {
      return;
    }
    warnedWhenCucumberFeaturesPropertyIsUsed = true;
    log.warn(() -> "Discovering tests using the cucumber.features property. Other discovery selectors are ignored!");
  }

  public void resolveSelectors(EngineDiscoveryRequest request, TestaraCucumberEngineDescriptor engineDescriptor) {
    Predicate<String> packageFilter = this.buildPackageFilter(request);
    this.resolve(request, engineDescriptor, packageFilter);
    this.filter(engineDescriptor, packageFilter);
    TestaraCucumberEngineOptions options = new TestaraCucumberEngineOptions(request.getConfigurationParameters());
    if (options.shouldFilterSkippedScenarios()) {
      this.filterByTags(engineDescriptor, options);
    }
    this.pruneTree(engineDescriptor);
    engineDescriptor.sortChildren();
  }

  private void filterByTags(TestDescriptor descriptor, TestaraCucumberEngineOptions options) {
    boolean hasTags = ObjectUtils.isNotEmpty(descriptor.getTags());
    boolean hasChildren = ObjectUtils.isNotEmpty(descriptor.getChildren());
    if (hasChildren) {
      List<TestDescriptor> children = new ArrayList<>(descriptor.getChildren());
      children.forEach((child) -> {
        filterByTags(child, options);
      });
    }

    if (hasTags && !hasChildren) {
      List<String> tags = descriptor.getTags()
        .stream()
        .map(tag -> "@" + tag.getName())
        .collect(Collectors.toList());
      if (!options.isValidTags(tags)) {
        descriptor.removeFromHierarchy();
      }
    }
  }

  private Predicate<String> buildPackageFilter(EngineDiscoveryRequest request) {
    Filter<String> packageFilter = Filter.composeFilters(request.getFiltersByType(PackageNameFilter.class));
    return packageFilter.toPredicate();
  }

  private void resolve(EngineDiscoveryRequest request, TestaraCucumberEngineDescriptor engineDescriptor,
    Predicate<String> packageFilter) {
    ConfigurationParameters configuration = request.getConfigurationParameters();
    TestaraFeatureResolver featureResolver = TestaraFeatureResolver.create(configuration, engineDescriptor, packageFilter);

    engineDescriptor.setFeatureResolver(featureResolver);
    TestaraCucumberEngineOptions options = new TestaraCucumberEngineOptions(configuration);
    Set<FeatureWithLines> featureWithLines = options.featuresWithLines();
    if (!featureWithLines.isEmpty()) {
      warnWhenCucumberFeaturesPropertyIsUsed();
      featureWithLines.forEach(featureResolver::resolveFeatureWithLines);
      return;
    }

    request.getSelectorsByType(ClasspathRootSelector.class)
      .forEach(featureResolver::resolveClasspathRoot);
    request.getSelectorsByType(ClasspathResourceSelector.class)
      .forEach(featureResolver::resolveClasspathResource);
    request.getSelectorsByType(ClassSelector.class)
      .forEach(featureResolver::resolveClass);
    request.getSelectorsByType(PackageSelector.class)
      .forEach(featureResolver::resolvePackageResource);
    request.getSelectorsByType(FileSelector.class)
      .forEach(featureResolver::resolveFile);
    request.getSelectorsByType(DirectorySelector.class)
      .forEach(featureResolver::resolveDirectory);
    request.getSelectorsByType(UniqueIdSelector.class)
      .forEach(featureResolver::resolveUniqueId);
    request.getSelectorsByType(UriSelector.class)
      .forEach(featureResolver::resolveUri);
  }

  private void filter(TestDescriptor engineDescriptor, Predicate<String> packageFilter) {
    this.applyPackagePredicate(packageFilter, engineDescriptor);
  }

  private void pruneTree(TestDescriptor rootDescriptor) {
    rootDescriptor.accept(TestDescriptor::prune);
  }

  private void applyPackagePredicate(Predicate<String> packageFilter, TestDescriptor engineDescriptor) {
    engineDescriptor.accept((descriptor) -> {
      if (descriptor instanceof TestaraNodeDescriptor.PickleDescriptor pickleDescriptor) {
        if (!this.includePickle(pickleDescriptor, packageFilter)) {
          descriptor.removeFromHierarchy();
        }
      }
    });
  }

  private boolean includePickle(TestaraNodeDescriptor.PickleDescriptor pickleDescriptor,
    Predicate<String> packageFilter) {
    Optional<String> aPackage = pickleDescriptor.getPackage();
    Objects.requireNonNull(packageFilter);
    return aPackage.map(packageFilter::test)
      .orElse(true);
  }
}
