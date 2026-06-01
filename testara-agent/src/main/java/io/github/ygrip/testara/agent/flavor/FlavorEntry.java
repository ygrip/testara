package io.github.ygrip.testara.agent.flavor;

import java.util.List;

/**
 * A single indexed Testara built-in step, classified by slice and capability.
 * All fields are discovered from source at agent build time — nothing is hardcoded.
 *
 * When the step uses Cucumber Expressions, {@code parameterTypes} lists the
 * {@code {typeName}} tokens in order, enabling the agent to suggest valid values.
 */
public record FlavorEntry(
    String slice,              // api | ui | sql | mongo | kafka | elastic | core
    String keyword,            // Given | When | Then
    String expression,         // step expression (Cucumber Expression or regex, anchors stripped)
    String example,            // ready-to-use gherkin step text
    String capability,         // semantic label derived from the expression
    String module,             // testara-api-cucumber, testara-ui-cucumber, etc.
    String className,          // ApiBaseSteps, UIBaseSteps, etc.
    List<String> parameterTypes // Cucumber Expression type names in order, e.g. ["actor","string","devices"]
) {
  /** Backwards-compatible constructor — parameterTypes defaults to empty list. */
  public FlavorEntry(String slice, String keyword, String expression, String example,
      String capability, String module, String className) {
    this(slice, keyword, expression, example, capability, module, className, List.of());
  }

  /** True when the step expression text contains the given keyword (case-insensitive). */
  public boolean matchesIntent(String kw) {
    String lower = kw.toLowerCase();
    return capability.toLowerCase().contains(lower)
        || expression.toLowerCase().contains(lower)
        || example.toLowerCase().contains(lower);
  }
}
