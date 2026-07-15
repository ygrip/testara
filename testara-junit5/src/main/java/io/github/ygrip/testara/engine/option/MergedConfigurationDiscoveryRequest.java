package io.github.ygrip.testara.engine.option;

import org.junit.platform.engine.ConfigurationParameters;
import org.junit.platform.engine.DiscoveryFilter;
import org.junit.platform.engine.DiscoverySelector;
import org.junit.platform.engine.EngineDiscoveryListener;
import org.junit.platform.engine.EngineDiscoveryRequest;
import org.junit.platform.engine.OutputDirectoryCreator;
import org.junit.platform.engine.reporting.OutputDirectoryProvider;

import java.util.List;

/**
 * Wraps an {@link EngineDiscoveryRequest} so every {@code getConfigurationParameters()}
 * call anywhere downstream - including inside {@code TestaraDiscoverySelectorResolver}
 * and {@code TestaraFeatureResolver}, which re-derive it straight from the request
 * rather than a passed-in value - sees the merged cucumber.properties + junit-platform.properties
 * view instead of the raw JUnit Platform one.
 */
public final class MergedConfigurationDiscoveryRequest implements EngineDiscoveryRequest {
  private final EngineDiscoveryRequest delegate;
  private final ConfigurationParameters mergedConfigurationParameters;

  public MergedConfigurationDiscoveryRequest(EngineDiscoveryRequest delegate) {
    this.delegate = delegate;
    this.mergedConfigurationParameters = TestaraConfigurationParameters.merge(delegate.getConfigurationParameters());
  }

  @Override
  public <T extends DiscoverySelector> List<T> getSelectorsByType(Class<T> selectorType) {
    return delegate.getSelectorsByType(selectorType);
  }

  @Override
  public <T extends DiscoveryFilter<?>> List<T> getFiltersByType(Class<T> filterType) {
    return delegate.getFiltersByType(filterType);
  }

  @Override
  public ConfigurationParameters getConfigurationParameters() {
    return mergedConfigurationParameters;
  }

  @Override
  public EngineDiscoveryListener getDiscoveryListener() {
    return delegate.getDiscoveryListener();
  }

  @Override
  public OutputDirectoryProvider getOutputDirectoryProvider() {
    return delegate.getOutputDirectoryProvider();
  }

  @Override
  public OutputDirectoryCreator getOutputDirectoryCreator() {
    return delegate.getOutputDirectoryCreator();
  }
}
