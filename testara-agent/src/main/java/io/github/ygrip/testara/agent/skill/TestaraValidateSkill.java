package io.github.ygrip.testara.agent.skill;

import io.github.ygrip.testara.agent.index.TestaraProjectProfile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * Pre-flight validation: scans the project for common configuration issues
 * before running tests. Returns a categorised pass/fail checklist.
 *
 * Checks:
 *  1. configuration.properties in src/test/resources (not src/main or missing)
 *  2. application.properties in src/test/resources (not src/main or missing)
 *  3. cucumber.glue includes io.github.ygrip.testara and the project base package
 *  4. No Page classes contain TODO selectors
 *  5. Required config keys present for active slices
 *  6. web.page.*.url keys present for all Page classes (UI projects)
 */
public class TestaraValidateSkill implements AgentSkill<Void, String> {

  @Override
  public String name() { return "testara-validate"; }

  @Override
  public String execute(Void input, AgentContext context) {
    Path root = context.projectRoot();
    TestaraProjectProfile profile = context.profile();
    boolean concise = "concise".equals(context.options().get("format"));

    List<Check> checks = new ArrayList<>();

    checks.addAll(checkPropertyFiles(root));
    checks.addAll(checkCucumberGlue(root, profile));
    checks.addAll(checkPageSelectors(root));
    checks.addAll(checkRequiredConfig(root, profile));

    long passed  = checks.stream().filter(c -> c.passed).count();
    long failed  = checks.stream().filter(c -> !c.passed).count();
    long warnings = checks.stream().filter(c -> c.warn).count();

    if (concise) {
      StringBuilder sb = new StringBuilder();
      sb.append("pre-flight: ").append(passed).append(" passed, ").append(failed).append(" failed");
      if (warnings > 0) sb.append(", ").append(warnings).append(" warnings");
      sb.append("\n");
      checks.forEach(c -> sb.append(c.passed ? (c.warn ? "⚠ WARN" : "✓ PASS") : "✗ FAIL")
          .append(" [").append(c.category).append("] ").append(c.message).append("\n")
          .append(c.detail != null && !c.detail.isBlank() ? "       " + c.detail + "\n" : ""));
      if (failed > 0) sb.append("\nfix: address FAIL items before running mvn verify.\n");
      return sb.toString();
    }

    StringBuilder sb = new StringBuilder();
    sb.append("# Testara Pre-flight Validation\n\n");
    sb.append("**Result:** ").append(failed == 0 ? "✅ READY" : "❌ " + failed + " issue(s) must be fixed").append("\n");
    sb.append("**Checks:** ").append(passed).append(" passed, ").append(failed).append(" failed");
    if (warnings > 0) sb.append(", ").append(warnings).append(" warnings");
    sb.append("\n\n");

    Map<String, List<Check>> byCategory = new LinkedHashMap<>();
    checks.forEach(c -> byCategory.computeIfAbsent(c.category, k -> new ArrayList<>()).add(c));
    byCategory.forEach((cat, list) -> {
      sb.append("### ").append(cat).append("\n\n");
      list.forEach(c -> {
        String icon = c.passed ? (c.warn ? "⚠" : "✅") : "❌";
        sb.append(icon).append(" **").append(c.message).append("**\n");
        if (c.detail != null && !c.detail.isBlank())
          sb.append("  > ").append(c.detail).append("\n");
      });
      sb.append("\n");
    });
    if (failed > 0) sb.append("> Fix all ❌ items before running `mvn verify`.\n");
    return sb.toString();
  }

  // ── Checks ────────────────────────────────────────────────────────────────

  private List<Check> checkPropertyFiles(Path root) {
    List<Check> out = new ArrayList<>();

    // configuration.properties
    Path testConfig = root.resolve("src/test/resources/configuration.properties");
    Path mainConfig = root.resolve("src/main/resources/configuration.properties");
    if (Files.exists(testConfig)) {
      out.add(pass("Properties", "configuration.properties found at src/test/resources/"));
    } else if (Files.exists(mainConfig)) {
      out.add(fail("Properties",
          "configuration.properties found in src/main/resources — WRONG scope",
          "Move to src/test/resources/configuration.properties. Testara reads from test classpath; main-scope files cause silent NullPointerException in ClassScanner."));
    } else {
      out.add(fail("Properties", "configuration.properties not found",
          "Run testara_init or create src/test/resources/configuration.properties manually."));
    }

    // application.properties
    Path testApp = root.resolve("src/test/resources/application.properties");
    Path mainApp = root.resolve("src/main/resources/application.properties");
    if (Files.exists(testApp)) {
      out.add(pass("Properties", "application.properties found at src/test/resources/"));
    } else if (Files.exists(mainApp)) {
      out.add(warn("Properties",
          "application.properties found in src/main/resources — WRONG scope",
          "Move to src/test/resources/application.properties or application-{env}.properties."));
    } else {
      out.add(warn("Properties", "application.properties not found",
          "Create src/test/resources/application.properties with environment-specific values."));
    }
    return out;
  }

