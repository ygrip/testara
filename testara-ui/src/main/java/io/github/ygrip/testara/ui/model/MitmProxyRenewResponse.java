package io.github.ygrip.testara.ui.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response when renewing an instance's lifespan.
 * Maps to {@code RenewResponse} in the MitmProxy Grid API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MitmProxyRenewResponse {
  private String status;
  private String message;
  private int ttl;
  private String expiresAt;
  private double remainingSeconds;
}
