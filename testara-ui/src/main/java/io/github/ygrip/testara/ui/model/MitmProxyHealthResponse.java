package io.github.ygrip.testara.ui.model;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Grid health and capacity.
 * Maps to {@code HealthResponse} in the MitmProxy Grid API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MitmProxyHealthResponse {
  private String status;
  private int instances;
  private List<Integer> usedPorts;
  private int availableSlots;
  private String portRange;
  private int defaultTtl;

  public boolean isHealthy() {
    return "up".equalsIgnoreCase(status);
  }
}
