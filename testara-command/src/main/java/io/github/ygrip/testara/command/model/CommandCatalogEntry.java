package io.github.ygrip.testara.command.model;

import java.util.List;

public record CommandCatalogEntry(
    String name,
    List<String> aliases,
    List<String> subCommands,
    boolean cacheable,
    String returnType
) {}
