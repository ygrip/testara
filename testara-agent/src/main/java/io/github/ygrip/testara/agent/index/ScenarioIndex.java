package io.github.ygrip.testara.agent.index;

import java.util.List;

public record ScenarioIndex(
    String name,
    ScenarioType type,
    List<String> tags,
    List<StepIndex> steps,
    List<ExamplesIndex> examples,
    List<StepIndex> ruleBackgroundSteps  // Background of the enclosing Rule; the feature Background lives on FeatureIndex
) {
  public ScenarioIndex {
    if (ruleBackgroundSteps == null) ruleBackgroundSteps = List.of();
  }

  /** Backwards-compatible constructor — scenario outside a Rule (no rule background). */
  public ScenarioIndex(String name, ScenarioType type, List<String> tags, List<StepIndex> steps,
      List<ExamplesIndex> examples) {
    this(name, type, tags, steps, examples, List.of());
  }
}
