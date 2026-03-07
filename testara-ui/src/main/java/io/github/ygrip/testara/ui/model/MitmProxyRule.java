package io.github.ygrip.testara.ui.model;

import java.util.Map;

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
  public static MitmProxyRule mockResponse(String urlContains, int statusCode, String body) {
    return MitmProxyRule.builder()
        .match(MitmProxyRuleMatch.builder().urlContains(urlContains).build())
        .action(MitmProxyRuleAction.builder()
            .modifyResponse(MitmProxyResponseModification.builder()
                .statusCode(statusCode)
                .body(body)
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
}
