package io.github.ygrip.testara.agent.flavor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scans Testara cucumber module source files and builds a FlavorEntry catalog.
 * All entries come from @Given/@When/@Then annotations — nothing is hardcoded.
 */
public class TestaraFlavorIndexer {

  private static final Logger LOG = Logger.getLogger(TestaraFlavorIndexer.class.getName());

  // Handles escaped quotes inside the annotation string: @Given("...\"...\"..")
  private static final Pattern STEP_ANNOTATION = Pattern.compile(
      "@(Given|When|Then)\\s*\\(\\s*\"((?:[^\"\\\\]|\\\\.)*?)\"");
  private static final Pattern CLASS_NAME = Pattern.compile(
      "(?:public\\s+)?class\\s+(\\w+)");

  /** Indexes all flavor steps reachable from any Maven module under {@code projectRoot}. */
  public List<FlavorEntry> index(Path projectRoot, List<String> modules) {
    List<FlavorEntry> entries = new ArrayList<>();
    Set<Path> scanned = new LinkedHashSet<>();

    // Always try to scan the project root's own source
    scanned.add(projectRoot);

    // Add each declared Maven module dir
    for (String module : modules) {
      Path moduleDir = projectRoot.resolve(module);
      if (Files.isDirectory(moduleDir)) scanned.add(moduleDir);
    }

    for (Path root : scanned) {
      entries.addAll(scanDir(root));
    }
    return List.copyOf(entries);
  }

