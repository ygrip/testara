package io.github.ygrip.testara.agent.skill;

import io.github.ygrip.testara.agent.catalog.RuntimeCatalogEntry;
import io.github.ygrip.testara.agent.flavor.FlavorEntry;
import io.github.ygrip.testara.agent.index.TestaraProjectProfile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Skill: return full Testara runtime context for the project.
 *
 * Tells the agent what is installed, what config is present/missing,
 * what slices are active, and what steps/commands/validations are available.
 * This is the foundation skill that must be called before generating any artifact.
 */
public class TestaraContextSkill implements AgentSkill<Void, String> {

  @Override
  public String name() { return "testara-context"; }

  @Override
  public String execute(Void input, AgentContext context) {
    TestaraProjectProfile profile = context.profile();
    boolean concise = "concise".equals(context.options().get("format"));

    // Detect active slices from runtime catalog
    Set<String> activeSlices = profile.runtimeCatalog().stream()
        .map(RuntimeCatalogEntry::slice).collect(Collectors.toCollection(LinkedHashSet::new));

    // Read properties from configuration.properties
    Map<String, String> configuredProps = readConfigProperties(context.projectRoot());

    // Map which catalog prefixes have config present
    Map<String, Boolean> prefixCoverage = new LinkedHashMap<>();
    for (RuntimeCatalogEntry entry : profile.runtimeCatalog()) {
      boolean hasConfig = configuredProps.keySet().stream()
          .anyMatch(k -> k.startsWith(entry.prefix()));
      prefixCoverage.put(entry.prefix(), hasConfig);
    }

    // Count missing required config
    long missingCount = prefixCoverage.values().stream().filter(v -> !v).count();

    if (concise) {
      return renderConcise(profile, activeSlices, configuredProps, prefixCoverage, missingCount);
    }
    return renderFull(profile, activeSlices, configuredProps, prefixCoverage, missingCount);
  }

  private String renderConcise(TestaraProjectProfile profile, Set<String> slices,
      Map<String, String> props, Map<String, Boolean> coverage, long missing) {
    StringBuilder sb = new StringBuilder();
    sb.append("project-root: ").append(profile.projectRoot()).append("\n");
    sb.append("slices: ").append(String.join(", ", slices)).append("\n");
    sb.append("modules: ").append(profile.mavenModules().size()).append("\n");
    sb.append("feature-files: ").append(profile.features().size()).append(" | scenarios: ")
        .append(profile.totalScenarios()).append("\n");
    if (!profile.tags().isEmpty()) {
      sb.append("top-tags: ").append(profile.tags().stream().limit(12)
          .map(t -> t.tag() + "(" + t.scenarioCount() + ")")
          .collect(Collectors.joining(", "))).append("\n");
    }
    sb.append("flavor-steps: ").append(profile.flavorSteps().size()).append(" | ");
    sb.append("commands: ").append(profile.commands().size()).append(" | ");
    sb.append("validations: ").append(profile.validations().size()).append("\n");
    sb.append("config-keys: ").append(props.size()).append(" | missing-config-blocks: ").append(missing).append("\n");
    if (missing > 0) {
      sb.append("missing blocks: ");
      coverage.entrySet().stream().filter(e -> !e.getValue())
          .map(Map.Entry::getKey).forEach(p -> sb.append(p).append(" "));
      sb.append("\n");
    }
    if (!profile.flavorSteps().isEmpty()) {
      sb.append("sample-steps:\n");
      profile.flavorSteps().stream().limit(8)
          .forEach(s -> sb.append("- ").append(s.keyword()).append(" ").append(s.example()).append("\n"));
    }
    sb.append("properties() command: available for env-specific values\n");
    sb.append("prop(key) = alias for properties(key)");
    return sb.toString();
  }

  private String renderFull(TestaraProjectProfile profile, Set<String> slices,
      Map<String, String> props, Map<String, Boolean> coverage, long missing) {
    StringBuilder sb = new StringBuilder();
    sb.append("# Testara Runtime Context\n\n");
    sb.append("**Project root:** `").append(profile.projectRoot()).append("`\n");
    sb.append("**Java version:** ").append(profile.javaVersion()).append("\n");
    sb.append("**Build tool:** ").append(profile.buildTool()).append("\n\n");

    sb.append("## Active Slices\n\n");
    slices.forEach(s -> sb.append("- ").append(s).append("\n"));
    sb.append("\n");

    sb.append("## Available Catalog\n\n");
    sb.append("| Resource | Count |\n|----------|-------|\n");
    sb.append("| Flavor steps | ").append(profile.flavorSteps().size()).append(" |\n");
    sb.append("| Commands | ").append(profile.commands().size()).append(" |\n");
    sb.append("| Validations | ").append(profile.validations().size()).append(" |\n");
    sb.append("| Config keys present | ").append(props.size()).append(" |\n\n");

    sb.append("## Config Coverage\n\n");
    sb.append("| Prefix | Status |\n|--------|--------|\n");
    coverage.forEach((prefix, present) ->
        sb.append("| `").append(prefix).append("` | ").append(present ? "✓ configured" : "✗ missing").append(" |\n"));
    sb.append("\n");

    if (!profile.commands().isEmpty()) {
      sb.append("## Commands (top 10)\n\n");
      profile.commands().stream().limit(10)
          .forEach(c -> sb.append("- `").append(c.command()).append("`→").append(c.returnType()).append("\n"));
      if (profile.commands().size() > 10)
        sb.append("…and ").append(profile.commands().size() - 10).append(" more. Use `test-command` to list all.\n");
      sb.append("\n");
    }

    sb.append("## Key Rules\n\n");
    sb.append("- Use `properties(key)` for: URLs, hosts, credentials, topic names, DB names, emails, test data\n");
    sb.append("- Use `prop(key)` as a shorter alias\n");
    sb.append("- Use `uuid()`, `timestamp()`, `combine()` for dynamic generated values\n");
    sb.append("- Do NOT hardcode environment-specific values in feature files\n");
    return sb.toString();
  }

  private Map<String, String> readConfigProperties(Path projectRoot) {
    Map<String, String> props = new LinkedHashMap<>();
    for (String candidate : List.of(
        "src/test/resources/configuration.properties",
        "configuration.properties")) {
      Path p = projectRoot.resolve(candidate);
      if (Files.exists(p)) {
        try {
          Files.readAllLines(p, StandardCharsets.UTF_8).stream()
              .filter(l -> !l.isBlank() && !l.startsWith("#") && l.contains("="))
              .forEach(l -> {
                int eq = l.indexOf('=');
                props.put(l.substring(0, eq).trim(), l.substring(eq + 1).trim());
              });
        } catch (IOException ignored) {}
        break;
      }
    }
    return props;
  }
}
