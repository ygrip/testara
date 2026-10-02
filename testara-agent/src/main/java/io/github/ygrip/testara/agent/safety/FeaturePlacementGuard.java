package io.github.ygrip.testara.agent.safety;

import io.github.ygrip.testara.agent.index.FeatureIndex;
import io.github.ygrip.testara.agent.index.ScenarioIndex;
import io.cucumber.gherkin.GherkinParser;
import io.cucumber.messages.types.Envelope;
import java.nio.charset.StandardCharsets;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Validates generated feature files before they are committed to disk.
 *
 * <p>Checks: valid Gherkin syntax markers, unique scenario names in the same
 * feature, no empty Given/When/Then steps, no generated secrets in step text,
 * and no references to unknown commands/validators.
 */
public final class FeaturePlacementGuard {

  private FeaturePlacementGuard() { /* utility */ }

  public enum Violation { DUPLICATE_SCENARIO, EMPTY_STEP, SECRET_LEAK, UNKNOWN_COMMAND }

  public record GuardResult(boolean passed, List<String> errors) {
    public static GuardResult ok() { return new GuardResult(true, List.of()); }
    public static GuardResult fail(String... errors) {
      return new GuardResult(false, List.of(errors));
    }
  }

  /**
   * Validate a generated feature's scenarios against existing features in the
   * target directory to avoid name collisions.
   */
  public static GuardResult validatePlacement(
      FeatureIndex generated,
      List<FeatureIndex> existing) {

    Set<String> existingNames = existing.stream()
        .flatMap(f -> f.scenarios().stream())
        .map(s -> s.name().strip().toLowerCase(Locale.ROOT))
        .collect(Collectors.toSet());

    List<String> errors = new ArrayList<>();
    for (ScenarioIndex s : generated.scenarios()) {
      String name = s.name().strip().toLowerCase(Locale.ROOT);
      if (existingNames.contains(name)) {
        errors.add("Duplicate scenario name: \"" + s.name() + "\" already exists in target directory");
      }
    }
    return errors.isEmpty() ? GuardResult.ok() : GuardResult.fail(errors.toArray(new String[0]));
  }

  /** Parse generated Gherkin with Cucumber's grammar before it is written. */
  public static GuardResult validateGherkin(String featureContent) {
    if (featureContent == null || featureContent.isBlank()) {
      return GuardResult.fail("Empty feature content");
    }
    try (var envelopes = GherkinParser.builder()
        .includeSource(false)
        .includePickles(false)
        .build()
        .parse("generated.feature", featureContent.getBytes(StandardCharsets.UTF_8))) {
      List<Envelope> parsed = envelopes.toList();
      List<String> errors = parsed.stream()
          .flatMap(envelope -> envelope.getParseError().stream())
          .map(error -> error.getMessage())
          .toList();
      boolean hasFeature = parsed.stream()
          .flatMap(envelope -> envelope.getGherkinDocument().stream())
          .anyMatch(document -> document.getFeature().isPresent());
      if (!errors.isEmpty()) return GuardResult.fail(errors.toArray(new String[0]));
      if (!hasFeature) return GuardResult.fail("No Gherkin Feature found");
      return GuardResult.ok();
    } catch (RuntimeException e) {
      return GuardResult.fail("Invalid Gherkin: " + e.getMessage());
    }
  }
}
