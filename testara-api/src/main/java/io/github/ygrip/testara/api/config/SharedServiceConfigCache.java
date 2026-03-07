package io.github.ygrip.testara.api.config;

import io.github.ygrip.testara.api.model.ApiModel;
import io.github.ygrip.testara.api.model.DefaultApiSpec;
import io.github.ygrip.testara.core.context.TestComponent;
import io.github.ygrip.testara.core.registry.RegistryScope;
import lombok.extern.log4j.Log4j2;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static io.github.ygrip.testara.core.support.CommonHelper.isBlank;

/**
 * Shared singleton cache for service configurations
 * This cache is shared across all threads and contains immutable service configurations
 */
@Log4j2
@TestComponent(scope = RegistryScope.GLOBAL)
public class SharedServiceConfigCache {

  // Immutable service configurations shared across all threads
  private final static ConcurrentHashMap<String, ServiceConfig> serviceConfigs = new ConcurrentHashMap<>();

  // Thread-safe initialization flag
  private final Object initLock = new Object();

  public SharedServiceConfigCache(ApiProperties apiProperties, ApiSpecProperties apiSpecProperties) {
    loadServiceConfigurations(apiProperties, apiSpecProperties);
  }

  public void loadServiceConfigurations(ApiProperties apiProperties, ApiSpecProperties apiSpecProperties) {
    if (isBlank(apiProperties)) {
      return;
    }

    Map<String, ApiModel> services = apiProperties.getService();
    if (isBlank(services)) {
      return;
    }

    for (String serviceName : services.keySet()) {
      ApiModel service = services.get(serviceName);
      ServiceConfig config = createServiceConfig(service, apiSpecProperties);
      serviceConfigs.put(serviceName, config);
      log.debug("Loaded service configuration: {}", serviceName);
    }
  }

  private ServiceConfig createServiceConfig(ApiModel service, ApiSpecProperties defaultSpecification) {
    Map<String, Object> headers = new HashMap<>();
    Map<String, Object> queryParameters = new HashMap<>();
    Map<String, Object> formParameters = new HashMap<>();

    // Load default specification if present
    String serviceDefaultSpecification = service.getDefault_specification();
    if (!isBlank(serviceDefaultSpecification) && !isBlank(defaultSpecification)) {
      Map<String, DefaultApiSpec> specifications = defaultSpecification.getApi();
      if (specifications.containsKey(serviceDefaultSpecification)) {
        DefaultApiSpec specification = specifications.get(serviceDefaultSpecification);
        if (specification != null) {
          if (!isBlank(specification.getHeader())) {
            headers.putAll(specification.getHeader());
          }
          if (!isBlank(specification.getParameter())) {
            queryParameters.putAll(specification.getParameter());
          }
          if (!isBlank(specification.getForm_param())) {
            formParameters.putAll(specification.getForm_param());
          }
        }
      }
    }

    // Override with service-specific configurations
    if (!isBlank(service.getHeader())) {
      headers.putAll(service.getHeader());
    }
    if (!isBlank(service.getParameter())) {
      queryParameters.putAll(service.getParameter());
    }
    if (!isBlank(service.getForm_param())) {
      formParameters.putAll(service.getForm_param());
    }

    // Parse values into static and command models
    ServiceConfig.ParsedConfig parsedHeaders = parseConfigMap(headers);
    ServiceConfig.ParsedConfig parsedParameters = parseConfigMap(queryParameters);
    ServiceConfig.ParsedConfig parsedForms = parseConfigMap(formParameters);

    return ServiceConfig.builder()
        .apiModel(service)
        .headers(parsedHeaders)
        .parameters(parsedParameters)
        .formParams(parsedForms)
        .build();
  }

  private ServiceConfig.ParsedConfig parseConfigMap(Map<String, Object> input) {
    Map<String, Object> staticValues = new HashMap<>();

    if (!isBlank(input)) {
      for (Map.Entry<String, Object> entry : input.entrySet()) {
        String key = entry.getKey();
        Object value = entry.getValue();
        staticValues.put(key, value);
      }
    }

    return ServiceConfig.ParsedConfig.builder().staticValues(staticValues).build();
  }

  /**
   * Get service configuration by name
   */
  public ServiceConfig getServiceConfig(String serviceName) {
    return serviceConfigs.get(serviceName);
  }

  /**
   * Check if a service configuration exists
   */
  public boolean hasServiceConfig(String serviceName) {
    return serviceConfigs.containsKey(serviceName);
  }

  /**
   * Get all service names
   */
  public Set<String> getServiceNames() {
    return serviceConfigs.keySet();
  }

  /**
   * Clear all cached configurations (mainly for testing)
   */
  public void clear() {
    synchronized (initLock) {
      serviceConfigs.clear();
      log.trace("Shared service configuration cache cleared");
    }
  }
}

