package io.github.ygrip.testara.agent.catalog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
  @Test
  void uiDriverExampleKeysExistOnTheDriverPropertiesClasses(@TempDir Path root) throws IOException {
    Path config = root.resolve("src/main/java/io/github/ygrip/testara/ui/config");
    Files.createDirectories(config);
    Files.writeString(config.resolve("PlaywrightDriverProperties.java"), """
        @LoadProperties(prefix = "playwright.browser")
        public class PlaywrightDriverProperties { }
        """);
    Files.writeString(config.resolve("AppiumDriverProperties.java"), """
        @LoadProperties(prefix = "appium.driver")
        public class AppiumDriverProperties { }
        """);

    List<String> indexed = examples(new RuntimeCatalogIndexer().index(root, List.of()));
    List<String> builtIn = examples(RuntimeCatalogIndexer.builtInCatalog());

    for (List<String> keys : List.of(indexed, builtIn)) {
      assertFalse(keys.contains("playwright.browser.browserType"), keys::toString);
      assertFalse(keys.contains("appium.driver.platformName"), keys::toString);
      assertFalse(keys.contains("appium.driver.deviceName"), keys::toString);
      assertTrue(keys.contains("playwright.browser.page-scan-locations"), keys::toString);
      assertTrue(keys.contains("appium.driver.capabilities.android.{name}.platformName"), keys::toString);
    }
  }

  private List<String> examples(List<RuntimeCatalogEntry> catalog) {
    return catalog.stream()
        .filter(entry -> entry.prefix().startsWith("playwright") || entry.prefix().startsWith("appium"))
        .flatMap(entry -> entry.exampleKeys().stream())
        .toList();
  }
}