  private List<FlavorEntry> scanDir(Path root) {
    List<FlavorEntry> entries = new ArrayList<>();
    try {
      Files.walkFileTree(root, new SimpleFileVisitor<>() {
        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
          String name = dir.getFileName() == null ? "" : dir.getFileName().toString();
          // Skip non-step directories to stay fast
          if (name.equals("target") || name.equals("test") || name.equals("resources")) {
            return FileVisitResult.SKIP_SUBTREE;
          }
          return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
          if (!file.toString().endsWith("Steps.java")) return FileVisitResult.CONTINUE;
          try {
            entries.addAll(parseStepFile(file));
          } catch (IOException e) {
            LOG.fine("Cannot parse step file " + file + ": " + e.getMessage());
          }
          return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) {
          return FileVisitResult.CONTINUE;
        }
      });
    } catch (IOException e) {
      LOG.fine("Error scanning for step files under " + root + ": " + e.getMessage());
    }
    return entries;
  }

  /** Matches {typeName} tokens in Cucumber Expressions. */
  private static final Pattern CE_PARAM = Pattern.compile("\\{([^}]+)}");

  /** Detects Cucumber Expression format: contains {type} and no regex anchors. */
  private static boolean isCucumberExpression(String expr) {
    return CE_PARAM.matcher(expr).find() && !expr.contains("(.+)") && !expr.contains("(\\w");
  }

  private List<FlavorEntry> parseStepFile(Path file) throws IOException {
    String source = Files.readString(file, StandardCharsets.UTF_8);
    String className = extractClassName(source);
    String module = detectModule(file);
    String slice = detectSlice(file, className);

    if (slice == null) return List.of();

    List<FlavorEntry> entries = new ArrayList<>();
    Matcher m = STEP_ANNOTATION.matcher(source);
    while (m.find()) {
      String keyword = m.group(1);
      String rawExpr = unescapeAnnotation(m.group(2));
      if (rawExpr.length() < 5) continue;

      String expression = stripAnchors(rawExpr);
      String example;
      List<String> paramTypes;

      if (isCucumberExpression(expression)) {
        // Cucumber Expression: extract {type} names and build example from them
        paramTypes = extractParamTypes(expression);
        example = cucumberExprToExample(expression, slice);
      } else {
        // Legacy regex expression
        paramTypes = List.of();
        example = toExample(expression, slice);
      }
      String capability = toCapability(expression);
      entries.add(new FlavorEntry(slice, keyword, expression, example, capability, module, className, paramTypes));
    }
    return entries;
  }

  /** Extracts the list of {typeName} tokens from a Cucumber Expression in order. */
  private List<String> extractParamTypes(String expression) {
    List<String> types = new ArrayList<>();
    Matcher m = CE_PARAM.matcher(expression);
    while (m.find()) types.add(m.group(1));
    return List.copyOf(types);
  }

  /** Converts a Cucumber Expression to a human-readable example by substituting {type} with concrete values. */
  private String cucumberExprToExample(String expression, String slice) {
    String id = switch (slice) {
      case "api"     -> "[api]";
      case "ui"      -> "user";
      case "sql"     -> "[sql]";
      case "mongo"   -> "[mongo]";
      case "kafka"   -> "[kafka]";
      case "elastic" -> "[elastic-search]";
      default        -> "actor";
    };

    return CE_PARAM.matcher(expression).replaceAll(mr -> {
      return switch (mr.group(1)) {
        case "actor"                    -> id;
        case "string"                   -> "\"value\"";
        case "word"                     -> "value";
        case "int"                      -> "200";
        case "long"                     -> "5000";
        case "bool"                     -> "true";
        case "timeUnit"                 -> "seconds";
        case "devices"                  -> "desktop";
        case "displayedOrNotDisplayed"  -> "displayed";
        case "clickableOrNotClickable"  -> "clickable";
        case "elementState"             -> "visible";
        case "shouldOrShouldNot"        -> "should";
        case "setOrDefine"              -> "set";
        case "setTo"                    -> "to";
        case "greaterOrLess"            -> "greater";
        case "thanOrEqual"              -> "than";
        case "ascendingOrDescending"    -> "ascending";
        case "previousOrNext"           -> "next";
        case "requestOrResponse"        -> "response";
        case "standAloneOrEmbedded"     -> "standalone";
        case "stringValidation"         -> "contains";
        case "httpMethod"               -> "GET";
        case "sql"                      -> "[sql]";
        case "mongo"                    -> "[mongo]";
        case "elasticsearch"            -> "[elastic-search]";
        case "file"                     -> "[file]";
        default                         -> "{" + mr.group(1) + "}";
      };
    });
  }

  // ── Slice detection ──────────────────────────────────────────────────────

  private String detectSlice(Path file, String className) {
    String path = file.toString().replace('\\', '/');
    if (path.contains("testara-api-cucumber") || path.contains("/api/steps")) return "api";
    if (path.contains("testara-ui-cucumber")  || path.contains("/ui/steps"))  return "ui";
    if (path.contains("testara-database-cucumber") || path.contains("/database/steps")) {
      String lower = className.toLowerCase();
      return lower.contains("mongo") ? "mongo" : "sql";
    }
    if (path.contains("testara-streaming-cucumber") || path.contains("/streaming/steps")) return "kafka";
    if (path.contains("testara-elastic-cucumber")   || path.contains("/elastic/steps"))  return "elastic";
    if (path.contains("testara-cucumber")            || path.contains("/cucumber/steps")) return "core";
    return null; // not a recognized flavor step file
  }

  private String detectModule(Path file) {
    String path = file.toString().replace('\\', '/');
    String[] parts = path.split("/");
    for (String part : parts) {
      if (part.startsWith("testara-") && (part.contains("cucumber") || part.contains("api")
          || part.contains("ui") || part.contains("database") || part.contains("streaming")
          || part.contains("elastic"))) {
        return part;
      }
    }
    return "testara-cucumber";
  }

  // ── Example generation ───────────────────────────────────────────────────

  /**
   * Converts a raw Cucumber step regex into a ready-to-paste gherkin example.
   * Groups are replaced with slice-appropriate identifiers and readable placeholders.
   */
  String toExample(String expression, String slice) {
    String id = switch (slice) {
      case "api"     -> "[api]";
      case "ui"      -> "user";
      case "sql"     -> "[sql]";
      case "mongo"   -> "[mongo]";
      case "kafka"   -> "[kafka]";
      case "elastic" -> "[elastic]";
      default        -> "actor";
    };

    // After unescaping, expression uses actual regex: \w+, [^"]*, \d+, etc.
    return expression
        // Slice identifier (first (.+) group)
        .replaceFirst("\\(\\.\\+\\)", id)
        // Literal [sql] / [mongo] groups — already unescaped: (\[sql\]) → [sql]
        .replaceAll("\\(\\\\\\[sql\\\\\\]\\)", "[sql]")
        .replaceAll("\\(\\\\\\[mongo\\\\\\]\\)", "[mongo]")
        // HTTP methods
        .replaceAll("\\(GET\\|POST\\|PUT\\|PATCH\\|DELETE(?:\\|\\w+)*\\)", "POST")
        // Platform alternatives
        .replaceAll("\\(desktop\\|mobile(?:\\|\\w+)*\\)", "desktop")
        // Boolean alternatives
        .replaceAll("\\(true\\|false\\)", "true")
        // Display/condition alternatives
        .replaceAll("\\(displayed\\|not displayed\\)", "displayed")
        .replaceAll("\\(enabled\\|visible(?:\\|[^)]+)*\\)", "visible")
        .replaceAll("\\(contains(?:\\|[^)]+)*\\)", "contains")
        // Quoted string groups "([^"]*)" → "{value}"  (after unescaping, it's this)
        .replaceAll("\"\\(\\[\\^\"\\]\\*\\)\"", "\"{value}\"")
        .replaceAll("\\(\\[\\^\"\\]\\*\\)", "{value}")
        // Word groups (\w+) → {name}  — after unescaping: single backslash
        .replaceAll("\\(\\\\w\\+\\)", "{name}")
        // Number groups (\d+) → {number}
        .replaceAll("\\(\\\\d\\+(?:\\.\\?\\\\d\\*)?\\)", "{number}")
        // Any remaining groups
        .replaceAll("\\([^)]+\\)", "{param}")
        // Strip remaining backslashes (from \[, \] etc.)
        .replaceAll("\\\\", "")
        .trim();
  }

  // ── Capability extraction ────────────────────────────────────────────────

  /**
   * Derives a short human-readable capability label from the step expression text.
   * Takes the most meaningful leading words after stripping the identifier.
   */
  String toCapability(String expression) {
    String clean = expression
        // Remove leading identifier group
        .replaceFirst("^\\(\\.\\+\\)\\s*", "")
        .replaceFirst("^\\(\\\\\\[sql\\\\\\]\\)\\s*", "")
        .replaceFirst("^\\(\\\\\\[mongo\\\\\\]\\)\\s*", "")
        // Remove regex groups
        .replaceAll("\\([^)]+\\)", "")
        // Remove quoted params
        .replaceAll("\"[^\"]*\"", "")
        // Remove backslashes
        .replaceAll("\\\\", "")
        // Normalize whitespace
        .replaceAll("\\s+", " ")
        .trim();

    // Take first 4 meaningful words
    String[] words = clean.split(" ");
    StringBuilder sb = new StringBuilder();
    int count = 0;
    for (String w : words) {
      w = w.trim();
      if (!w.isBlank() && w.length() > 1 && !w.equals("with") && !w.equals("and")
          && !w.equals("the") && !w.equals("to") && !w.equals("from")) {
        if (sb.length() > 0) sb.append(" ");
        sb.append(w);
        if (++count >= 4) break;
      }
    }
    return sb.toString().toLowerCase();
  }

  // ── Utilities ────────────────────────────────────────────────────────────

  /** Converts Java string escapes in annotation values to their actual characters. */
  private String unescapeAnnotation(String s) {
    return s.replace("\\\\", " BSLASH ")  // protect actual backslashes first
            .replace("\\\"", "\"")                    // \" → "
            .replace("\\n", "\n")
            .replace("\\t", "\t")
            .replace(" BSLASH ", "\\");     // restore actual backslashes
  }

  private String stripAnchors(String expr) {
    String s = expr;
    if (s.startsWith("^")) s = s.substring(1);
    if (s.endsWith("$")) s = s.substring(0, s.length() - 1);
    return s;
  }

  private String extractClassName(String source) {
    Matcher m = CLASS_NAME.matcher(source);
    return m.find() ? m.group(1) : "UnknownSteps";
  }
}
