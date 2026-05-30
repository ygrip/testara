package io.github.ygrip.testara.validation.model;

import java.util.List;

public record ValidatorCatalogEntry(
    String name,
    List<String> aliases,
    String actualType,
    String expectedType
) {}
