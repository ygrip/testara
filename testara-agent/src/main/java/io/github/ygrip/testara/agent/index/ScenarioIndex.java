package io.github.ygrip.testara.agent.index;

import java.util.List;

public record ScenarioIndex(
    String name,
    ScenarioType type,
    List<String> tags,
    List<StepIndex> steps,
    List<ExamplesIndex> examples
) {}
