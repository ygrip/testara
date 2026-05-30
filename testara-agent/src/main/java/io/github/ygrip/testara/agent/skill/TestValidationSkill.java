package io.github.ygrip.testara.agent.skill;

import io.github.ygrip.testara.agent.index.ValidationIndex;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Skill: list validations, show validation detail, or generate a new ValidatorLogic / JSON validation.
 *
 * Modes (determined by description):
 *   blank / "--list"          → list all indexed validations
 *   "detail:<name>"           → show source + when/how-to-use docs for that validation
 *   any other text            → generate a new validation (warns if similar exists)
 */
public class TestValidationSkill implements AgentSkill<String, String> {

  private static final List<String> BUILT_IN = List.of(
      "EQUAL", "NOT_EQUAL", "EMPTY", "NOT_EMPTY", "CONTAINS", "CONTAINS_TEXT",
      "STARTS_WITH", "ENDS_WITH", "MATCH_PATTERN", "HAS_SIZE", "GREATER_THAN",
      "LESSER_THAN", "IN_RANGE_OF", "SORTED", "CONTAINS_KEY", "MATCH_SCHEMA");

  private static final Map<String, String> KEYWORD_MAP = Map.ofEntries(
      Map.entry("equal",       "EQUAL"),
      Map.entry("same",        "EQUAL"),
      Map.entry("not equal",   "NOT_EQUAL"),
      Map.entry("empty",       "EMPTY"),
      Map.entry("not empty",   "NOT_EMPTY"),
      Map.entry("contains",    "CONTAINS"),
      Map.entry("include",     "CONTAINS"),
      Map.entry("starts with", "STARTS_WITH"),
      Map.entry("ends with",   "ENDS_WITH"),
      Map.entry("match",       "MATCH_PATTERN"),
      Map.entry("pattern",     "MATCH_PATTERN"),
      Map.entry("size",        "HAS_SIZE"),
      Map.entry("count",       "HAS_SIZE"),
      Map.entry("greater",     "GREATER_THAN"),
      Map.entry("more than",   "GREATER_THAN"),
      Map.entry("less than",   "LESSER_THAN"),
      Map.entry("range",       "IN_RANGE_OF"),
      Map.entry("between",     "IN_RANGE_OF"),
      Map.entry("sorted",      "SORTED"),
      Map.entry("ordered",     "SORTED"),
      Map.entry("has key",     "CONTAINS_KEY"),
      Map.entry("schema",      "MATCH_SCHEMA")
  );

  @Override
  public String name() { return "test-validation"; }

  @Override
  public String execute(String description, AgentContext context) {
    List<ValidationIndex> validations = context.profile().validations();

    // List mode — delegate to ListValidationsSkill for consistent output
    String detail = context.options().get("detail");
    if (description == null || description.isBlank() || "--list".equalsIgnoreCase(description.strip())) {
      return new ListValidationsSkill().execute(null, context);
    }

    // Detail mode
    String detailName = detail != null ? detail
        : (description.startsWith("detail:") ? description.substring(7).strip() : null);
    if (detailName != null) {
      return renderDetail(detailName, validations);
    }

    // Generate mode
    return generateValidation(description, validations, context);
  }

  // ── Detail ─────────────────────────────────────────────────────────────────

  private String renderDetail(String name, List<ValidationIndex> validations) {
    Optional<ValidationIndex> found = validations.stream()
        .filter(v -> v.validation().equalsIgnoreCase(name)
            || v.aliases().stream().anyMatch(a -> a.equalsIgnoreCase(name))
            || v.className().equalsIgnoreCase(name))
        .findFirst();

    if (found.isEmpty()) {
      StringBuilder sb = new StringBuilder();
      sb.append("Validation `").append(name).append("` not found in project index.\n\n");
      if (!validations.isEmpty()) {
        sb.append("**Available custom validations:** ");
        sb.append(validations.stream().map(v -> "`" + v.validation() + "`").collect(Collectors.joining(", ")));
        sb.append("\n\n");
      }
      sb.append("For built-in validators, run `testara-agent test-validation --list`.\n");
      return sb.toString();
    }

    ValidationIndex v = found.get();
    StringBuilder sb = new StringBuilder();
    sb.append("## Validation: `").append(v.validation()).append("`\n\n");
    sb.append("| Field | Value |\n|-------|-------|\n");
    sb.append("| **Class** | `").append(v.className()).append("` |\n");
    sb.append("| **Actual type** | `").append(v.actualType()).append("` |\n");
    sb.append("| **Expected type** | `").append(v.expectedType()).append("` |\n");
    sb.append("| **Aliases** | ").append(v.aliases().isEmpty() ? "none"
        : v.aliases().stream().map(a -> "`" + a + "`").collect(Collectors.joining(", "))).append(" |\n");
    sb.append("| **Cacheable** | ").append(v.cacheable() ? "yes" : "no").append(" |\n");
    sb.append("| **Source** | `").append(v.sourcePath()).append("` |\n\n");

    sb.append("### When to use\n\n");
    sb.append("Use `").append(v.validation()).append("` when you need to verify that a `")
        .append(v.actualType()).append("` value matches an expected `").append(v.expectedType())
        .append("` value using the custom logic defined in `").append(v.className()).append("`.\n\n");

    sb.append("### How to use in a JSON validation file\n\n```json\n");
    sb.append("{\n  \"validation\": \"").append(v.validation()).append("\",\n");
    sb.append("  \"actual\": \"${commandThatReturns").append(v.actualType()).append("()}\",\n");
    sb.append("  \"expected\": \"expectedValue\"\n}\n```\n\n");
    sb.append("Placement: `src/test/resources/validations/<domain>/").append(v.validation().toLowerCase(Locale.ROOT)).append(".json`\n\n");

    if (!v.aliases().isEmpty()) {
      sb.append("### Aliases\n\nThe following aliases can be used interchangeably:\n");
      v.aliases().forEach(a -> sb.append("- `").append(a).append("`\n"));
      sb.append("\n");
    }

    sb.append("### Source\n\n```java\n");
    try {
      sb.append(Files.readString(v.sourcePath(), StandardCharsets.UTF_8));
    } catch (IOException e) {
      sb.append("// Source not readable: ").append(e.getMessage());
    }
    sb.append("```\n");
    return sb.toString();
  }

