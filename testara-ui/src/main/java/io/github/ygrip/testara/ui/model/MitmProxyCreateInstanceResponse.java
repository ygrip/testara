package io.github.ygrip.testara.ui.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response when a new mitmproxy instance is created.
 * Maps to {@code CreateInstanceResponse} in the MitmProxy Grid API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MitmProxyCreateInstanceResponse {
  private String instanceId;
  private int port;
  private String status;
  private int ttl;
  private String expiresAt;
}
