package io.github.ygrip.testara.agent.catalog;

import io.github.ygrip.testara.agent.flavor.FlavorEntry;
import io.github.ygrip.testara.agent.index.StepDefinitionIndex;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Validates generated Testara artifacts before they are returned to the agent.
 *
 * Catches common guardrail violations:
 *  - hardcoded env-specific values that should use properties()
 *  - steps that do not link to any indexed Testara built-in or project glue
 */
public final class GenerationGuard {

  private static final Pattern LOCALHOST = Pattern.compile("\"[^\"]*localhost[^\"]*\"");
  private static final Pattern HTTP_URL   = Pattern.compile("\"https?://[^\"]+\"");
  private static final Pattern CREDENTIAL = Pattern.compile("\"(?i)[^\"]*(?:password|token|secret|api[_-]?key)[^\"]*\"");

  public record Violation(Severity severity, String rule, String line, String suggestion) {
    public enum Severity { ERROR, WARN, INFO }
    public String format() {
      return "[" + severity + "] Rule " + rule + ": `" + line.strip() + "` → " + suggestion;
    }
  }

  /** Validate a generated feature file content and return any violations. */
  public static List<Violation> validateFeature(String featureContent) {
    List<Violation> violations = new ArrayList<>();
    if (featureContent == null) return violations;

    String[] lines = featureContent.split("\n");
    for (String line : lines) {
      String stripped = line.strip();
      if (!stripped.startsWith("Given") && !stripped.startsWith("When")
          && !stripped.startsWith("Then") && !stripped.startsWith("And")) continue;

      // Rule 1: localhost URLs
      if (LOCALHOST.matcher(stripped).find() && !stripped.contains("properties(")) {
        violations.add(new Violation(Violation.Severity.ERROR, "1",
            stripped, "Replace hardcoded localhost with properties(api.service.*.host)"));
      }
      // Rule 1: http:// URLs
      if (HTTP_URL.matcher(stripped).find() && !stripped.contains("properties(")) {
        violations.add(new Violation(Violation.Severity.ERROR, "1",
            stripped, "Replace hardcoded URL with properties(key)"));
      }
      // Rule 1: credentials
      if (CREDENTIAL.matcher(stripped).find() && !stripped.contains("properties(")) {
        violations.add(new Violation(Violation.Severity.ERROR, "1",
            stripped, "Replace hardcoded credential with properties(test.*.token/password)"));
      }
    }
    return violations;
  }

  /** Validate generated feature content and require every step to link to known glue. */
  public static List<Violation> validateFeature(String featureContent, List<FlavorEntry> flavorSteps,
      List<StepDefinitionIndex> projectSteps) {
    List<Violation> violations = new ArrayList<>(validateFeature(featureContent));
    StepLinker.linkFeature(featureContent, flavorSteps, projectSteps).stream()
        .filter(link -> !link.matched())
        .forEach(link -> violations.add(new Violation(Violation.Severity.ERROR, "STEP",
            "line " + link.lineNumber() + ": " + link.stepLine(),
            "Use an indexed Testara built-in step, generate a UserAction and call it with the built-in action step, or add a project step definition.")));
    return violations;
  }

  /**
   * Append a guardrail summary to output if violations are found.
   * Returns the output unchanged if clean.
   */
  public static String annotate(String output, List<Violation> violations) {
    if (violations.isEmpty()) return output;
    StringBuilder sb = new StringBuilder(output);
    long errors = violations.stream().filter(v -> v.severity() == Violation.Severity.ERROR).count();
    long warns  = violations.stream().filter(v -> v.severity() == Violation.Severity.WARN).count();
    sb.append("\n\n---\n**Guardrail findings: ").append(errors).append(" errors, ").append(warns).append(" warnings**\n");
    violations.forEach(v -> sb.append("- ").append(v.format()).append("\n"));
    sb.append("\nSee `testara-guide` or `testara-property rules` for correction guidance.");
    return sb.toString();
  }

  private GenerationGuard() {}
}