  private List<Check> checkCucumberGlue(Path root, TestaraProjectProfile profile) {
    List<Check> out = new ArrayList<>();
    Path jup = root.resolve("src/test/resources/cucumber.properties");
    Path jprop = root.resolve("src/test/resources/junit-platform.properties");

    String glueContent = "";
    for (Path p : List.of(jup, jprop)) {
      if (Files.exists(p)) {
        try { glueContent += Files.readString(p, StandardCharsets.UTF_8); }
        catch (IOException ignored) {}
      }
    }

    boolean hasTestara = glueContent.contains("io.github.ygrip.testara");
    boolean hasProject = !glueContent.isBlank() &&
        (glueContent.contains("cucumber.glue") || glueContent.contains("glue ="));

    if (hasTestara) {
      out.add(pass("Glue", "cucumber.glue includes io.github.ygrip.testara (built-in steps)"));
    } else {
      out.add(fail("Glue", "cucumber.glue missing io.github.ygrip.testara",
          "Add 'cucumber.glue=io.github.ygrip.testara,{basePackage}' to src/test/resources/cucumber.properties."));
    }
    return out;
  }

  private List<Check> checkPageSelectors(Path root) {
    List<Check> out = new ArrayList<>();
    Path pageDir = root.resolve("src/main/java");
    if (!Files.exists(pageDir)) return out;

    List<String> todoPages = new ArrayList<>();
    try (Stream<Path> walk = Files.walk(pageDir)) {
      walk.filter(p -> p.toString().endsWith("Page.java"))
          .forEach(p -> {
            try {
              String src = Files.readString(p, StandardCharsets.UTF_8);
              if (src.contains("TODO") && src.contains("Locator")) {
                todoPages.add(root.relativize(p).toString());
              }
            } catch (IOException ignored) {}
          });
    } catch (IOException ignored) {}

    if (todoPages.isEmpty()) {
      out.add(pass("Selectors", "No Page classes with TODO selectors found"));
    } else {
      out.add(fail("Selectors",
          todoPages.size() + " Page class(es) contain TODO selectors",
          "Replace TODO with real DOM selectors in: " + String.join(", ", todoPages)));
    }
    return out;
  }

  private List<Check> checkRequiredConfig(Path root, TestaraProjectProfile profile) {
    List<Check> out = new ArrayList<>();
    Map<String, String> props = new LinkedHashMap<>();

    for (Path p : List.of(
        root.resolve("src/test/resources/configuration.properties"),
        root.resolve("src/test/resources/application.properties"))) {
      if (Files.exists(p)) {
        try {
          Files.readAllLines(p, StandardCharsets.UTF_8).stream()
              .filter(l -> !l.isBlank() && !l.startsWith("#") && l.contains("="))
              .forEach(l -> { int eq = l.indexOf('='); props.put(l.substring(0, eq).trim(), l.substring(eq + 1).trim()); });
        } catch (IOException ignored) {}
      }
    }

    if (props.isEmpty()) {
      out.add(warn("Config", "No properties loaded — skipping config key checks", null));
      return out;
    }

    // Check UI: selenium or playwright scan locations present
    boolean hasUiEngine = props.keySet().stream()
        .anyMatch(k -> k.startsWith("selenium.driver") || k.startsWith("playwright.browser"));
    boolean hasDefaultEngine = props.containsKey("automation.engine.default-engine");
    if (hasUiEngine || hasDefaultEngine) {
      boolean hasPageUrl = props.keySet().stream().anyMatch(k -> k.matches("web\\.page\\..+\\.url"));
      if (hasPageUrl) {
        out.add(pass("Config", "web.page.*.url keys present for UI navigation"));
      } else {
        out.add(warn("Config", "No web.page.*.url keys found for UI project",
            "Add 'web.page.{device}.{pageName}.url=http://...' to src/test/resources/application.properties for each Page class."));
      }
    }

    // Check API: api.service.* present if [api] slice
    boolean hasApiSteps = profile != null && profile.features().stream()
        .anyMatch(f -> f.tags().contains("@api"));
    if (hasApiSteps) {
      boolean hasApiService = props.keySet().stream().anyMatch(k -> k.startsWith("api.service."));
      if (hasApiService) {
        out.add(pass("Config", "api.service.* keys present for API tests"));
      } else {
        out.add(warn("Config", "No api.service.* keys found but @api features exist",
            "Add 'api.service.{alias}.host=...' to configuration.properties."));
      }
    }

    return out;
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  private record Check(String category, boolean passed, boolean warn, String message, String detail) {}

  private Check pass(String cat, String msg)               { return new Check(cat, true,  false, msg, null); }
  private Check warn(String cat, String msg, String detail){ return new Check(cat, true,  true,  msg, detail); }
  private Check fail(String cat, String msg, String detail){ return new Check(cat, false, false, msg, detail); }
}
