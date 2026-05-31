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

  // No hardcoded lists — all validators are discovered dynamically from the indexed profile.

  @Override
  public String name() { return "test-validation"; }

  @Override
  public String execute(String description, AgentContext context) {
    List<ValidationIndex> validations = context.profile().validations();
    boolean concise = "concise".equals(context.options().get("format"));

    String detail = context.options().get("detail");
    if (description == null || description.isBlank() || "--list".equalsIgnoreCase(description.strip())) {
      return new ListValidationsSkill().execute(null, context);
    }

    String detailName = detail != null ? detail
        : (description.startsWith("detail:") ? description.substring(7).strip() : null);
    if (detailName != null) {
      return renderDetail(detailName, validations, concise);
    }

    return generateValidation(description, validations, context, concise);
  }

  // ── Detail ─────────────────────────────────────────────────────────────────

  private String renderDetail(String name, List<ValidationIndex> validations, boolean concise) {
    Optional<ValidationIndex> found = validations.stream()
        .filter(v -> v.validation().equalsIgnoreCase(name)
            || v.aliases().stream().anyMatch(a -> a.equalsIgnoreCase(name))
            || v.className().equalsIgnoreCase(name))
        .findFirst();

    if (found.isEmpty()) {
      String available = validations.isEmpty() ? "none (check built-ins with --list)"
          : validations.stream().map(ValidationIndex::validation).collect(Collectors.joining(", "));
      return "validation '" + name + "' not found. available custom: " + available;
    }

    ValidationIndex v = found.get();
    if (concise) {
      StringBuilder sb = new StringBuilder();
      sb.append("validation: ").append(v.validation());
      sb.append(" | actual: ").append(v.actualType()).append(" | expected: ").append(v.expectedType());
      sb.append(" | cacheable: ").append(v.cacheable() ? "yes" : "no");
      if (!v.aliases().isEmpty()) sb.append(" | aliases: ").append(String.join(", ", v.aliases()));
      sb.append("\nwhen: verify ").append(v.actualType()).append(" matches ").append(v.expectedType());
      sb.append("\nusage: {\"validation\":\"").append(v.validation())
          .append("\",\"actual\":\"${cmd()}\",\"expected\":\"value\"}");
      sb.append("\nsource: ").append(v.sourcePath());
      return sb.toString();
    }

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
    sb.append("### When to use\nVerify a `").append(v.actualType()).append("` against an expected `")
        .append(v.expectedType()).append("` using `").append(v.className()).append("`.\n\n");
    sb.append("### Usage\n\n```json\n{\"validation\":\"").append(v.validation())
        .append("\",\"actual\":\"${cmd()}\",\"expected\":\"value\"}\n```\n\n");
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

  private String generateValidation(String description, List<ValidationIndex> validations,
      AgentContext context, boolean concise) {
    String mode = context.options().getOrDefault("mode", "auto");
    String pkg  = context.options().getOrDefault("package", "io.github.ygrip.testara.validation");

    StringBuilder sb = new StringBuilder();

    // Check if description matches an already-indexed validator (built-in or custom)
    String descLower = description.toLowerCase(Locale.ROOT);
    Optional<ValidationIndex> existing = validations.stream()
        .filter(v -> descLower.contains(v.validation().toLowerCase(Locale.ROOT))
            || v.aliases().stream().anyMatch(a -> descLower.contains(a.toLowerCase(Locale.ROOT))))
        .findFirst();

    existing.ifPresent(v -> sb.append(concise
        ? "note: '" + v.validation() + "' already exists (" + v.className() + "). run detail:" + v.validation() + " to review.\n\n"
        : "> **Note:** Validation `" + v.validation() + "` already exists (`" + v.className()
          + "`). Consider reusing it or run `detail:" + v.validation() + "` to see its signature.\n\n"));

    // Auto-resolve: check if any indexed validator name is a keyword match in the description
    Optional<ValidationIndex> resolved = existing.isPresent() ? existing
        : validations.stream()
            .filter(v -> descLower.contains(v.validation().toLowerCase(Locale.ROOT).replace("_", " "))
                || descLower.contains(v.validation().toLowerCase(Locale.ROOT)))
            .findFirst();

    boolean useJson = "json".equals(mode) || ("auto".equals(mode) && resolved.isPresent());

    if (useJson && resolved.isPresent()) {
      ValidationIndex v = resolved.get();
      if (concise) {
        sb.append("mode: json | validator: ").append(v.validation()).append("\n");
        sb.append("{\"validation\":\"").append(v.validation())
            .append("\",\"actual\":\"${cmd()}\",\"expected\":\"value\"}\n");
        sb.append("placement: src/test/resources/validations/<domain>/<name>.json\n");
      } else {
        sb.append("## Generated Validation\n\n**Description:** ").append(description).append("\n\n");
        sb.append("**Mode:** JSON (uses indexed validator `").append(v.validation()).append("`)\n\n");
        sb.append("```json\n").append(generateValidationJson(v.validation(), description)).append("\n```\n\n");
        sb.append("**Placement:** `src/test/resources/validations/<domain>/<name>.json`\n");
      }
    } else {
      String className = toClassName(description) + "Validator";
      if (concise) {
        sb.append("mode: java | class: ").append(className).append(" | package: ").append(pkg).append("\n\n");
        sb.append("```java\n").append(generateValidatorClass(className, description, pkg)).append("```\n");
        sb.append("placement: src/test/java/").append(pkg.replace('.', '/')).append("/").append(className).append(".java\n");
      } else {
        sb.append("## Generated Validation\n\n**Description:** ").append(description).append("\n\n");
        sb.append("**Mode:** Java (custom ValidatorLogic)\n\n");
        sb.append("### ").append(className).append(".java\n\n```java\n");
        sb.append(generateValidatorClass(className, description, pkg));
        sb.append("```\n\n");
        sb.append("**Placement:** `src/test/java/").append(pkg.replace('.', '/')).append("/")
            .append(className).append(".java`\n");
      }
    }
    return sb.toString();
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
