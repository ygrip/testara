package io.github.ygrip.testara.agent.flavor;

/**
 * A single indexed Testara built-in step, classified by slice and capability.
 * All fields are discovered from source — nothing is hardcoded.
 */
public record FlavorEntry(
    String slice,       // api | ui | sql | mongo | kafka | elastic | core
    String keyword,     // Given | When | Then
    String expression,  // raw step regex (with anchors stripped)
    String example,     // ready-to-use gherkin step text with {placeholder} substitutions
    String capability,  // semantic label derived from the expression text
    String module,      // testara-api-cucumber, testara-ui-cucumber, etc.
    String className    // ApiBaseSteps, UIBaseSteps, etc.
) {
  /** True when the step expression text contains the given keyword (case-insensitive). */
  public boolean matchesIntent(String keyword) {
    String lower = keyword.toLowerCase();
    return capability.toLowerCase().contains(lower)
        || expression.toLowerCase().contains(lower)
        || example.toLowerCase().contains(lower);
  }
}
