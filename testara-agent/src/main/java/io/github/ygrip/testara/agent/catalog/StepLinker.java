package io.github.ygrip.testara.agent.catalog;

import io.github.ygrip.testara.agent.flavor.FlavorEntry;
import io.github.ygrip.testara.agent.index.StepDefinitionIndex;
import io.github.ygrip.testara.agent.knowledge.FrameworkKnowledgeStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Links generated Gherkin step lines to indexed Testara or project glue. */
public final class StepLinker {

  private static final Pattern STEP_LINE = Pattern.compile("^\\s*(Given|When|Then|And|But)\\s+(.+)$");

  public enum Source { BUILT_IN, PROJECT, UNMATCHED }

  public record Link(int lineNumber, String keyword, String text, Source source, String expression,
      String owner) {
    public boolean matched() { return source != Source.UNMATCHED; }
    public String stepLine() { return keyword + " " + text; }
  }

  public static List<Link> linkFeature(String featureContent, List<FlavorEntry> flavorSteps,
      List<StepDefinitionIndex> projectSteps) {
    List<CompiledStep> compiled = new ArrayList<>();
    for (FlavorEntry entry : flavorSteps != null ? flavorSteps : List.<FlavorEntry>of()) {
      compiled.add(CompiledStep.builtIn(entry));
    }
    for (StepDefinitionIndex step : projectSteps != null ? projectSteps : List.<StepDefinitionIndex>of()) {
      compiled.add(CompiledStep.project(step));
    }

    List<Link> links = new ArrayList<>();
    if (featureContent == null || featureContent.isBlank()) return links;
    String[] lines = featureContent.split("\n");
    for (int i = 0; i < lines.length; i++) {
      var matcher = STEP_LINE.matcher(lines[i]);
      if (!matcher.matches()) continue;
      String keyword = matcher.group(1);
      String text = matcher.group(2).strip();
      CompiledStep match = compiled.stream()
          .filter(step -> step.matches(keyword, text))
          .findFirst()
          .orElse(null);
      if (match != null) {
        links.add(new Link(i + 1, keyword, text, match.source, match.expression, match.owner));
      } else {
        links.add(new Link(i + 1, keyword, text, Source.UNMATCHED, "", ""));
      }
    }
    return List.copyOf(links);
  }

  public static boolean matchesStep(String keyword, String text, FlavorEntry entry) {
    return CompiledStep.builtIn(entry).matches(keyword, text);
  }

  private record CompiledStep(String keyword, String expression, Pattern pattern, Source source,
      String owner) {
    static CompiledStep builtIn(FlavorEntry entry) {
      return new CompiledStep(entry.keyword(), entry.expression(), compile(entry.expression()),
          Source.BUILT_IN, entry.module() + ":" + entry.className());
    }

    static CompiledStep project(StepDefinitionIndex step) {
      return new CompiledStep(step.annotation(), step.expression(), compile(step.expression()),
          Source.PROJECT, step.sourcePath() + ":" + step.className());
    }

    boolean matches(String stepKeyword, String stepText) {
      return keywordCompatible(stepKeyword, keyword) && pattern.matcher(stepText).matches();
    }
  }

  private static boolean keywordCompatible(String featureKeyword, String glueKeyword) {
    if ("And".equalsIgnoreCase(featureKeyword) || "But".equalsIgnoreCase(featureKeyword)) return true;
    if ("And".equalsIgnoreCase(glueKeyword) || "But".equalsIgnoreCase(glueKeyword)) return true;
    return featureKeyword.equalsIgnoreCase(glueKeyword);
  }

  private static Pattern compile(String expression) {
    String body = expression == null ? "" : expression.strip();
    if (body.startsWith("^") || body.endsWith("$")) {
      return Pattern.compile(body, Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    }
    if (body.contains("{")) {
      return Pattern.compile("^" + cucumberExpressionToRegex(body) + "$",
          Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    }
    if (looksLikeRegex(body)) {
      return Pattern.compile("^" + body + "$", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    }
    return Pattern.compile("^" + Pattern.quote(body) + "$", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
  }

  private static boolean looksLikeRegex(String expression) {
    return expression.contains("(") || expression.contains("[") || expression.contains("\\d")
        || expression.contains(".+") || expression.contains(".*");
  }

  private static String cucumberExpressionToRegex(String expression) {
    Map<String, String> parameterTypes = FrameworkKnowledgeStore.instance().parameterTypes();
    StringBuilder out = new StringBuilder();
    StringBuilder literal = new StringBuilder();
    for (int i = 0; i < expression.length(); i++) {
      char c = expression.charAt(i);
      if (c == '{') {
        int end = expression.indexOf('}', i);
        if (end > i) {
          if (!literal.isEmpty()) {
            out.append(Pattern.quote(literal.toString()));
            literal.setLength(0);
          }
          String type = expression.substring(i + 1, end);
          out.append(parameterRegex(type, parameterTypes));
          i = end;
          continue;
        }
      }
      literal.append(c);
    }
    if (!literal.isEmpty()) out.append(Pattern.quote(literal.toString()));
    return out.toString();
  }

  private static String parameterRegex(String type, Map<String, String> parameterTypes) {
    String normalized = type.toLowerCase(Locale.ROOT);
    if ("string".equals(normalized)) return "(?:\"[^\"]*\"|'[^']*')";
    if ("int".equals(normalized) || "long".equals(normalized) || "double".equals(normalized)
        || "float".equals(normalized)) return "-?\\d+(?:\\.\\d+)?";
    String registered = parameterTypes.get(type);
    return registered != null ? "(?:" + registered + ")" : ".+";
  }

  private StepLinker() {}
}
