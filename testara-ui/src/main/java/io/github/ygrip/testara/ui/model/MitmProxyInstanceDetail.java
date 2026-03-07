package io.github.ygrip.testara.ui.model;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Detailed information about a specific mitmproxy instance including its rules.
 * Maps to {@code InstanceDetail} in the MitmProxy Grid API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MitmProxyInstanceDetail {
  private String instanceId;
  private int port;
  private String status;
  private String createdAt;
  private double uptimeSeconds;
  private int ttl;
  private double remainingSeconds;
  private List<MitmProxyRuleResponse> rules;
  private List<String> clientIps;
}
