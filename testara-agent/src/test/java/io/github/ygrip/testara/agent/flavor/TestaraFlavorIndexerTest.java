package io.github.ygrip.testara.agent.flavor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestaraFlavorIndexerTest {

  @Test
  void indexesNestedModulesOnceIncludingAndSteps(@TempDir Path root) throws IOException {
    Path steps = root.resolve("testara-api-cucumber/src/main/java/io/github/ygrip/testara/api/steps");
    Files.createDirectories(steps);
    Files.writeString(steps.resolve("ApiBaseSteps.java"), """
        package io.github.ygrip.testara.api.steps;
        public class ApiBaseSteps {
          @Given("{actor} prepare request")
          public void prepare(String actor) { }
          @And("{actor} send the request")
          public void send(String actor) { }
        }
        """);

    List<FlavorEntry> entries = new TestaraFlavorIndexer().index(root, List.of("testara-api-cucumber"));

    assertEquals(2, entries.size(), entries::toString);
    assertTrue(entries.stream().allMatch(e -> e.slice().equals("api")));
    assertTrue(entries.stream().anyMatch(e -> e.keyword().equals("And")));
  }

  @Test
  void detectsSliceFromPathBelowScanRootOnly(@TempDir Path parent) throws IOException {
    // The project lives under a directory whose name looks like a Testara cucumber module.
    Path root = parent.resolve("testara-cucumber-demo");
    Path steps = root.resolve("src/main/java/com/acme");
    Files.createDirectories(steps);
    Files.writeString(steps.resolve("LoginSteps.java"), """
        package com.acme;
        public class LoginSteps {
          @Given("user opens the login page")
          public void open() { }
        }
        """);

    List<FlavorEntry> entries = new TestaraFlavorIndexer().index(root, List.of());

    assertTrue(entries.isEmpty(), "project steps must not be classified as built-in flavor: " + entries);
  }
}
