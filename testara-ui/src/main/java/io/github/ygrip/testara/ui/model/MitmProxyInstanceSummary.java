package io.github.ygrip.testara.ui.model;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Summary info for a running mitmproxy instance.
 * Maps to {@code InstanceSummary} in the MitmProxy Grid API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MitmProxyInstanceSummary {
  private String instanceId;
  private int port;
  private String status;
  private String createdAt;
  private double uptimeSeconds;
  private int ttl;
  private double remainingSeconds;
  private int ruleCount;
  private List<String> clientIps;
}
