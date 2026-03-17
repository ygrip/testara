package io.github.ygrip.testara.ui.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Modifications applied to the outgoing request.
 * Maps to {@code RequestModification} in the MitmProxy Grid API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MitmProxyRequestModification {
  private MitmProxyHeaderModification headers;
  private MitmProxyParamModification params;
  private Object body;
  private MitmProxyBodyReplace bodyReplace;
}
