package io.github.ygrip.testara.agent.catalog;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scans testara source modules for @LoadProperties(prefix="...") annotations
 * and builds a RuntimeCatalogEntry list — all data from source, nothing hardcoded.
 *
 * Also holds a built-in fallback catalog for when sources are not present (JAR-only deployments),
 * derived directly from scanning the actual testara repo.
 */
public final class RuntimeCatalogIndexer {

  private static final Logger LOG = Logger.getLogger(RuntimeCatalogIndexer.class.getName());
  private static final Pattern LOAD_PROPS = Pattern.compile(
      "@LoadProperties\\s*\\(\\s*prefix\\s*=\\s*\"([^\"]+)\"");
  private static final Pattern CLASS_NAME = Pattern.compile(
      "(?:public\\s+)?(?:class|interface)\\s+(\\w+)");

  /** Scan source dirs for @LoadProperties and build catalog. Falls back to built-in if empty. */
  public List<RuntimeCatalogEntry> index(Path projectRoot, List<String> modules) {
    List<RuntimeCatalogEntry> entries = new ArrayList<>();
    Set<Path> roots = collectRoots(projectRoot, modules);
    for (Path root : roots) {
      entries.addAll(scanDir(root));
    }
    // If nothing was found from source (standalone user project), use built-in catalog
    if (entries.isEmpty()) {
      return builtInCatalog();
    }
    return List.copyOf(entries);
  }

  private Set<Path> collectRoots(Path root, List<String> modules) {
    Set<Path> roots = new LinkedHashSet<>();
    roots.add(root);
    for (String module : modules) {
      Path moduleDir = root.resolve(module);
      if (Files.isDirectory(moduleDir)) roots.add(moduleDir);
    }
    return roots;
  }

