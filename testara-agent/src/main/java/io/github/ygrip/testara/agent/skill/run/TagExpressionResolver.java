package io.github.ygrip.testara.agent.skill.run;

import io.github.ygrip.testara.agent.index.ExamplesIndex;
import io.github.ygrip.testara.agent.index.FeatureIndex;
import io.github.ygrip.testara.agent.index.ScenarioIndex;
import io.github.ygrip.testara.agent.index.TagIndex;
import io.github.ygrip.testara.agent.index.TestaraProjectProfile;
import io.cucumber.tagexpressions.Expression;
import io.cucumber.tagexpressions.TagExpressionParser;

import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Resolves a natural-language test run request into a safe Cucumber tag expression.
 * Priority: explicit tags → known aliases → indexed project tags → domain inference.
 */
public class TagExpressionResolver {

  private static final Logger LOG = Logger.getLogger(TagExpressionResolver.class.getName());

  private static final Map<String, String> DEFAULT_ALIASES = Map.of(
      "smoke",      "@smoke",
      "sanity",     "@smoke",
      "regression", "@regression",
      "api",        "@api",
      "ui",         "@ui",
      "critical",   "@P0",
      "p0",         "@P0",
      "p1",         "@P1",
      "slow",       "@slow",
      "flaky",      "@flaky"
  );

  private static final Pattern EXPLICIT_TAG = Pattern.compile("@(\\w[\\w-]*)");
  private static final Pattern NOT_CLAUSE   = Pattern.compile("\\b(?:not|except|exclude)\\s+@?(\\w[\\w-]*)");

  private final Map<String, String> aliases;

  public TagExpressionResolver() {
    this(Map.of());
  }

  public TagExpressionResolver(Map<String, String> customAliases) {
    Map<String, String> merged = new HashMap<>(DEFAULT_ALIASES);
    merged.putAll(customAliases);
    this.aliases = Collections.unmodifiableMap(merged);
  }

  public String resolve(String input, TestaraProjectProfile profile) {
    if (input == null || input.isBlank()) return "";
    String trimmed = input.strip();
    String lower = trimmed.toLowerCase(Locale.ROOT);

    // Preserve an explicit Cucumber expression exactly; do not rebuild its precedence.
    if (looksLikeExplicitExpression(trimmed)) {
      try {
        TagExpressionParser.parse(trimmed);
        return trimmed;
      } catch (RuntimeException ignored) {
        // Natural-language fallback below will provide a safer resolution.
      }
    }

    // 1. Collect explicit @tags from input
    List<String> positiveTags = new ArrayList<>();
    List<String> negativeTags = new ArrayList<>();

    // 1. Collect NOT clauses first so explicit negative tags are not also treated as positive.
    Matcher not = NOT_CLAUSE.matcher(lower);
    while (not.find()) {
      String word = not.group(1);
      String resolved = aliases.getOrDefault(word, "@" + word);
      negativeTags.add(resolved);
    }

    // 2. Collect explicit @tags from input, excluding tags already identified as negative.
    Matcher explicit = EXPLICIT_TAG.matcher(input);
    while (explicit.find()) {
      String tag = explicit.group(0);
      if (!negativeTags.contains(tag)) positiveTags.add(tag);
    }

    // 3. Map natural language words to aliases / indexed tags
    if (positiveTags.isEmpty()) {
      Set<String> indexed = profile.tags().stream()
          .map(TagIndex::tag).collect(Collectors.toSet());

      for (String word : lower.split("[\\s,;]+")) {
        word = word.replaceAll("[^a-z0-9_-]", "");
        if (word.isBlank()) continue;
        if (aliases.containsKey(word)) {
          String alias = aliases.get(word);
          if (!negativeTags.contains(alias)) positiveTags.add(alias);
        } else if (indexed.contains("@" + word)) {
          if (!negativeTags.contains("@" + word)) positiveTags.add("@" + word);
        }
      }
      if (positiveTags.isEmpty()) {
        String specific = inferSpecificExpressionFromFeatureAndScenarioText(lower, profile, negativeTags);
        if (!specific.isBlank()) return appendNegative(specific, negativeTags);
        positiveTags.addAll(inferFromFeatureAndScenarioText(lower, profile, negativeTags));
      }
    }

    // 4. Handle OR groups: "payment or order tests" → "(payment or order)"
    if (positiveTags.isEmpty() && !lower.contains(" or ")) return negativeTags.isEmpty() ? "" :
        negativeTags.stream().distinct().map(t -> "not " + t).collect(Collectors.joining(" and "));

    if (positiveTags.isEmpty() && lower.contains(" or ")) {
      String[] orParts = lower.split("\\s+or\\s+");
      List<String> orTags = new ArrayList<>();
      Set<String> indexed = profile.tags().stream().map(TagIndex::tag).collect(Collectors.toSet());
      for (String part : orParts) {
        for (String word : part.split("[\\s,;]+")) {
          word = word.replaceAll("[^a-z0-9_-]", "");
          if (word.isBlank()) continue;
          if (aliases.containsKey(word)) { orTags.add(aliases.get(word)); break; }
          else if (indexed.contains("@" + word)) { orTags.add("@" + word); break; }
        }
      }
      if (!orTags.isEmpty()) positiveTags.addAll(orTags);
    }

    if (positiveTags.isEmpty() && negativeTags.isEmpty()) return "";

    String positive = formatPositiveTags(positiveTags, lower);
    String negative = negativeTags.stream().distinct()
        .map(t -> "not " + t)
        .collect(Collectors.joining(" and "));

    if (positive.isBlank()) return negative;
    if (negative.isBlank()) return positive;
    return positive + " and " + negative;
  }

