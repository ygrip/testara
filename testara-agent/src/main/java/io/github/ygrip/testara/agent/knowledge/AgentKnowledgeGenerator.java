package io.github.ygrip.testara.agent.knowledge;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.ygrip.testara.agent.flavor.FlavorEntry;
import io.github.ygrip.testara.agent.flavor.TestaraFlavorIndexer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Build-time generator: scans framework source files and writes JSON knowledge
 * catalogs into the agent JAR resources. Run via exec-maven-plugin at
 * process-classes phase with args: [repoRoot] [outputDir].
 *
 * Generates:
 *   agent-context/flavor-catalog.json   — FlavorEntry list from all cucumber modules
 *   agent-context/ui-interactions.json  — usage strings for interaction/observation classes
 */
public class AgentKnowledgeGenerator {

  private static final Logger LOG = Logger.getLogger(AgentKnowledgeGenerator.class.getName());

  // Cucumber modules to index for flavor steps
  private static final List<String> CUCUMBER_MODULES = List.of(
      "testara-cucumber", "testara-api-cucumber", "testara-ui-cucumber",
      "testara-database-cucumber", "testara-streaming-cucumber", "testara-elastic-cucumber"
  );

  // UI source packages to scan for interaction/observation classes
  private static final List<String> UI_SOURCE_PATHS = List.of(
      "testara-ui/src/main/java/io/github/ygrip/testara/ui/interaction",
      "testara-ui/src/main/java/io/github/ygrip/testara/ui/observation",
      "testara-ui/src/main/java/io/github/ygrip/testara/ui/navigation"
  );

  public static void main(String[] args) throws Exception {
    if (args.length < 2) {
      System.err.println("Usage: AgentKnowledgeGenerator <repoRoot> <outputDir>");
      System.exit(1);
    }
    Path repoRoot  = Paths.get(args[0]).toAbsolutePath().normalize();
    Path outputDir = Paths.get(args[1]).toAbsolutePath().normalize();
    Files.createDirectories(outputDir);

    ObjectMapper mapper = new ObjectMapper();

    // 1. Flavor catalog — all built-in step entries from cucumber modules
    LOG.info("Generating flavor catalog from " + repoRoot);
    List<FlavorEntry> flavorCatalog = new TestaraFlavorIndexer().index(repoRoot, CUCUMBER_MODULES);
    mapper.writerWithDefaultPrettyPrinter()
        .writeValue(outputDir.resolve("flavor-catalog.json").toFile(), flavorCatalog);
    LOG.info("Flavor catalog: " + flavorCatalog.size() + " entries");

    // 2. Parameter type registry — from BaseDefinitions.java
    LOG.info("Generating parameter type registry from BaseDefinitions");
    Map<String, String> typeRegistry = extractParameterTypes(repoRoot);
    mapper.writerWithDefaultPrettyPrinter()
        .writeValue(outputDir.resolve("parameter-types.json").toFile(), typeRegistry);
    LOG.info("Parameter types: " + typeRegistry.size() + " entries");

    // 3. UI interaction/observation examples
    LOG.info("Generating UI interaction examples");
    List<String> interactions = new ArrayList<>();
    for (String relPath : UI_SOURCE_PATHS) {
      Path sourceDir = repoRoot.resolve(relPath);
      if (Files.isDirectory(sourceDir)) {
        interactions.addAll(extractInteractionExamples(sourceDir));
      } else {
        LOG.fine("UI source path not found (skipping): " + sourceDir);
      }
    }
    // Deduplicate while preserving order
    List<String> deduped = interactions.stream().distinct().collect(Collectors.toList());
    mapper.writerWithDefaultPrettyPrinter()
        .writeValue(outputDir.resolve("ui-interactions.json").toFile(), deduped);
    LOG.info("UI interactions: " + deduped.size() + " examples");
  }

  // ── Parameter type registry ───────────────────────────────────────────────

  private static final Pattern PARAM_TYPE_ANN = Pattern.compile(
      "@ParameterType\\(\"((?:[^\"]|\\\\.)*)\"\\)\\s*(?:public\\s+\\w[\\w<>]*\\s+(\\w+))");

  /**
   * Scans BaseDefinitions.java and any other @ParameterType definitions across
   * cucumber modules to build a typeName → regex pattern registry.
   */
  static Map<String, String> extractParameterTypes(Path repoRoot) throws IOException {
    Map<String, String> registry = new LinkedHashMap<>();
    // Primary source: testara-cucumber BaseDefinitions
    List<Path> sources = new ArrayList<>();
    for (String module : List.of("testara-cucumber", "testara-api-cucumber",
        "testara-ui-cucumber", "testara-database-cucumber")) {
      Path modDir = repoRoot.resolve(module + "/src/main/java");
      if (Files.isDirectory(modDir)) {
        try (Stream<Path> walk = Files.walk(modDir)) {
          walk.filter(p -> p.toString().endsWith(".java"))
              .forEach(sources::add);
        }
      }
    }
    for (Path src : sources) {
      try {
        String source = Files.readString(src, StandardCharsets.UTF_8);
        Matcher m = PARAM_TYPE_ANN.matcher(source);
        while (m.find()) {
          String pattern = m.group(1);
          String methodName = m.group(2);
          if (methodName != null && !registry.containsKey(methodName)) {
            registry.put(methodName, pattern);
          }
        }
      } catch (IOException ignored) {}
    }
    return registry;
  }