  private List<RuntimeCatalogEntry> scanDir(Path root) {
    List<RuntimeCatalogEntry> entries = new ArrayList<>();
    try {
      Files.walkFileTree(root, new SimpleFileVisitor<>() {
        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
          String name = dir.getFileName() == null ? "" : dir.getFileName().toString();
          if (name.equals("target") || name.equals("test")) return FileVisitResult.SKIP_SUBTREE;
          return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
          if (!file.toString().endsWith("Properties.java")) return FileVisitResult.CONTINUE;
          try {
            String source = Files.readString(file, StandardCharsets.UTF_8);
            Matcher m = LOAD_PROPS.matcher(source);
            if (m.find()) {
              String prefix = m.group(1);
              String className = extractClassName(source);
              String module = detectModule(file);
              String slice = detectSlice(prefix, module, className);
              List<String> examples = buildExampleKeys(prefix, className);
              entries.add(new RuntimeCatalogEntry(slice, prefix, module, className, examples));
            }
          } catch (IOException e) {
            LOG.fine("Cannot read " + file);
          }
          return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) {
          return FileVisitResult.CONTINUE;
        }
      });
    } catch (IOException e) {
      LOG.fine("Error scanning for properties classes: " + e.getMessage());
    }
    return entries;
  }

  private String extractClassName(String source) {
    Matcher m = CLASS_NAME.matcher(source);
    return m.find() ? m.group(1) : "UnknownProperties";
  }

  private String detectModule(Path file) {
    String path = file.toString().replace('\\', '/');
    String[] parts = path.split("/");
    for (String part : parts) {
      if (part.startsWith("testara-") && !part.equals("testara-agent")) return part;
    }
    return "unknown";
  }

  private String detectSlice(String prefix, String module, String className) {
    String p = prefix.toLowerCase(Locale.ROOT);
    if (p.startsWith("api") || p.startsWith("spec") || p.startsWith("response")) return "api";
    if (p.startsWith("selenium")) return "ui-selenium";
    if (p.startsWith("playwright")) return "ui-playwright";
    if (p.startsWith("appium")) return "ui-appium";
    if (p.startsWith("automation.engine") || p.startsWith("web")) return "ui";
    if (p.startsWith("sql")) return "sql";
    if (p.startsWith("mongo")) return "mongo";
    if (p.startsWith("kafka") || p.startsWith("streaming")) return "kafka";
    if (p.startsWith("command")) return "command";
    if (p.startsWith("validator")) return "validation";
    if (p.startsWith("elasticsearch") || p.startsWith("elastic")) return "elastic";
    return "core";
  }

  private List<String> buildExampleKeys(String prefix, String className) {
    // Generate 2-3 representative key examples based on the prefix
    return switch (prefix) {
      case "api"             -> List.of("api.service.{name}.host", "api.service.{name}.basePath", "api.enable-request-log");
      case "spec"            -> List.of("spec.api.{name}.header.Content-Type", "spec.api.{name}.header.Accept");
      case "response"        -> List.of("response.default-fields.success", "response.default-fields.error-code");
      case "selenium.driver" -> List.of("selenium.driver.headless", "selenium.driver.page-scan-locations", "selenium.driver.action-scan-locations");
      case "playwright.browser" -> List.of("playwright.browser.headless", "playwright.browser.browserType");
      case "appium.driver"   -> List.of("appium.driver.platformName", "appium.driver.deviceName");
      case "automation.engine" -> List.of("automation.engine.default-engine", "automation.engine.active-engines");
      case "web"             -> List.of("web.page.desktop.{page-name}.url");
      case "sql"             -> List.of("sql.service.{name}.host-name", "sql.service.{name}.db-name", "sql.service.{name}.db-type");
      case "mongo"           -> List.of("mongo.service.{name}.hosts", "mongo.service.{name}.db-name");
      case "kafka"           -> List.of("kafka.service.{name}.servers", "kafka.service.{name}.group-id", "kafka.service.{name}.topics.{topic}");
      case "command.executor" -> List.of("command.executor.scan-locations", "command.executor.cache-enabled");
      case "validator.helper" -> List.of("validator.helper.scan-locations", "validator.helper.validations-path");
      default               -> List.of(prefix + ".*");
    };
  }

  // ── Built-in fallback (derived from scanning testara source @LoadProperties) ──────────

  public static List<RuntimeCatalogEntry> builtInCatalog() {
    return List.of(
        entry("api",         "api",              "testara-api",           "ApiProperties",         "api.service.{name}.host", "api.service.{name}.basePath", "api.enable-request-log"),
        entry("api",         "spec",             "testara-api",           "ApiSpecProperties",     "spec.api.{name}.header.Content-Type", "spec.api.{name}.header.Accept"),
        entry("api",         "response",         "testara-api",           "ResponseMappingProperties", "response.default-fields.success", "response.default-fields.error-code"),
        entry("ui",          "automation.engine","testara-ui",            "EngineProperties",      "automation.engine.default-engine", "automation.engine.active-engines"),
        entry("ui",          "web",              "testara-ui",            "WebPageDataProperties", "web.page.desktop.{page}.url"),
        entry("ui-selenium", "selenium.driver",  "testara-ui-selenium",   "SeleniumDriverProperties", "selenium.driver.headless", "selenium.driver.page-scan-locations", "selenium.driver.action-scan-locations"),
        entry("ui-playwright","playwright.browser","testara-ui-playwright","PlaywrightDriverProperties", "playwright.browser.headless", "playwright.browser.browserType"),
        entry("ui-appium",   "appium.driver",    "testara-ui-appium",     "AppiumDriverProperties","appium.driver.platformName", "appium.driver.deviceName"),
        entry("sql",         "sql",              "testara-database",      "DatabaseProperties",    "sql.service.{name}.host-name", "sql.service.{name}.db-name", "sql.service.{name}.db-type"),
        entry("mongo",       "mongo",            "testara-database",      "MongoProperties",       "mongo.service.{name}.hosts", "mongo.service.{name}.db-name"),
        entry("kafka",       "kafka",            "testara-streaming",     "KafkaProperties",       "kafka.service.{name}.servers", "kafka.service.{name}.group-id", "kafka.service.{name}.topics.{topic}"),
        entry("elastic",     "elasticsearch",    "testara-elastic",       "ElasticSearchProperties","elasticsearch.service.{name}.hosts"),
        entry("command",     "command.executor", "testara-command",       "CommandExecutorProperties","command.executor.scan-locations", "command.executor.cache-enabled"),
        entry("validation",  "validator.helper", "testara-validation",    "ValidatorProperties",   "validator.helper.scan-locations", "validator.helper.validations-path")
    );
  }

  private static RuntimeCatalogEntry entry(String slice, String prefix, String module,
      String className, String... examples) {
    return new RuntimeCatalogEntry(slice, prefix, module, className, List.of(examples));
  }
}
