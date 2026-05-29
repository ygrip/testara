package io.github.ygrip.testara.agent.index;

import java.nio.file.Path;

public record StepDefinitionIndex(
    String annotation,
    String expression,
    Path sourcePath,
    String className,
    String methodName
) {}
