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
  private Object body;
  /**
   * Base64-encoded body content for binary responses (images, fonts, etc.).
   * The MitmProxy Grid API addon must handle this field by decoding the value
   * with {@code base64.b64decode()} and setting it as {@code flow.response.content}.
   */
  private String bodyBase64;
  private MitmProxyBodyReplace bodyReplace;
}
