package io.github.ygrip.testara.agent.skill;

import io.github.ygrip.testara.agent.flavor.FlavorEntry;
import io.github.ygrip.testara.agent.knowledge.FrameworkKnowledgeStore;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders a built-in step from its Cucumber Expression and {@link FlavorEntry#parameterTypes()},
 * filling each parameter with a caller-supplied, type-checked value — never the catalog's generic
 * example placeholders ({@code value}, {@code "value"}, {@code 200}).
 *
 * <p>Identity parameters ({@code {actor}}, {@code {sql}}, {@code {mongo}}, {@code {elasticsearch}},
 * {@code {file}}) are filled automatically; every other parameter consumes the next argument in order.
 */
final class FlavorStepFiller {

  private static final Pattern PARAMETER = Pattern.compile("\\{(\\w*)}");
  private static final Pattern WORD = Pattern.compile("\\S+");
  private static final Pattern NUMBER = Pattern.compile("-?\\d+(?:\\.\\d+)?");
  private static final Map<String, String> IDENTITIES = Map.of(
      "sql", "[sql]",
      "mongo", "[mongo]",
      "elasticsearch", "[elastic-search]",
      "file", "[file]");

  private FlavorStepFiller() {}

  /** Finds the entry declaring exactly {@code expression}, preferring the project catalog. */
  static Optional<FlavorEntry> find(List<FlavorEntry> catalog, String expression) {
    Optional<FlavorEntry> found = catalog.stream()
        .filter(entry -> expression.equals(entry.expression()))
        .findFirst();
    if (found.isPresent()) return found;
    return FrameworkKnowledgeStore.instance().flavorCatalog().stream()
        .filter(entry -> expression.equals(entry.expression()))
        .findFirst();
  }

  /**
   * Fills {@code entry} and returns the step text (without the Gherkin keyword).
   *
   * @throws IllegalArgumentException when the argument count or a value's type does not fit the expression
   */
  static String fill(FlavorEntry entry, String actor, String... args) {
    List<String> types = entry.parameterTypes();
    Matcher matcher = PARAMETER.matcher(entry.expression());
    StringBuilder out = new StringBuilder();
    int typeIndex = 0;
    int argIndex = 0;
    while (matcher.find()) {
      if (typeIndex >= types.size()) {
        throw new IllegalArgumentException("Step has more parameters than declared types: " + entry.expression());
      }
      String type = types.get(typeIndex++);
      String value;
      if ("actor".equals(type)) {
        value = actor;
      } else if (IDENTITIES.containsKey(type)) {
        value = IDENTITIES.get(type);
      } else {
        if (argIndex >= args.length) {
          throw new IllegalArgumentException("Missing value for {" + type + "} in: " + entry.expression());
        }
        value = typed(type, args[argIndex++], entry);
      }
      matcher.appendReplacement(out, Matcher.quoteReplacement(value));
    }
    matcher.appendTail(out);
    if (argIndex != args.length) {
      throw new IllegalArgumentException("Too many values (" + args.length + ") for: " + entry.expression());
    }
    return out.toString();
  }

  private static String typed(String type, String value, FlavorEntry entry) {
    if (value == null) throw new IllegalArgumentException("Null value for {" + type + "} in: " + entry.expression());
    return switch (type) {
      case "string" -> quoted(value);
      case "word" -> {
        requireMatch(WORD, value, type, entry);
        yield value;
      }
      case "int", "long", "double", "float", "bigdecimal", "biginteger", "byte", "short" -> {
        requireMatch(NUMBER, value, type, entry);
        yield value;
      }
      default -> {
        String pattern = FrameworkKnowledgeStore.instance().patternFor(type);
        if (pattern != null) requireMatch(Pattern.compile(pattern), value, type, entry);
        yield value;
      }
    };
  }

  /** Cucumber {@code {string}} accepts double or single quotes; pick the one the value does not contain. */
  private static String quoted(String value) {
    if (!value.contains("\"")) return "\"" + value + "\"";
    if (value.contains("'")) {
      throw new IllegalArgumentException("{string} value contains both quote styles: " + value);
    }
    return "'" + value + "'";
  }

  private static void requireMatch(Pattern pattern, String value, String type, FlavorEntry entry) {
    if (!pattern.matcher(value).matches()) {
      throw new IllegalArgumentException("'" + value + "' is not a valid {" + type + "} for: " + entry.expression());
    }
  }
}
