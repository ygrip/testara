package io.github.ygrip.testara.agent.knowledge;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.ygrip.testara.agent.flavor.FlavorEntry;

import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Loads the agent knowledge catalog bundled in the JAR at build time by
 * {@link AgentKnowledgeGenerator}. Provides flavor steps, UI interaction
 * examples, and compiled step-pattern regexes to skills and review tools.
 *
 * Resources bundled at build time:
 *   agent-context/flavor-catalog.json   — all built-in step FlavorEntry list
 *   agent-context/ui-interactions.json  — interaction/observation class usages
 */
public final class FrameworkKnowledgeStore {

  private static final Logger LOG = Logger.getLogger(FrameworkKnowledgeStore.class.getName());
  private static final FrameworkKnowledgeStore INSTANCE = new FrameworkKnowledgeStore();

  private final List<FlavorEntry> flavorCatalog;
  private final List<String> uiInteractionExamples;
  private final List<Pattern> uiStepPatterns;

  private FrameworkKnowledgeStore() {
    this.flavorCatalog = loadFlavorCatalog();
    this.uiInteractionExamples = loadUiInteractions();
    this.uiStepPatterns = buildUiStepPatterns(flavorCatalog);
  }

  public static FrameworkKnowledgeStore instance() { return INSTANCE; }

  /** All built-in Testara steps from all slices. */
  public List<FlavorEntry> flavorCatalog() { return flavorCatalog; }

  /** Built-in steps filtered by slice (ui, api, sql, etc.). */
  public List<FlavorEntry> flavorCatalogForSlice(String slice) {
    return flavorCatalog.stream()
        .filter(e -> slice.equalsIgnoreCase(e.slice()))
        .collect(Collectors.toList());
  }

  /** Compiled regex patterns for each built-in UI step — used by TestReviewSkill. */
  public List<Pattern> uiStepPatterns() { return uiStepPatterns; }

  /** Formatted usage examples for interaction/observation classes — used by TestaraUiSkill. */
  public List<String> uiInteractionExamples() { return uiInteractionExamples; }

  /** True if the flavor catalog was successfully loaded (non-empty). */
  public boolean isLoaded() { return !flavorCatalog.isEmpty(); }

  // ── Loaders ───────────────────────────────────────────────────────────────

  private List<FlavorEntry> loadFlavorCatalog() {
    return loadJson("agent-context/flavor-catalog.json",
        new TypeReference<List<FlavorEntry>>() {});
  }

  private List<String> loadUiInteractions() {
    return loadJson("agent-context/ui-interactions.json",
        new TypeReference<List<String>>() {});
  }

  private <T> List<T> loadJson(String resource, TypeReference<List<T>> type) {
    try (InputStream is = FrameworkKnowledgeStore.class.getClassLoader()
        .getResourceAsStream(resource)) {
      if (is == null) {
        LOG.fine("Framework knowledge not found: " + resource + " (run a full build to generate)");
        return Collections.emptyList();
      }
      return new ObjectMapper().readValue(is, type);
    } catch (Exception e) {
      LOG.warning("Cannot load framework knowledge " + resource + ": " + e.getMessage());
      return Collections.emptyList();
    }
  }

  private static List<Pattern> buildUiStepPatterns(List<FlavorEntry> catalog) {
    List<Pattern> patterns = catalog.stream()
        .filter(e -> "ui".equals(e.slice()) || "core".equals(e.slice()))
        .map(e -> {
          try {
            // Convert step regex to a loose contains-match pattern
            String expr = e.expression()
                .replace("(.+)", ".+").replace("([^\"]*)", "[^\"]*")
                .replace("(\\w+)", "\\w+").replace("(\\d+)", "\\d+")
                .replaceAll("\\(\\|?[^)]+\\)", "[^\\s]+");
            return Pattern.compile("(?i).*" + expr + ".*", Pattern.DOTALL);
          } catch (Exception ex) {
            return null;
          }
        })
        .filter(p -> p != null)
        .collect(Collectors.toList());

    // Always include API/DB/Kafka patterns so review scores work across slices
    List<String> fallbacks = List.of(
        "(?i).*\\[api\\].*", "(?i).*\\[sql\\].*", "(?i).*\\[mongo\\].*",
        "(?i).*\\[elastic-search\\].*", "(?i).*user start kafka.*",
        "(?i).*user send kafka.*", "(?i).*user stop kafka.*",
        "(?i).*user using \\w+ in (desktop|mobile|android|ios).*",
        "(?i).*user open \".+\" page.*", "(?i).*user is in \".+\" page.*",
        "(?i).*user do \".+\".*", "(?i).*user click the \".+\".*",
        "(?i).*user (type|enter) value.*", "(?i).*user should see \".+\" is.*",
        "(?i).*user element \".+\" should.*", "(?i).*user see that.*"
    );
    for (String f : fallbacks) {
      try { patterns.add(Pattern.compile(f, Pattern.DOTALL)); }
      catch (Exception ignored) {}
    }
    return Collections.unmodifiableList(patterns);
  }
}
