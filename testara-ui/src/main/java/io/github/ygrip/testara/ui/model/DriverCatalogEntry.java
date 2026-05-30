package io.github.ygrip.testara.ui.model;

import java.util.List;

public record DriverCatalogEntry(
    String name,
    String engineId,
    List<String> platforms,
    String browserName
) {}
