package io.github.ygrip.testara.agent.knowledge;

import java.nio.file.Path;

public record FileFingerprint(
    Path path,
    FileType type,
    long size,
    long lastModifiedMillis,
    String sha256
) {}
