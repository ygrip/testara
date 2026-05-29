package io.github.ygrip.testara.agent.index;

import java.nio.file.Path;
import java.util.List;

public record ValidationIndex(
    String validation,
    List<String> aliases,
    boolean cacheable,
    Path sourcePath,
    String className
) {}
