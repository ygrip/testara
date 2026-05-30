package io.github.ygrip.testara.agent.index;

import java.nio.file.Path;
import java.util.List;

public record DriverIndex(
    String name,
    String engineClass,
    List<String> platforms,
    String browserName,
    Path sourcePath,
    String className
) {}
