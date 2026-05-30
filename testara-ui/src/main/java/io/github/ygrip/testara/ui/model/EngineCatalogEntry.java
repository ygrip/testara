package io.github.ygrip.testara.ui.model;

import java.util.List;

public record EngineCatalogEntry(
    String id,
    String engineClass,
    List<DriverCatalogEntry> drivers
) {}
