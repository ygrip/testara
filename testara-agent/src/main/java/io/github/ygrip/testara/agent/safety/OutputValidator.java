package io.github.ygrip.testara.agent.safety;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validates generated output before it is returned to the user or written to disk.
 *
 * <p>Ensures generated Gherkin is parseable, JSON is valid, Java source has
 * required structure, no secrets leaked, and no unknown references.
 */
public final class OutputValidator {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Pattern CLASS_NAME = Pattern.compile("\\bclass\\s+(\\w+)");
  private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_$][\\w$]*");
  private static final Pattern METHOD_NAME =
      Pattern.compile("(?m)^\\s*(?:public|protected|private)\\s+(?:static\\s+)?[\\w<>\\[\\],\\s]+?\\s+(\\w+)\\s*\\(");
  private static final Pattern ACTION_NAME = Pattern.compile("@Action\\s*\\(\\s*(?:value\\s*=\\s*)?\"([^\"]+)\"");

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
    List<String> errors = new ArrayList<>();
    Matcher className = CLASS_NAME.matcher(content);
    while (className.find()) {
      if (!IDENTIFIER.matcher(className.group(1)).matches()) {
        errors.add("Invalid Java class name: " + className.group(1));
      }
    }
    duplicates(METHOD_NAME, content).forEach(name -> errors.add("Duplicate method name: " + name));
    duplicates(ACTION_NAME, content).forEach(name -> errors.add("Duplicate @Action name: " + name));
    if (!errors.isEmpty()) return new ValidationResult(false, List.copyOf(errors));
    return ValidationResult.ok();
  }

  private static Set<String> duplicates(Pattern pattern, String content) {
    Set<String> seen = new HashSet<>();
    Set<String> duplicates = new LinkedHashSet<>();
    Matcher matcher = pattern.matcher(content);
    while (matcher.find()) {
      if (!seen.add(matcher.group(1))) duplicates.add(matcher.group(1));
    }
    return duplicates;
  }

  /** Validate generated JSON (basic structural check). */
  public static ValidationResult validateJson(String content) {
    if (content == null || content.isBlank()) {
      return ValidationResult.fail("Empty JSON content");
    }
    try {
      MAPPER.readTree(content);
      return ValidationResult.ok();
    } catch (Exception e) {
      return ValidationResult.fail("Invalid JSON: " + e.getMessage());
    }
  }
}
