package io.github.ygrip.testara.ui.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Rule as returned by the API (includes positional index).
 * Maps to {@code RuleResponse} in the MitmProxy Grid API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MitmProxyRuleResponse {
  private int index;
  private boolean enabled;
  private int priority;
  private MitmProxyRuleMatch match;
  private MitmProxyRuleAction action;
}
