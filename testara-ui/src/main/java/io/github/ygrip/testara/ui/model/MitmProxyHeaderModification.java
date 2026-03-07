package io.github.ygrip.testara.ui.model;

import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Add, overwrite, or remove HTTP headers.
 * Maps to {@code HeaderModification} in the MitmProxy Grid API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MitmProxyHeaderModification {
  private Map<String, String> set;
  private List<String> remove;
}
