package io.github.ygrip.testara.agent.knowledge;

import io.cucumber.tagexpressions.TagExpressionParser;

import java.util.Collection;
import java.util.List;
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
    return matchesTags(List.of(tag));
  }

  public boolean matchesTags(Collection<String> tags) {
    if (tagExpression == null || tagExpression.isBlank()) return true;
    try {
      return TagExpressionParser.parse(tagExpression).evaluate(List.copyOf(tags));
    } catch (RuntimeException e) {
      return false;
    }
  }

  public boolean matchesText(String input) {
    if (text == null || text.isBlank()) return true;
    return input != null && input.toLowerCase(Locale.ROOT).contains(text.toLowerCase(Locale.ROOT));
  }
}
