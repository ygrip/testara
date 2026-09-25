package io.github.ygrip.testara.agent.catalog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeCatalogIndexerTest {

  @Test
  void mergesProjectLoadPropertiesClassesIntoBuiltInCatalog(@TempDir Path root) throws IOException {
    Path config = root.resolve("src/main/java/com/acme/config");
    Files.createDirectories(config);
    Files.writeString(config.resolve("AcmeConfig.java"), """
        package com.acme.config;
        @LoadProperties(prefix = "acme.client")
        public class AcmeConfig { }
        """);

    List<RuntimeCatalogEntry> catalog = new RuntimeCatalogIndexer().index(root, List.of());

    List<String> prefixes = catalog.stream().map(RuntimeCatalogEntry::prefix).toList();
    assertTrue(prefixes.contains("acme.client"), prefixes::toString);
    assertTrue(prefixes.containsAll(RuntimeCatalogIndexer.builtInCatalog().stream()
        .map(RuntimeCatalogEntry::prefix).toList()), "built-in entries must survive: " + prefixes);
    assertEquals(prefixes.size(), prefixes.stream().distinct().count(), "prefixes must be unique");
  }
}
