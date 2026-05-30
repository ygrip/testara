package io.github.ygrip.testara.agent.knowledge;

import java.util.Locale;

/** Query criteria for knowledge lookups. */
public record KnowledgeQuery(
    String text,
    String tagExpression,
    int maxResults
) {
  public static KnowledgeQuery fromText(String text) {
    return new KnowledgeQuery(text, null, 200);
  }

  public static KnowledgeQuery fromTag(String tagExpression) {
    return new KnowledgeQuery(null, tagExpression, 200);
  }

  public boolean matchesTag(String tag) {
    if (tagExpression == null || tagExpression.isBlank()) return true;
    return tag.toLowerCase(Locale.ROOT).contains(
        tagExpression.replace("@", "").toLowerCase(Locale.ROOT));
  }

  public boolean matchesText(String input) {
    if (text == null || text.isBlank()) return true;
    return input.toLowerCase(Locale.ROOT).contains(text.toLowerCase(Locale.ROOT));
  }
}
