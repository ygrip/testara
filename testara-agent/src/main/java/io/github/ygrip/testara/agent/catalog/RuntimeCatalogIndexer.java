package io.github.ygrip.testara.agent.catalog;

import io.github.ygrip.testara.agent.index.ProjectIndexer;

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

  /**
   * Scan source dirs for @LoadProperties and merge the result over the built-in catalog: a source
   * entry replaces the built-in entry with the same prefix, other built-ins are kept.
   */
  public List<RuntimeCatalogEntry> index(Path projectRoot, List<String> modules) {
    Map<String, RuntimeCatalogEntry> byPrefix = new LinkedHashMap<>();
    builtInCatalog().forEach(entry -> byPrefix.put(entry.prefix(), entry));
    for (Path root : ProjectIndexer.collectJavaSourceRoots(projectRoot, modules)) {
      if (!Files.isDirectory(root)) continue;
      scanDir(root).forEach(entry -> byPrefix.put(entry.prefix(), entry));
    }
    return List.copyOf(byPrefix.values());
  }

  private List<RuntimeCatalogEntry> scanDir(Path root) {
    List<RuntimeCatalogEntry> entries = new ArrayList<>();
    try {
      Files.walkFileTree(root, new SimpleFileVisitor<>() {
        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
          String name = dir.getFileName() == null ? "" : dir.getFileName().toString();
          if (dir.equals(root)) return FileVisitResult.CONTINUE;
          if (ProjectIndexer.isExcludedDirectory(dir) || name.equals("test")) return FileVisitResult.SKIP_SUBTREE;
          return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
          // Any class may carry @LoadProperties (e.g. ClassScannerConfig, ReportConfiguration).
          if (!file.toString().endsWith(".java")) return FileVisitResult.CONTINUE;
          try {
            String source = Files.readString(file, StandardCharsets.UTF_8);
            if (!source.contains("@LoadProperties")) return FileVisitResult.CONTINUE;
            Matcher m = LOAD_PROPS.matcher(source);
            if (m.find()) {
              String prefix = m.group(1);
              String className = extractClassName(source);
              String module = detectModule(root.relativize(file));
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
    if (p.startsWith("vibium")) return "ui-vibium";
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
      case "playwright.browser" -> List.of("playwright.browser.headless", "playwright.browser.page-scan-locations", "playwright.browser.action-scan-locations");
      case "appium.driver"   -> List.of("appium.driver.remote-driver.android.uri", "appium.driver.capabilities.android.{name}.platformName", "appium.driver.capabilities.android.{name}.deviceName");
      case "vibium.browser"  -> List.of("vibium.browser.headless", "vibium.browser.vibium-binary-path");
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
        entry("ui-playwright","playwright.browser","testara-ui-playwright","PlaywrightDriverProperties", "playwright.browser.headless", "playwright.browser.page-scan-locations", "playwright.browser.action-scan-locations"),
        entry("ui-appium",   "appium.driver",    "testara-ui-appium",     "AppiumDriverProperties","appium.driver.remote-driver.android.uri", "appium.driver.capabilities.android.{name}.platformName", "appium.driver.capabilities.android.{name}.deviceName"),
        entry("ui-vibium",   "vibium.browser",   "testara-ui-vibium",     "VibiumDriverProperties","vibium.browser.headless", "vibium.browser.vibium-binary-path"),
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
