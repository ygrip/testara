package io.github.ygrip.testara.engine.parser;

import io.cucumber.core.feature.FeatureParser;
import io.cucumber.core.gherkin.Feature;
import io.cucumber.core.resource.Resource;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class TestaraCachingFeatureParser {
  private final Map<URI, Optional<Feature>> cache = new HashMap<>();
  private final FeatureParser delegate;

  public TestaraCachingFeatureParser(FeatureParser delegate) {
    this.delegate = delegate;
  }

  public Optional<Feature> parseResource(Resource resource) {
    return this.cache.computeIfAbsent(resource.getUri(), (uri) -> this.delegate.parseResource(resource));
  }
}
