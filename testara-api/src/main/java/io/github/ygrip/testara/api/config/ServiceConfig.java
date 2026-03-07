package io.github.ygrip.testara.api.config;

import io.github.ygrip.testara.api.model.ApiModel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Immutable service configuration holder
 * Separates static values from command models for efficient caching
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceConfig {

  private ApiModel apiModel;
  private ParsedConfig headers;
  private ParsedConfig parameters;
  private ParsedConfig formParams;


  /**
   * Parsed configuration that separates static values from command models
   */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ParsedConfig {
    private Map<String, Object> staticValues;

    /**
     * Check if this config has any values
     */
    public boolean isEmpty() {
      return (staticValues == null || staticValues.isEmpty());
    }
  }
}

