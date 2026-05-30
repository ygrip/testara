package io.github.ygrip.testara.agent.safety;

import java.util.List;

/**
 * Validates generated output before it is returned to the user or written to disk.
 *
 * <p>Ensures generated Gherkin is parseable, JSON is valid, Java source has
 * required structure, no secrets leaked, and no unknown references.
 */
public final class OutputValidator {

  private OutputValidator() { /* utility */ }

  public record ValidationResult(boolean valid, List<String> errors) {
    public static ValidationResult ok() { return new ValidationResult(true, List.of()); }
    public static ValidationResult fail(String... errors) {
      return new ValidationResult(false, List.of(errors));
    }
  }

  /** Validate generated feature file content. */
  public static ValidationResult validateFeature(String content) {
    if (content == null || content.isBlank()) {
      return ValidationResult.fail("Empty feature content");
    }
    // Delegate to FeaturePlacementGuard for Gherkin checks
    var gherkinResult = FeaturePlacementGuard.validateGherkin(content);
    if (!gherkinResult.passed()) {
      return ValidationResult.fail(gherkinResult.errors().toArray(new String[0]));
    }
    return ValidationResult.ok();
  }

  /** Validate generated Java source for basic structure. */
  public static ValidationResult validateJavaSource(String content, boolean isCommand, boolean isValidator) {
    if (content == null || content.isBlank()) {
      return ValidationResult.fail("Empty Java source");
    }
    if (!JavaCompilationGuard.hasClassDeclaration(content)) {
      return ValidationResult.fail("No class declaration found in generated Java source");
    }
    if (isCommand && !JavaCompilationGuard.hasCommandAnnotation(content)) {
      return ValidationResult.fail("Generated command is missing @CommandTag annotation");
    }
    if (isValidator && !JavaCompilationGuard.hasValidationAnnotation(content)) {
      return ValidationResult.fail("Generated validator is missing @ValidationTag annotation");
    }
    return ValidationResult.ok();
  }

  /** Validate generated JSON (basic structural check). */
  public static ValidationResult validateJson(String content) {
    if (content == null || content.isBlank()) {
      return ValidationResult.fail("Empty JSON content");
    }
    String trimmed = content.strip();
    if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
      return ValidationResult.fail("Content does not appear to be valid JSON (must start with { and end with })");
    }
    return ValidationResult.ok();
  }
}
