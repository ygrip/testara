package io.github.ygrip.testara.ui.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Modifications applied to the incoming response.
 * Maps to {@code ResponseModification} in the MitmProxy Grid API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MitmProxyResponseModification {
  private Integer statusCode;
  private MitmProxyHeaderModification headers;
  private String body;
  private MitmProxyBodyReplace bodyReplace;
}
