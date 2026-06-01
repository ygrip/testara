package io.github.ygrip.testara.agent.skill;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

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
      } else if (line.startsWith("## DB Kafka Elastic")) {
        sb.append(line).append("\n");
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