  // ── Generate ──────────────────────────────────────────────────────────────

  private String generateValidation(String description, List<ValidationIndex> validations, AgentContext context) {
    String mode = context.options().getOrDefault("mode", "auto");
    String pkg  = context.options().getOrDefault("package", "com.company.automation.validators");

    StringBuilder sb = new StringBuilder();

    // Warn if similar already exists
    String descLower = description.toLowerCase(Locale.ROOT);
    validations.stream()
        .filter(v -> descLower.contains(v.validation().toLowerCase(Locale.ROOT))
            || v.aliases().stream().anyMatch(a -> descLower.contains(a.toLowerCase(Locale.ROOT))))
        .findFirst()
        .ifPresent(v -> sb.append("> **Note:** Validation `").append(v.validation())
            .append("` already exists (`").append(v.className())
            .append("`). Consider reusing it or run `detail:").append(v.validation())
            .append("` to see its signature.\n\n"));

    String resolved = resolveBuiltIn(descLower);
    boolean useJson = "json".equals(mode) || ("auto".equals(mode) && resolved != null);

    sb.append("## Generated Validation\n\n**Description:** ").append(description).append("\n\n");

    if (useJson && resolved != null) {
      sb.append("**Mode:** JSON (uses built-in validator `").append(resolved).append("`)\n\n");
      sb.append("### validation.json\n\n```json\n");
      sb.append(generateValidationJson(resolved, description));
      sb.append("\n```\n\n");
      sb.append("**Placement:** `src/test/resources/validations/<domain>/<name>.json`\n");
    } else {
      String className = toClassName(description) + "Validator";
      sb.append("**Mode:** Java (custom ValidatorLogic)\n\n");
      sb.append("### ").append(className).append(".java\n\n```java\n");
      sb.append(generateValidatorClass(className, description, pkg));
      sb.append("```\n\n");
      sb.append("**Placement:** `src/test/java/").append(pkg.replace('.', '/')).append("/")
          .append(className).append(".java`\n\n");
      sb.append("**Scan config:**\n```properties\n");
      sb.append("validator.scan-locations=io.github.ygrip.testara,").append(pkg).append("\n```\n");
    }
    return sb.toString();
  }

  private String resolveBuiltIn(String desc) {
    for (Map.Entry<String, String> entry : KEYWORD_MAP.entrySet()) {
      if (desc.contains(entry.getKey())) return entry.getValue();
    }
    return null;
  }

  private String generateValidationJson(String validator, String description) {
    return """
        {
          "validation": "%s",
          "description": "%s",
          "expected": null
        }""".formatted(validator, description.replace("\"", "\\\""));
  }

  private String generateValidatorClass(String className, String description, String pkg) {
    return """
        package %s;

        import io.github.ygrip.testara.validation.model.ValidatorLogic;
        import io.github.ygrip.testara.validation.model.ValidationTag;

        /**
         * %s
         * Generated by Testara Agent — review before committing.
         */
        @ValidationTag(command = "%s")
        public class %s extends ValidatorLogic<Object, Object> {

          @Override
          protected String setDefaultMessage() {
            return "%s validation failed";
          }

          @Override
          public boolean validate() throws Exception {
            // TODO: implement validation logic
            // getActual() — the actual value from the test
            // getExpected() — the expected value from the step
            throw new UnsupportedOperationException("Not yet implemented");
          }
        }
        """.formatted(pkg, description, toValidationName(description), className, className);
  }

  private String toValidationName(String description) {
    String slug = description.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-")
        .replaceAll("^-|-$", "");
    return slug.substring(0, Math.min(40, slug.length()));
  }

  private String toClassName(String description) {
    String[] parts = description.replaceAll("[^a-zA-Z0-9]+", " ").trim().split("\\s+");
    StringBuilder sb = new StringBuilder();
    for (String p : parts) {
      if (!p.isBlank()) sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1).toLowerCase(Locale.ROOT));
    }
    return sb.toString();
  }
}
