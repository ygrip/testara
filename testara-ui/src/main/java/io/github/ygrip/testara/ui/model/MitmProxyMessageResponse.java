package io.github.ygrip.testara.ui.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Generic status + message response.
 * Maps to {@code MessageResponse} in the MitmProxy Grid API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MitmProxyMessageResponse {
  private String status;
  private String message;
}
