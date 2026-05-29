package io.github.ygrip.testara.agent.skill.run;

import io.github.ygrip.testara.agent.index.TagIndex;
import io.github.ygrip.testara.agent.index.TestaraProjectProfile;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Resolves a natural-language test run request into a safe Cucumber tag expression.
 * Priority: explicit tags → known aliases → indexed project tags → domain inference.
 */
public class TagExpressionResolver {

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
  private static final Pattern NOT_CLAUSE   = Pattern.compile("\\b(?:not|except|exclude)\\s+(\\w+)");

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
    String lower = input.toLowerCase(Locale.ROOT);

    // 1. Collect explicit @tags from input
    List<String> positiveTags = new ArrayList<>();
    List<String> negativeTags = new ArrayList<>();

    Matcher explicit = EXPLICIT_TAG.matcher(input);
    while (explicit.find()) positiveTags.add(explicit.group(0));

    // 2. Collect NOT clauses
    Matcher not = NOT_CLAUSE.matcher(lower);
    while (not.find()) {
      String word = not.group(1);
      String resolved = aliases.getOrDefault(word, "@" + word);
      negativeTags.add(resolved);
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

    String positive = positiveTags.size() > 1
        ? "(" + positiveTags.stream().distinct().collect(Collectors.joining(" or ")) + ")"
        : positiveTags.stream().distinct().collect(Collectors.joining(" and "));
    String negative = negativeTags.stream().distinct()
        .map(t -> "not " + t)
        .collect(Collectors.joining(" and "));

    if (positive.isBlank()) return negative;
    if (negative.isBlank()) return positive;
    return positive + " and " + negative;
  }

  /** Count scenarios matching the resolved expression (simple tag set match). */
  public int countMatching(String tagExpression, TestaraProjectProfile profile) {
    if (tagExpression.isBlank()) return profile.totalScenarios();
    return (int) profile.features().stream()
        .flatMap(f -> f.scenarios().stream())
        .filter(s -> {
          Set<String> scenarioTags = new HashSet<>(s.tags());
          return matchesExpression(tagExpression, scenarioTags);
        })
        .count();
  }

  /** Very simple expression evaluator: handles `and`, `not`, single tags. */
  private boolean matchesExpression(String expr, Set<String> tags) {
    String[] parts = expr.split("\\s+and\\s+");
    for (String part : parts) {
      part = part.strip();
      if (part.startsWith("not ")) {
        String negTag = part.substring(4).strip();
        if (tags.contains(negTag)) return false;
      } else if (!tags.contains(part)) {
        return false;
      }
    }
    return true;
  }
}
