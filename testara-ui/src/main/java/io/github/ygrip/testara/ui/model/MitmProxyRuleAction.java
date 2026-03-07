package io.github.ygrip.testara.ui.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * What to do when a rule matches. Both fields are optional and can be
 * combined so a single rule modifies both the request and the response.
 * Maps to {@code RuleAction} in the MitmProxy Grid API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MitmProxyRuleAction {
  private MitmProxyRequestModification modifyRequest;
  private MitmProxyResponseModification modifyResponse;
}
