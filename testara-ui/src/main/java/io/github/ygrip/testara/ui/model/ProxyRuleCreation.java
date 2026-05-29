package io.github.ygrip.testara.ui.model;

import java.io.File;
import java.nio.file.Files;
import java.util.Base64;

import io.github.ygrip.mitmproxy.grid.client.model.MitmProxyBodyReplace;
import io.github.ygrip.mitmproxy.grid.client.model.MitmProxyRequestModification;
import io.github.ygrip.mitmproxy.grid.client.model.MitmProxyResponseModification;
import io.github.ygrip.mitmproxy.grid.client.model.MitmProxyRule;
import io.github.ygrip.mitmproxy.grid.client.model.MitmProxyRuleAction;
import io.github.ygrip.mitmproxy.grid.client.model.MitmProxyRuleMatch;
import io.github.ygrip.testara.core.file.FileHelper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;

/**
 * Proxy rule creation specification loaded from a JSON file.
 * <p>
 * Mirrors the {@link MitmProxyRule} structure but adds convenience fields for
 * file-based body replacement (e.g. images, large payloads). The JSON file
 * lives under {@code src/test/resources/} and is resolved by
 * {@link io.github.ygrip.testara.core.transformer.TransformerService}.
 * <p>
 * Example JSON ({@code github/intercept network response from user avatar.json}):
 * <pre>{@code
 * {
 *   "enabled": true,
 *   "priority": 0,
 *   "match": {
 *     "urlContains": "avatars.githubusercontent.com",
 *     "responseContentType": "image/"
 *   },
 *   "action": {
 *     "modifyResponse": {
 *       "statusCode": 200,
 *       "headers": {
 *         "set": { "Content-Type": "image/png" }
 *       }
 *     }
 *   },
 *   "responseBodyFile": "github/images/replacement-avatar.png"
 * }
 * }</pre>
 *
 * @see MitmProxyRule
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Log4j2
public class ProxyRuleCreation {

  @Builder.Default
  private boolean enabled = true;
  @Builder.Default
  private int priority = 0;
  private MitmProxyRuleMatch match;
  private MitmProxyRuleAction action;

  /**
   * Path to a file whose content replaces the response body.
   * Binary files (images, fonts, etc.) are automatically base64-encoded.
   * Resolved relative to the test resources folder when a baseFolder is supplied.
   */
  private String responseBodyFile;

  /**
   * Path to a file whose content replaces the request body.
   * Resolved relative to the test resources folder when a baseFolder is supplied.
   */
  private String requestBodyFile;

  /**
   * Convert to a {@link MitmProxyRule}, resolving file references relative to
   * {@code baseFolder}.
   *
   * @param baseFolder absolute path prefix for file resolution (typically
   *                   {@code <projectDir>/src/test/resources/})
   * @return a fully-resolved rule ready for the MitmProxy Grid API
   */
  public MitmProxyRule toMitmProxyRule(String baseFolder) {
    MitmProxyRuleAction resolvedAction = resolveAction(baseFolder);
    return MitmProxyRule.builder()
      .enabled(enabled)
      .priority(priority)
      .match(match)
      .action(resolvedAction)
      .build();
  }

  /**
   * Convert to a {@link MitmProxyRule} without resolving file references.
   */
  public MitmProxyRule toMitmProxyRule() {
    return toMitmProxyRule(null);
  }

  private MitmProxyRuleAction resolveAction(String baseFolder) {
    if (action == null) {
      return MitmProxyRuleAction.builder().build();
    }

    MitmProxyResponseModification responseModification = action.getModifyResponse() != null
      ? MitmProxyResponseModification.builder()
        .statusCode(action.getModifyResponse().getStatusCode())
        .headers(action.getModifyResponse().getHeaders())
        .body(action.getModifyResponse().getBody())
        .bodyBase64(action.getModifyResponse().getBodyBase64())
        .bodyReplace(action.getModifyResponse().getBodyReplace())
        .build()
      : null;

    MitmProxyRequestModification requestModification = action.getModifyRequest() != null
      ? MitmProxyRequestModification.builder()
        .headers(action.getModifyRequest().getHeaders())
        .params(action.getModifyRequest().getParams())
        .body(action.getModifyRequest().getBody())
        .bodyReplace(action.getModifyRequest().getBodyReplace())
        .build()
      : null;

    if (responseBodyFile != null && !responseBodyFile.isBlank()) {
      if (responseModification == null) {
        responseModification = MitmProxyResponseModification.builder().build();
      }
      String content = readFileContent(responseBodyFile, baseFolder);
      if (content != null) {
        if (isBinaryFile(responseBodyFile)) {
          responseModification.setBodyBase64(content);
        } else {
          responseModification.setBody(content);
        }
      }
    }

    if (requestBodyFile != null && !requestBodyFile.isBlank()) {
      if (requestModification == null) {
        requestModification = MitmProxyRequestModification.builder().build();
      }
      String content = readFileContent(requestBodyFile, baseFolder);
      if (content != null) {
        requestModification.setBody(content);
      }
    }

    return MitmProxyRuleAction.builder()
      .modifyRequest(requestModification)
      .modifyResponse(responseModification)
      .build();
  }

  private String readFileContent(String filePath, String baseFolder) {
    try {
      File file = new File(filePath);
      if (!file.isAbsolute() && baseFolder != null) {
        file = new File(baseFolder + filePath);
      }
      if (!file.exists()) {
        log.warn("Body file not found: {}", file.getAbsolutePath());
        return null;
      }
      if (isBinaryFile(filePath)) {
        byte[] bytes = Files.readAllBytes(file.toPath());
        return Base64.getEncoder().encodeToString(bytes);
      } else {
        return FileHelper.readFile(file.getAbsolutePath());
      }
    } catch (Exception e) {
      log.error("Failed to read body file: {}", filePath, e);
      return null;
    }
  }

  private static boolean isBinaryFile(String filePath) {
    String lower = filePath.toLowerCase();
    return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
      || lower.endsWith(".gif") || lower.endsWith(".webp") || lower.endsWith(".ico")
      || lower.endsWith(".bmp") || lower.endsWith(".tiff") || lower.endsWith(".avif")
      || lower.endsWith(".woff") || lower.endsWith(".woff2") || lower.endsWith(".ttf")
      || lower.endsWith(".eot") || lower.endsWith(".pdf") || lower.endsWith(".zip")
      || lower.endsWith(".gz") || lower.endsWith(".br");
  }
}
