package io.github.ygrip.testara.agent.skill;

import java.nio.file.Path;

public record FilePatch(
    Path path,
    FilePatchOperation operation,
    String content,
    String reason
) {}