  private String formatPositiveTags(List<String> positiveTags, String lower) {
    List<String> distinct = positiveTags.stream().distinct().toList();
    if (distinct.size() <= 1) return String.join("", distinct);
    if (lower.contains(" or ")) return "(" + String.join(" or ", distinct) + ")";
    return String.join(" and ", distinct);
  }

  private String appendNegative(String positive, List<String> negativeTags) {
    String negative = negativeTags.stream().distinct()
        .map(t -> "not " + t)
        .collect(Collectors.joining(" and "));
    return negative.isBlank() ? positive : positive + " and " + negative;
  }

  private String inferSpecificExpressionFromFeatureAndScenarioText(String lower, TestaraProjectProfile profile,
      List<String> negativeTags) {
    Set<String> words = meaningfulWords(lower);
    if (words.isEmpty()) return "";

    Match best = null;
    boolean tie = false;
    for (var feature : profile.features()) {
      for (var scenario : feature.scenarios()) {
        Set<String> scenarioTags = new LinkedHashSet<>(feature.tags());
        scenarioTags.addAll(scenario.tags());
        String haystack = (feature.featureName() + " " + scenario.name() + " " + String.join(" ", scenarioTags))
            .toLowerCase(Locale.ROOT).replace("@", " ");
        int score = score(haystack, words);
        if (score == 0) continue;
        if (best == null || score > best.score()) {
          best = new Match(score, scenarioTags);
          tie = false;
        } else if (score == best.score()) {
          tie = true;
        }
      }
    }
    if (best == null || tie) return "";
    List<String> significant = best.tags().stream()
        .filter(t -> !negativeTags.contains(t))
        .filter(t -> !t.matches("@P\\d+") && !Set.of("@positive", "@negative", "@manual").contains(t))
        .distinct()
        .toList();
    if (significant.isEmpty()) {
      significant = best.tags().stream().filter(t -> !negativeTags.contains(t)).distinct().toList();
    }
    return String.join(" and ", significant);
  }

  private int score(String haystack, Set<String> words) {
    int score = 0;
    for (String word : words) {
      if (haystack.contains(word)) score++;
    }
    return score;
  }

  private List<String> inferFromFeatureAndScenarioText(String lower, TestaraProjectProfile profile,
      List<String> negativeTags) {
    Set<String> words = meaningfulWords(lower);
    if (words.isEmpty()) return List.of();

    Set<String> inferred = new LinkedHashSet<>();
    for (var feature : profile.features()) {
      Set<String> featureTags = new LinkedHashSet<>(feature.tags());
      String featureText = (feature.featureName() + " " + String.join(" ", featureTags))
          .toLowerCase(Locale.ROOT).replace("@", "");
      if (containsAny(featureText, words)) addPreferredTags(inferred, featureTags, negativeTags);
      for (var scenario : feature.scenarios()) {
        Set<String> scenarioTags = new LinkedHashSet<>(featureTags);
        scenarioTags.addAll(scenario.tags());
        String scenarioText = (scenario.name() + " " + String.join(" ", scenarioTags))
            .toLowerCase(Locale.ROOT).replace("@", "");
        if (containsAny(scenarioText, words)) addPreferredTags(inferred, scenarioTags, negativeTags);
      }
    }
    return List.copyOf(inferred);
  }

  private boolean containsAny(String text, Set<String> words) {
    return words.stream().anyMatch(text::contains);
  }

