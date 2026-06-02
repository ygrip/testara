package io.github.ygrip.testara.agent.skill;

import io.github.ygrip.testara.agent.flavor.FlavorEntry;
import io.github.ygrip.testara.agent.knowledge.FrameworkKnowledgeStore;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Skill: serve the embedded Testara agent guide and generation rules.
 *
 * Agents should call this at the start of a session or before generating
 * any Testara artifact to get the authoritative generation rules.
 *
 * The guide is embedded in the JAR at agent-context/generation-rules.md —
 * always reflects the version of testara-agent installed.
 */
public class TestaraGuideSkill implements AgentSkill<String, String> {

  @Override
  public String name() { return "testara-guide"; }

  @Override
  public String execute(String section, AgentContext context) {
    String guide = loadGuide();
    if (guide == null) return "Guide not available in this build.";

    boolean concise = "concise".equals(context.options().get("format"));

    if (section == null || section.isBlank() || "all".equalsIgnoreCase(section)) {
      return concise ? extractRulesOnly(guide) : guide;
    }

    // Return the section matching the keyword
    String lower = section.toLowerCase(Locale.ROOT);
    if ("steps".equals(lower) || "built-in steps".equals(lower) || "builtins".equals(lower)) {
      return renderBuiltInSteps(concise);
    }
    String[] lines = guide.split("\n");
    StringBuilder sb = new StringBuilder();
    boolean inSection = false;
    for (String line : lines) {
      if (line.startsWith("## ")) {
        inSection = line.toLowerCase(Locale.ROOT).contains(lower);
      }
      if (inSection) sb.append(line).append("\n");
    }
    return sb.length() > 0 ? sb.toString() : guide; // fallback to full guide if section not found
  }

  private String renderBuiltInSteps(boolean concise) {
    List<FlavorEntry> catalog = FrameworkKnowledgeStore.instance().flavorCatalog();
    if (catalog.isEmpty()) return "Built-in step catalog not available in this build.";

    Map<String, List<FlavorEntry>> bySlice = catalog.stream()
        .collect(Collectors.groupingBy(FlavorEntry::slice, LinkedHashMap::new, Collectors.toList()));
    StringBuilder sb = new StringBuilder("## Built-in Step Reference\n\n");
    sb.append("Source: generated from Testara Cucumber step definitions in this installed agent build.\n");
    sb.append("Use exact wording; quoted values are `{string}` parameters and must stay quoted in features.\n\n");
    List<String> slices = List.of("ui", "api", "sql", "mongo", "kafka", "elastic", "core");
    for (String slice : slices) {
      List<FlavorEntry> entries = bySlice.getOrDefault(slice, List.of());
      if (entries.isEmpty()) continue;
      sb.append("### ").append(slice).append(" (").append(entries.size()).append(")\n");
      representativeSteps(slice, entries, concise ? 8 : 16)
          .forEach(e -> sb.append("- `").append(e.keyword()).append(" ")
              .append(e.expression()).append("`")
              .append(" — ").append(e.className()).append("\n"));
      sb.append("\n");
    }
    return sb.toString();
  }

  private List<FlavorEntry> representativeSteps(String slice, List<FlavorEntry> entries, int limit) {
    List<String> priority = switch (slice) {
      case "ui" -> List.of("using {word} in {devices}", "open", "is in", "type value {string} to {string} in the",
          "enter value", "click the {string} in the", "should see {string} is {displayedOrNotDisplayed}", "element", "wait", "do");
      case "api" -> List.of("using service", "prepare", "process request", "statusCode", "response success", "assign previous");
      case "sql", "mongo", "elastic" -> List.of("connect", "prepare", "select", "execute", "assign previous");
      case "kafka" -> List.of("start kafka", "send kafka", "consume", "stop kafka");
      default -> List.of();
    };
    List<FlavorEntry> prioritized = priority.stream()
        .map(p -> entries.stream()
            .filter(e -> e.expression().toLowerCase(Locale.ROOT).contains(p.toLowerCase(Locale.ROOT)))
            .findFirst()
            .orElse(null))
        .filter(e -> e != null)
        .toList();
    return java.util.stream.Stream.concat(prioritized.stream(), entries.stream())
        .collect(Collectors.toMap(e -> e.keyword() + " " + e.expression(), e -> e, (a, b) -> a,
            LinkedHashMap::new))
        .values().stream()
        .limit(limit)
        .toList();
  }

  private String extractRulesOnly(String guide) {
    // Concise: quick guardrails plus section headings and hard rules.
    StringBuilder sb = new StringBuilder();
    boolean inQuickGuardrails = false;
    for (String line : guide.split("\n")) {
      if (line.startsWith("## ")) {
        inQuickGuardrails = line.startsWith("## Agent quick guardrails");
      }
      if (inQuickGuardrails && (line.startsWith("## ") || line.startsWith("- "))) {
        sb.append(line).append("\n");
      } else if (line.startsWith("## RULE") || line.startsWith("## Quick") || line.startsWith("## Testara")
          || line.startsWith("## Utilities") || line.startsWith("## Code") || line.startsWith("## Built-in")
          || line.startsWith("## Helper") || line.startsWith("## POM")) {
        sb.append(line).append("\n");
      } else if (line.startsWith("## DB Kafka Elastic") || line.startsWith("## UI Runtime Quirks")) {
        sb.append(line).append("\n");
      } else if (line.startsWith("### Quirk")) {
        sb.append(line).append("\n");
      } else if (line.startsWith("**Rule:**")) {
        sb.append("  ").append(line).append("\n");
      } else if (line.startsWith("MUST") || line.startsWith("ALLOWED") || line.startsWith("NEVER")
          || line.startsWith("WRONG") || line.startsWith("RIGHT") || line.startsWith("- Reuse")
          || line.startsWith("- Prefer") || line.startsWith("- Do not")) {
        sb.append("  ").append(line).append("\n");
      }
    }
    return sb.toString();
  }

  static String loadGuide() {
    try (InputStream is = TestaraGuideSkill.class.getClassLoader()
        .getResourceAsStream("agent-context/generation-rules.md")) {
      if (is == null) return null;
      return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    } catch (Exception e) {
      return null;
    }
  }
}
