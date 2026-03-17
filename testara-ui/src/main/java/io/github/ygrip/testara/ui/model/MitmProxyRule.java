package io.github.ygrip.testara.ui.model;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import io.github.ygrip.testara.core.mapper.MapperHelper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Create a new interception rule (input model).
 * Maps to {@code RuleCreate} in the MitmProxy Grid API.
 *
 * <pre>{@code
 * MitmProxyRule rule = MitmProxyRule.builder()
 *     .match(MitmProxyRuleMatch.builder().urlContains("api.example.com").method("GET").build())
 *     .action(MitmProxyRuleAction.builder()
 *         .modifyResponse(MitmProxyResponseModification.builder()
 *             .statusCode(200)
 *             .body("{\"mocked\": true}")
 *             .build())
 *         .build())
 *     .build();
 * }</pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MitmProxyRule {
  @Builder.Default
  private boolean enabled = true;
  @Builder.Default
  private int priority = 0;
  private MitmProxyRuleMatch match;
  private MitmProxyRuleAction action;

  // ── Convenience factory methods ──────────────────────────────────

  /**
   * Mock a response with a fixed status code and body.
   */
  public static MitmProxyRule mockResponse(String urlContains, int statusCode, Object body) {
    return MitmProxyRule.builder()
        .match(MitmProxyRuleMatch.builder().urlContains(urlContains).build())
        .action(MitmProxyRuleAction.builder()
            .modifyResponse(MitmProxyResponseModification.builder()
                .statusCode(statusCode)
                .body(MapperHelper.toString(body))
                .build())
            .build())
        .build();
  }

  /**
   * Replace a substring in the response body.
   */
  public static MitmProxyRule replaceResponseBody(String urlContains, String from, String to) {
    return MitmProxyRule.builder()
        .match(MitmProxyRuleMatch.builder().urlContains(urlContains).build())
        .action(MitmProxyRuleAction.builder()
            .modifyResponse(MitmProxyResponseModification.builder()
                .bodyReplace(MitmProxyBodyReplace.builder().from(from).to(to).build())
                .build())
            .build())
        .build();
  }

  /**
   * Replace a substring in the request body.
   */
  public static MitmProxyRule replaceRequestBody(String urlContains, String from, String to) {
    return MitmProxyRule.builder()
        .match(MitmProxyRuleMatch.builder().urlContains(urlContains).build())
        .action(MitmProxyRuleAction.builder()
            .modifyRequest(MitmProxyRequestModification.builder()
                .bodyReplace(MitmProxyBodyReplace.builder().from(from).to(to).build())
                .build())
            .build())
        .build();
  }

  /**
   * Set headers on the outgoing request (e.g. inject auth tokens).
   */
  public static MitmProxyRule setRequestHeaders(String urlContains, Map<String, String> headers) {
    return MitmProxyRule.builder()
        .match(MitmProxyRuleMatch.builder().urlContains(urlContains).build())
        .action(MitmProxyRuleAction.builder()
            .modifyRequest(MitmProxyRequestModification.builder()
                .headers(MitmProxyHeaderModification.builder().set(headers).build())
                .build())
            .build())
        .build();
  }

  /**
   * Set headers on the incoming response.
   */
  public static MitmProxyRule setResponseHeaders(String urlContains, Map<String, String> headers) {
    return MitmProxyRule.builder()
        .match(MitmProxyRuleMatch.builder().urlContains(urlContains).build())
        .action(MitmProxyRuleAction.builder()
            .modifyResponse(MitmProxyResponseModification.builder()
                .headers(MitmProxyHeaderModification.builder().set(headers).build())
                .build())
            .build())
        .build();
  }

  /**
   * Block requests matching the URL by responding with 403.
   */
  public static MitmProxyRule block(String urlContains) {
    return MitmProxyRule.builder()
        .match(MitmProxyRuleMatch.builder().urlContains(urlContains).build())
        .action(MitmProxyRuleAction.builder()
            .modifyResponse(MitmProxyResponseModification.builder()
                .statusCode(403)
                .body("")
                .build())
            .build())
        .build();
  }

  /**
   * Add query parameters to matching requests.
   */
  public static MitmProxyRule setQueryParams(String urlContains, Map<String, String> params) {
    return MitmProxyRule.builder()
        .match(MitmProxyRuleMatch.builder().urlContains(urlContains).build())
        .action(MitmProxyRuleAction.builder()
            .modifyRequest(MitmProxyRequestModification.builder()
                .params(MitmProxyParamModification.builder().set(params).build())
                .build())
            .build())
        .build();
  }

  /**
   * Prevent the browser from using cached responses by stripping cache-negotiation
   * headers from outgoing requests and forcing {@code Cache-Control: no-store} on
   * incoming responses.
   * <p>
   * Apply this rule <b>before</b> navigating so assets are never cached in the
   * first place.  Subsequent interception rules will then always see full
   * {@code 200 OK} responses with a body to modify.
   *
   * @param urlContains substring the URL must contain ({@code ""} to match all traffic)
   */
  public static MitmProxyRule disableCaching(String urlContains) {
    return MitmProxyRule.builder()
        .priority(Integer.MAX_VALUE)
        .match(MitmProxyRuleMatch.builder().urlContains(urlContains).build())
        .action(MitmProxyRuleAction.builder()
            .modifyRequest(MitmProxyRequestModification.builder()
                .headers(MitmProxyHeaderModification.builder()
                    .remove(List.of(
                        "If-None-Match",
                        "If-Modified-Since",
                        "If-Range"))
                    .build())
                .build())
            .modifyResponse(MitmProxyResponseModification.builder()
                .headers(MitmProxyHeaderModification.builder()
                    .set(Map.of(
                        "Cache-Control", "no-store, no-cache, must-revalidate",
                        "Pragma", "no-cache"))
                    .remove(List.of("ETag", "Last-Modified"))
                    .build())
                .build())
            .build())
        .build();
  }

  /**
   * Disable caching for all traffic passing through the proxy.
   *
   * @see #disableCaching(String)
   */
  public static MitmProxyRule disableCaching() {
    return disableCaching("");
  }

  /**
   * Replace an image (or any binary asset) at matching URLs with a local file.
   * The file content is base64-encoded and sent as {@code bodyBase64}.
   *
   * @param urlContains substring the request URL must contain
   * @param imageFile   local image file to serve as the replacement
   * @param contentType MIME type of the replacement (e.g. {@code "image/png"})
   */
  public static MitmProxyRule replaceImage(String urlContains, File imageFile, String contentType) throws IOException {
    byte[] bytes = Files.readAllBytes(imageFile.toPath());
    String base64 = Base64.getEncoder().encodeToString(bytes);
    return replaceImageBase64(urlContains, base64, contentType);
  }

  /**
   * Replace an image (or any binary asset) at matching URLs with base64-encoded content.
   * Requires the mitmproxy grid addon to handle the {@code bodyBase64} field.
   *
   * @param urlContains   substring the request URL must contain
   * @param base64Content base64-encoded replacement content
   * @param contentType   MIME type of the replacement (e.g. {@code "image/png"})
   */
  public static MitmProxyRule replaceImageBase64(String urlContains, String base64Content, String contentType) {
    return MitmProxyRule.builder()
        .match(MitmProxyRuleMatch.builder().urlContains(urlContains).build())
        .action(MitmProxyRuleAction.builder()
            .modifyResponse(MitmProxyResponseModification.builder()
                .statusCode(200)
                .headers(MitmProxyHeaderModification.builder()
                    .set(Map.of(
                        "Content-Type", contentType,
                        "Cache-Control", "no-cache, no-store, must-revalidate",
                        "Pragma", "no-cache"))
                    .build())
                .bodyBase64(base64Content)
                .build())
            .build())
        .build();
  }
}