  private void addPreferredTags(Set<String> target, Set<String> source, List<String> negativeTags) {
    source.stream()
        .filter(t -> !negativeTags.contains(t))
        .filter(t -> !t.matches("@P\\d+") && !Set.of("@positive", "@negative", "@manual").contains(t))
        .forEach(target::add);
    if (target.isEmpty()) source.stream().filter(t -> !negativeTags.contains(t)).forEach(target::add);
  }

  private static final Set<String> STOP_WORDS = Set.of(
      "run", "test", "tests", "dry", "mode", "the", "a", "an", "in", "on", "for", "with",
      "and", "or", "please", "execute", "only", "all", "scenario", "scenarios"
  );

  private Set<String> meaningfulWords(String lower) {
    return Arrays.stream(lower.split("[\\s,;]+"))
        .map(w -> w.replaceAll("[^a-z0-9_-]", ""))
        .filter(w -> !w.isBlank())
        .filter(w -> !STOP_WORDS.contains(w))
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  private record Match(int score, Set<String> tags) {}

  /**
   * For a tag expression that matched zero scenarios, returns up to {@code limit} alternative
   * tag suggestions with their scenario counts, ordered by similarity to the requested tags.
   */
  public List<String> suggestAlternatives(String tagExpression, TestaraProjectProfile profile, int limit) {
    Set<String> requested = extractPositiveTags(tagExpression);
    if (requested.isEmpty()) {
      return profile.tags().stream()
          .sorted(Comparator.comparingInt(t -> -countMatching(t.tag(), profile)))
          .limit(limit)
          .map(t -> t.tag() + " (" + countMatching(t.tag(), profile) + " scenarios)")
          .toList();
    }
    return profile.tags().stream()
        .filter(t -> {
          String indexed = t.tag().toLowerCase(Locale.ROOT).replace("@", "");
          return requested.stream().anyMatch(req -> {
            String r = req.toLowerCase(Locale.ROOT).replace("@", "");
            return indexed.contains(r) || r.contains(indexed);
          });
        })
        .sorted(Comparator.comparingInt(t -> -countMatching(t.tag(), profile)))
        .limit(limit)
        .map(t -> t.tag() + " (" + countMatching(t.tag(), profile) + " scenarios)")
        .toList();
  }

  private Set<String> extractPositiveTags(String tagExpression) {
    Set<String> tags = new LinkedHashSet<>();
    Matcher m = EXPLICIT_TAG.matcher(tagExpression);
    while (m.find()) tags.add(m.group(0));
    return tags;
  }

  /** Count scenarios (or Scenario Outlines with at least one matching Examples block) matching the expression. */
  public int countMatching(String tagExpression, TestaraProjectProfile profile) {
    if (tagExpression.isBlank()) return profile.totalScenarios();
    Expression expression = parseOrNull(tagExpression);
    if (expression == null) return 0;
    return (int) profile.features().stream()
        .flatMap(f -> f.scenarios().stream().filter(s -> matches(expression, f, s)))
        .count();
  }

  /**
   * Whether Cucumber would run {@code scenario} for {@code tagExpression}: pickles inherit feature
   * and scenario tags, and a Scenario Outline's pickles additionally carry the tags of the Examples
   * block they come from, so an outline matches when any of its Examples blocks does.
   */
  public boolean matches(String tagExpression, FeatureIndex feature, ScenarioIndex scenario) {
    Expression expression = parseOrNull(tagExpression);
    return expression != null && matches(expression, feature, scenario);
  }

  private boolean matches(Expression expression, FeatureIndex feature, ScenarioIndex scenario) {
    List<String> baseTags = new ArrayList<>(feature.tags());
    baseTags.addAll(scenario.tags());
    if (scenario.examples() == null || scenario.examples().isEmpty()) {
      return expression.evaluate(baseTags);
    }
    for (ExamplesIndex examples : scenario.examples()) {
      List<String> pickleTags = new ArrayList<>(baseTags);
      if (examples.tags() != null) pickleTags.addAll(examples.tags());
      if (expression.evaluate(pickleTags)) return true;
    }
    return false;
  }

  private Expression parseOrNull(String tagExpression) {
    try {
      return TagExpressionParser.parse(tagExpression);
    } catch (RuntimeException e) {
      LOG.fine("Not a valid Cucumber tag expression: " + tagExpression + " (" + e.getMessage() + ")");
      return null;
    }
  }

  private boolean looksLikeExplicitExpression(String input) {
    String lower = input.toLowerCase(Locale.ROOT);
    return input.startsWith("@")
        || input.startsWith("(")
        || lower.startsWith("not @")
        || lower.startsWith("not (");
  }
}
