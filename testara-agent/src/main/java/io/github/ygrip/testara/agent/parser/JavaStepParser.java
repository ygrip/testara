package io.github.ygrip.testara.agent.parser;

import io.github.ygrip.testara.agent.index.StepDefinitionIndex;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Scans Java step definition files for Cucumber annotations AND method bodies.
 *
 * <p>Goes beyond annotation scanning to extract method signatures, parameter
 * types, and doc comments — useful for the agent to understand what each
 * step definition actually does at code level.
 */
public final class JavaStepParser {

  private static final Logger LOG = Logger.getLogger(JavaStepParser.class.getName());

  private static final Pattern STEP_ANNOTATION = Pattern.compile(
      "@(Given|When|Then|And|But)\\(\"([^\"]+)\"\\)");
  private static final Pattern METHOD_SIG = Pattern.compile(
      "\\b(public|protected|private)?\\s*(static\\s+)?(\\w+(?:<[^>]+>)?)\\s+(\\w+)\\s*\\(([^)]*)\\)");
  private static final Pattern JAVADOC = Pattern.compile(
      "/\\*\\*([^*]|\\*[^/])*\\*/");
  private static final Pattern THROWS_CLAUSE = Pattern.compile(
      "\\)\\s+throws\\s+([\\w,\\s]+)\\s*\\{");
  private static final Pattern METHOD_BODY = Pattern.compile(
      "\\{[^}]*\\}", Pattern.DOTALL);

  /** Parsed step definition with body context. */
  public record ParsedStepDef(
      String annotation,
      String expression,
      String methodName,
      String returnType,
      String parameters,
      List<String> throwsTypes,
      String javadoc,
      Path sourcePath,
      String className
  ) {}

  private JavaStepParser() { /* utility */ }

  /**
   * Parse all step definitions in a Java file, including method signatures
   * and doc comments.
   */
  public static List<ParsedStepDef> parse(Path javaFile) throws IOException {
    String content = Files.readString(javaFile, StandardCharsets.UTF_8);
    String className = extractClassName(content);
    List<ParsedStepDef> defs = new ArrayList<>();

    // Find each step annotation and its associated method
    Matcher annMatcher = STEP_ANNOTATION.matcher(content);
    while (annMatcher.find()) {
      String annType = annMatcher.group(1);
      String expression = annMatcher.group(2);
      int annEnd = annMatcher.end();

      // Find the method that follows this annotation
      String afterAnnotation = content.substring(annEnd);
      Matcher methodMatcher = METHOD_SIG.matcher(afterAnnotation);
      if (methodMatcher.find()) {
        String returnType = methodMatcher.group(3) != null ? methodMatcher.group(3) : "void";
        String methodName = methodMatcher.group(4);
        String parameters = methodMatcher.group(5) != null ? methodMatcher.group(5).strip() : "";

        // Check for throws clause
        String afterSig = afterAnnotation.substring(methodMatcher.end());
        Matcher throwsMatcher = THROWS_CLAUSE.matcher(afterSig);
        List<String> throwsTypes = new ArrayList<>();
        if (throwsMatcher.find()) {
          for (String t : throwsMatcher.group(1).split(",")) {
            throwsTypes.add(t.strip());
          }
        }

        // Extract Javadoc preceding the annotation
        String beforeAnn = content.substring(0, annMatcher.start());
        String javadoc = extractJavadoc(beforeAnn);

        defs.add(new ParsedStepDef(annType, expression, methodName,
            returnType, parameters, List.copyOf(throwsTypes), javadoc,
            javaFile, className));
      }
    }
    return List.copyOf(defs);
  }

  /**
   * Scan a directory of Java files and parse all step definitions.
   */
  public static List<ParsedStepDef> scanDirectory(Path root) {
    List<ParsedStepDef> all = new ArrayList<>();
    try (Stream<Path> walk = Files.walk(root)) {
      walk.filter(p -> p.toString().endsWith(".java")
              && !p.toString().contains("/target/"))
          .forEach(p -> {
            try {
              String content = Files.readString(p);
              if (content.contains("@Given") || content.contains("@When")
                  || content.contains("@Then")) {
                all.addAll(parse(p));
              }
            } catch (IOException e) {
              LOG.fine("Cannot read " + p);
            }
          });
    } catch (IOException e) {
      LOG.warning("Error scanning directory: " + e.getMessage());
    }
    return List.copyOf(all);
  }

  /**
   * Convert parsed step defs to the simpler StepDefinitionIndex record
   * used by the rest of the agent.
   */
  public static List<StepDefinitionIndex> toIndex(List<ParsedStepDef> parsed) {
    return parsed.stream()
        .map(p -> new StepDefinitionIndex(p.annotation(), p.expression(),
            p.sourcePath(), p.methodName(), p.className()))
        .toList();
  }

  // ── Helpers ──────────────────────────────────────────────────────

  private static String extractClassName(String content) {
    Matcher m = Pattern.compile("(?:public\\s+)?(?:class|interface)\\s+(\\w+)")
        .matcher(content);
    return m.find() ? m.group(1) : "Unknown";
  }

  private static String extractJavadoc(String beforeAnnotation) {
    // Find the last /** ... */ before the annotation
    Matcher m = JAVADOC.matcher(beforeAnnotation);
    String last = null;
    while (m.find()) last = m.group();
    if (last == null) return "";
    // Strip the comment markers and leading * on each line
    return last.replaceAll("/\\*\\*|\\*/", "")
        .replaceAll("(?m)^\\s*\\*\\s?", "")
        .strip();
  }
}
