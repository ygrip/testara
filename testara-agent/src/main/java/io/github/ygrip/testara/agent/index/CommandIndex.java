package io.github.ygrip.testara.agent.index;

import java.nio.file.Path;
import java.util.List;

public record CommandIndex(
    String command,
    List<String> aliases,
    String returnType,
    boolean cacheable,
    Path sourcePath,
    String className
) {}
