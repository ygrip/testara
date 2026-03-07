package io.github.ygrip.testara.ui.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Criteria a flow must satisfy for the rule to fire.
 * Maps to {@code RuleMatch} in the MitmProxy Grid API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MitmProxyRuleMatch {
  private String urlContains;
  private String urlPattern;
  private String method;
  private String contentType;
  private String responseContentType;
}