  // ── Interaction/observation scanner ──────────────────────────────────────

  /**
   * Scans .java source files and extracts public static factory method usages.
   * For chained builders (Enter.text().into()), follows inner classes.
   */
  static List<String> extractInteractionExamples(Path sourceDir) throws IOException {
    List<String> examples = new ArrayList<>();
    try (Stream<Path> files = Files.walk(sourceDir)) {
      files.filter(p -> p.toString().endsWith(".java"))
           .forEach(p -> {
             try { examples.addAll(extractFromFile(p)); }
             catch (IOException e) { LOG.fine("Skip " + p + ": " + e.getMessage()); }
           });
    }
    return examples;
  }

  private static final Pattern PUBLIC_STATIC_METHOD = Pattern.compile(
      "public\\s+static\\s+(?:\\w+(?:<[^>]*>)?\\s+)?(\\w+)\\s*\\(([^)]*)\\)");
  private static final Pattern PUBLIC_INSTANCE_METHOD = Pattern.compile(
      "public\\s+(?!static)(?:\\w+(?:<[^>]*>)?\\s+)?(\\w+)\\s*\\(([^)]*)\\)");
  private static final Pattern INNER_CLASS = Pattern.compile(
      "public\\s+(?:static\\s+)?final\\s+class\\s+(\\w+)");

  private static List<String> extractFromFile(Path file) throws IOException {
    String source = Files.readString(file, StandardCharsets.UTF_8);
    String className = file.getFileName().toString().replace(".java", "");

    // Skip non-interaction classes (interfaces, abstract, context classes)
    if (source.contains("interface ") && !source.contains("class ")) return List.of();
    if (source.contains("abstract class")) return List.of();
    if (className.endsWith("Context") || className.endsWith("Interaction")
        || className.equals("SessionInteractionContext")) return List.of();

    List<String> examples = new ArrayList<>();

    // Extract public static factory methods → entry points
    Matcher staticM = PUBLIC_STATIC_METHOD.matcher(source);
    while (staticM.find()) {
      String methodName = staticM.group(1);
      String params = staticM.group(2).trim();
      if (isConstructorOrBoilerplate(methodName)) continue;

      String paramExample = buildParamExample(params, methodName);
      String entry = className + "." + methodName + "(" + paramExample + ")";

      // Look for fluent chains via inner classes
      String chainedExample = findChainedUsage(source, className, methodName);
      examples.add(chainedExample != null ? entry + "." + chainedExample : entry);
    }

    // If no static methods found, try instance methods (for classes like SeeThat)
    if (examples.isEmpty()) {
      Matcher instanceM = PUBLIC_INSTANCE_METHOD.matcher(source);
      while (instanceM.find()) {
        String methodName = instanceM.group(1);
        String params = instanceM.group(2).trim();
        if (isConstructorOrBoilerplate(methodName)) continue;
        String paramExample = buildParamExample(params, methodName);
        examples.add(className + "." + methodName + "(" + paramExample + ")");
        if (examples.size() >= 3) break;
      }
    }

    return examples;
  }

  private static String findChainedUsage(String source, String className, String entryMethod) {
    // Look for inner classes whose methods return the outer class (builder pattern)
    Matcher innerM = INNER_CLASS.matcher(source);
    while (innerM.find()) {
      String innerName = innerM.group(1);
      // Find the inner class body (simple approach: look for the class in source)
      int start = innerM.start();
      String innerSource = source.substring(start, Math.min(start + 2000, source.length()));
      Matcher chainM = PUBLIC_INSTANCE_METHOD.matcher(innerSource);
      while (chainM.find()) {
        String chainMethod = chainM.group(1);
        String chainParams = chainM.group(2).trim();
        if (isConstructorOrBoilerplate(chainMethod) || chainMethod.equals(innerName)) continue;
        String chainExample = buildParamExample(chainParams, chainMethod);
        return chainMethod + "(" + chainExample + ")";
      }
    }
    return null;
  }

  private static String buildParamExample(String params, String methodName) {
    if (params.isBlank()) return "";
    String lower = methodName.toLowerCase();
    String[] parts = params.split(",");
    List<String> examples = new ArrayList<>();
    for (String part : parts) {
      String t = part.trim();
      if (t.contains("String")) {
        if (lower.contains("text") || lower.contains("value") || lower.contains("input")) {
          examples.add("\"text\"");
        } else if (lower.contains("page") || lower.contains("name")) {
          examples.add("\"page name\"");
        } else {
          examples.add("\"element name\"");
        }
      } else if (t.contains("Locator") || t.contains("Element")) {
        examples.add("\"element name\"");
      } else if (t.contains("int") || t.contains("long")) {
        examples.add("5000");
      } else if (t.contains("CharSequence") || t.contains("Key")) {
        examples.add("Keys.ENTER");
      } else {
        examples.add("...");
      }
    }
    return String.join(", ", examples);
  }

  private static boolean isConstructorOrBoilerplate(String name) {
    return name.equals("perform") || name.equals("root") || name.equals("build")
        || name.equals("toString") || name.equals("equals") || name.equals("hashCode")
        || name.equals("of") || name.startsWith("get") || name.startsWith("set")
        || name.equals("main") || name.equals("copy") || name.equals("clone")
        || Character.isUpperCase(name.charAt(0)); // Uppercase = constructor or type name
  }
}
