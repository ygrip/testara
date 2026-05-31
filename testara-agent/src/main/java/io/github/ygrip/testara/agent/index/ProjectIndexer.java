package io.github.ygrip.testara.agent.index;

import io.github.ygrip.testara.agent.catalog.RuntimeCatalogEntry;
import io.github.ygrip.testara.agent.catalog.RuntimeCatalogIndexer;
import io.github.ygrip.testara.agent.flavor.FlavorEntry;
import io.github.ygrip.testara.agent.flavor.TestaraFlavorIndexer;
import io.github.ygrip.testara.agent.parser.FeatureParser;

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
 * Indexes a Testara project: detects modules, feature files, step definitions,
 * commands, validators, and tags — all from the file system without executing code.
 */
public class ProjectIndexer {

  private static final Logger LOG = Logger.getLogger(ProjectIndexer.class.getName());

  private static final Pattern MODULE_PATTERN = Pattern.compile(
      "<module>\\s*([^<]+?)\\s*</module>");
  private static final Pattern JAVA_VERSION_PATTERN = Pattern.compile(
      "<java\\.version>\\s*([^<]+?)\\s*</java\\.version>");
  private static final Pattern STEP_ANNOTATION = Pattern.compile(
      "@(Given|When|Then|And|But)\\(\"(.*?)\"\\)");
  private static final Pattern COMMAND_TAG = Pattern.compile(
      "@CommandTag\\([^)]*command\\s*=\\s*\"([^\"]+)\"[^)]*(?:alias\\s*=\\s*\\{([^}]*)\\})?[^)]*(?:cacheable\\s*=\\s*(true|false))?");
  private static final Pattern VALIDATION_TAG = Pattern.compile(
      "@ValidationTag\\([^)]*command\\s*=\\s*\"([^\"]+)\"[^)]*(?:alias\\s*=\\s*\\{([^}]*)\\})?[^)]*(?:cacheable\\s*=\\s*(true|false))?");
  private static final Pattern DRIVER_METADATA = Pattern.compile(
      "@DriverMetadata\\(([^)]+)\\)");
  private static final Pattern DRIVER_NAME = Pattern.compile(
      "name\\s*=\\s*\"([^\"]+)\"");
  private static final Pattern DRIVER_ENGINE = Pattern.compile(
      "engine\\s*=\\s*(\\S+)\\.class");
  private static final Pattern DRIVER_PLATFORMS = Pattern.compile(
      "platforms\\s*=\\s*\\{([^}]*)\\}");
  private static final Pattern DRIVER_BROWSER = Pattern.compile(
      "browserName\\s*=\\s*\"([^\"]+)\"");
  private static final Pattern CLASS_NAME = Pattern.compile(
      "(?:public\\s+)?(?:class|interface)\\s+(\\w+)");

  private static final Pattern SCAN_LOCATIONS_PATTERN = Pattern.compile(
      "(?:command|validator)\\.executor\\.scan-locations\\s*=\\s*(.+)");

  private final FeatureParser featureParser = new FeatureParser();
  private final TestaraFlavorIndexer flavorIndexer = new TestaraFlavorIndexer();
  private final RuntimeCatalogIndexer catalogIndexer = new RuntimeCatalogIndexer();

  public TestaraProjectProfile index(Path projectRoot) {
    LOG.info("Indexing project at " + projectRoot);

    List<String> modules = detectModules(projectRoot);
    String javaVersion = detectJavaVersion(projectRoot);
    BuildTool buildTool = Files.exists(projectRoot.resolve("pom.xml"))
        ? BuildTool.MAVEN : BuildTool.GRADLE;

    // Collect all Java source roots: project root + all Maven module source dirs
    List<Path> javaSourceRoots = collectJavaSourceRoots(projectRoot, modules);

    // Read configured scan packages from configuration.properties
    Set<String> scanPackages = readScanPackages(projectRoot);

    List<Path> featureRoots = findFeatureRoots(projectRoot);
    List<Path> requestSpecRoots = findResourceDirs(projectRoot, "files");
    List<Path> validationRoots = findResourceDirs(projectRoot, "validations");

    List<FeatureIndex> features = parseFeatures(featureRoots);
    List<StepDefinitionIndex> stepDefs = scanStepDefinitions(javaSourceRoots);
    List<CommandIndex> commands = scanCommands(javaSourceRoots, scanPackages);
    List<ValidationIndex> validations = scanValidations(javaSourceRoots, scanPackages);
    List<DriverIndex> drivers = scanDrivers(javaSourceRoots);
    List<TagIndex> tags = buildTagIndex(features);
    List<FlavorEntry> flavorSteps = flavorIndexer.index(projectRoot, modules);
    List<RuntimeCatalogEntry> runtimeCatalog = catalogIndexer.index(projectRoot, modules);
    LOG.info("Flavor index: " + flavorSteps.size() + " built-in steps, "
        + runtimeCatalog.size() + " config catalog entries");

    return new TestaraProjectProfile(
        projectRoot, buildTool, javaVersion, modules,
        featureRoots, requestSpecRoots, validationRoots,
        features, stepDefs, commands, validations, drivers, tags,
        Map.of("scanPackages", String.join(",", scanPackages)), Map.of(),
        flavorSteps, runtimeCatalog);
  }

  // ── Source root collection ────────────────────────────────────────

  private List<Path> collectJavaSourceRoots(Path root, List<String> modules) {
    List<Path> roots = new ArrayList<>();
    roots.add(root);
    for (String module : modules) {
      Path moduleDir = root.resolve(module);
      Path mainSrc = moduleDir.resolve("src/main/java");
      Path testSrc = moduleDir.resolve("src/test/java");
      if (Files.exists(mainSrc)) roots.add(mainSrc.getParent().getParent().getParent()); // module root
      else roots.add(moduleDir);
    }
    return List.copyOf(roots);
  }

  // ── Scan package config ───────────────────────────────────────────

  private Set<String> readScanPackages(Path root) {
    // Default: testara built-in package (same as CommandExecutorProperties default)
    Set<String> defaults = new LinkedHashSet<>(List.of("io.github.ygrip.testara"));
    Path config = findConfigProperties(root);
    if (config == null) return defaults;
    try {
      String content = Files.readString(config, StandardCharsets.UTF_8);
      Matcher m = SCAN_LOCATIONS_PATTERN.matcher(content);
      if (m.find()) {
        String[] parts = m.group(1).trim().split(",");
        Set<String> locations = new LinkedHashSet<>();
        for (String p : parts) {
          String pkg = p.strip();
          if (!pkg.isBlank()) locations.add(pkg);
        }
        if (!locations.isEmpty()) {
          locations.addAll(defaults); // always include testara built-ins
          return locations;
        }
      }
    } catch (IOException e) {
      LOG.fine("Cannot read config for scan packages: " + e.getMessage());
    }
    return defaults;
  }

  private Path findConfigProperties(Path root) {
    for (String candidate : List.of(
        "src/test/resources/configuration.properties",
        "configuration.properties",
        "src/main/resources/configuration.properties")) {
      Path p = root.resolve(candidate);
      if (Files.exists(p)) return p;
    }
    return null;
  }

  // ── Module detection ──────────────────────────────────────────────

  private List<String> detectModules(Path root) {
    Path pom = root.resolve("pom.xml");
    if (!Files.exists(pom)) return List.of();
    try {
      String content = Files.readString(pom, StandardCharsets.UTF_8);
      List<String> modules = new ArrayList<>();
      Matcher m = MODULE_PATTERN.matcher(content);
      while (m.find()) modules.add(m.group(1));
      return List.copyOf(modules);
    } catch (IOException e) {
      LOG.warning("Cannot read pom.xml: " + e.getMessage());
      return List.of();
    }
  }

  private String detectJavaVersion(Path root) {
    Path pom = root.resolve("pom.xml");
    if (!Files.exists(pom)) return "unknown";
    try {
      String content = Files.readString(pom, StandardCharsets.UTF_8);
      Matcher m = JAVA_VERSION_PATTERN.matcher(content);
      return m.find() ? m.group(1) : "unknown";
    } catch (IOException e) {
      return "unknown";
    }
  }

  // ── Feature root detection ────────────────────────────────────────

  private List<Path> findFeatureRoots(Path root) {
    List<Path> roots = new ArrayList<>();
    try {
      Files.walkFileTree(root, Set.of(), 6, new SimpleFileVisitor<>() {
        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
          String name = dir.getFileName() == null ? "" : dir.getFileName().toString();
          if (name.equals("target")) return FileVisitResult.SKIP_SUBTREE;
          if (name.equals("features")) roots.add(dir);
          return FileVisitResult.CONTINUE;
        }
        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) {
          LOG.fine("Skipping inaccessible path: " + file);
          return FileVisitResult.CONTINUE;
        }
      });
    } catch (IOException e) {
      LOG.warning("Error scanning for feature roots: " + e.getMessage());
    }
    return List.copyOf(roots);
  }

  private List<Path> findResourceDirs(Path root, String dirName) {
    List<Path> dirs = new ArrayList<>();
    try {
      Files.walkFileTree(root, Set.of(), 6, new SimpleFileVisitor<>() {
        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
          String name = dir.getFileName() == null ? "" : dir.getFileName().toString();
          if (name.equals("target")) return FileVisitResult.SKIP_SUBTREE;
          if (name.equals(dirName)) dirs.add(dir);
          return FileVisitResult.CONTINUE;
        }
        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) {
          LOG.fine("Skipping inaccessible path: " + file);
          return FileVisitResult.CONTINUE;
        }
      });
    } catch (IOException e) {
      LOG.warning("Error scanning for " + dirName + " dirs: " + e.getMessage());
    }
    return List.copyOf(dirs);
  }

  // ── Feature parsing ───────────────────────────────────────────────

  private List<FeatureIndex> parseFeatures(List<Path> featureRoots) {
    List<FeatureIndex> features = new ArrayList<>();
    for (Path root : featureRoots) {
      try (Stream<Path> walk = Files.walk(root)) {
        walk.filter(p -> p.toString().endsWith(".feature"))
            .forEach(p -> {
              try {
                features.add(featureParser.parse(p));
              } catch (IOException e) {
                LOG.warning("Cannot parse feature " + p + ": " + e.getMessage());
              }
            });
      } catch (IOException e) {
        LOG.warning("Error walking feature root " + root + ": " + e.getMessage());
      }
    }
    return List.copyOf(features);
  }

  // ── Step definition scanning ──────────────────────────────────────

  private List<StepDefinitionIndex> scanStepDefinitions(List<Path> roots) {
    List<StepDefinitionIndex> defs = new ArrayList<>();
    for (Path root : roots) {
      for (Path javaFile : scanJavaFiles(root)) {
        try {
          String content = Files.readString(javaFile, StandardCharsets.UTF_8);
          String className = extractClassName(content);
          Matcher m = STEP_ANNOTATION.matcher(content);
          while (m.find()) {
            defs.add(new StepDefinitionIndex(m.group(1), m.group(2), javaFile, className, ""));
          }
        } catch (IOException e) {
          LOG.fine("Cannot read " + javaFile);
        }
      }
    }
    return List.copyOf(defs);
  }

  // ── Command scanning ──────────────────────────────────────────────

  private List<CommandIndex> scanCommands(List<Path> roots, Set<String> scanPackages) {
    Set<String> seen = new HashSet<>();
    List<CommandIndex> commands = new ArrayList<>();
    for (Path root : roots) {
      for (Path javaFile : scanJavaFiles(root)) {
        if (!matchesScanPackage(javaFile, scanPackages)) continue;
        try {
          String content = Files.readString(javaFile, StandardCharsets.UTF_8);
          if (!content.contains("@CommandTag")) continue;
          Matcher m = COMMAND_TAG.matcher(content);
          while (m.find()) {
            String name = m.group(1);
            if (seen.add(name)) {
              List<String> aliases = parseStringArray(m.group(2));
              boolean cacheable = "true".equals(m.group(3));
              String returnType = extractReturnType(content);
              String className = extractClassName(content);
              commands.add(new CommandIndex(name, aliases, returnType, cacheable, javaFile, className));
            }
          }
        } catch (IOException e) {
          LOG.fine("Cannot read " + javaFile);
        }
      }
    }
    return List.copyOf(commands);
  }

  // ── Validation scanning ───────────────────────────────────────────

  private List<ValidationIndex> scanValidations(List<Path> roots, Set<String> scanPackages) {
    Set<String> seen = new HashSet<>();
    List<ValidationIndex> validations = new ArrayList<>();
    for (Path root : roots) {
      for (Path javaFile : scanJavaFiles(root)) {
        if (!matchesScanPackage(javaFile, scanPackages)) continue;
        try {
          String content = Files.readString(javaFile, StandardCharsets.UTF_8);
          if (!content.contains("@ValidationTag")) continue;
          Matcher m = VALIDATION_TAG.matcher(content);
          while (m.find()) {
            String name = m.group(1);
            if (seen.add(name)) {
              List<String> aliases = parseStringArray(m.group(2));
              boolean cacheable = "true".equals(m.group(3));
              String actualType = extractGenericType(content, "ValidatorLogic", 0);
              String expectedType = extractGenericType(content, "ValidatorLogic", 1);
              String className = extractClassName(content);
              validations.add(new ValidationIndex(name, aliases, actualType, expectedType, cacheable, javaFile, className));
            }
          }
        } catch (IOException e) {
          LOG.fine("Cannot read " + javaFile);
        }
      }
    }
    return List.copyOf(validations);
  }

  // ── Driver scanning ───────────────────────────────────────────────

  private List<DriverIndex> scanDrivers(List<Path> roots) {
    List<DriverIndex> drivers = new ArrayList<>();
    for (Path root : roots) {
      for (Path javaFile : scanJavaFiles(root)) {
        try {
          String content = Files.readString(javaFile, StandardCharsets.UTF_8);
          if (!content.contains("@DriverMetadata")) continue;
          Matcher meta = DRIVER_METADATA.matcher(content);
          if (meta.find()) {
            String block = meta.group(1);
            Matcher nameMatcher = DRIVER_NAME.matcher(block);
            String name = nameMatcher.find() ? nameMatcher.group(1) : "";
            Matcher engineMatcher = DRIVER_ENGINE.matcher(block);
            String engine = engineMatcher.find() ? engineMatcher.group(1) : "";
            Matcher platformsMatcher = DRIVER_PLATFORMS.matcher(block);
            List<String> platforms = platformsMatcher.find()
                ? parseStringArray(platformsMatcher.group(1).replaceAll("DeviceType\\.", ""))
                : List.of();
            Matcher browserMatcher = DRIVER_BROWSER.matcher(block);
            String browser = browserMatcher.find() ? browserMatcher.group(1) : "";
            String className = extractClassName(content);
            if (!name.isBlank()) {
              drivers.add(new DriverIndex(name, engine, platforms, browser, javaFile, className));
            }
          }
        } catch (IOException e) {
          LOG.fine("Cannot read " + javaFile);
        }
      }
    }
    return List.copyOf(drivers);
  }

  // ── Tag index ─────────────────────────────────────────────────────

  private List<TagIndex> buildTagIndex(List<FeatureIndex> features) {
    Map<String, List<Path>> tagFeatures = new TreeMap<>();
    Map<String, Integer> tagScenarios = new TreeMap<>();
    Map<String, List<String>> tagScenarioNames = new TreeMap<>();

    for (FeatureIndex feature : features) {
      for (String tag : feature.tags()) {
        tagFeatures.computeIfAbsent(tag, k -> new ArrayList<>()).add(feature.path());
      }
      for (ScenarioIndex scenario : feature.scenarios()) {
        for (String tag : scenario.tags()) {
          tagFeatures.computeIfAbsent(tag, k -> new ArrayList<>()).add(feature.path());
          tagScenarios.merge(tag, 1, Integer::sum);
          tagScenarioNames.computeIfAbsent(tag, k -> new ArrayList<>()).add(scenario.name());
        }
      }
    }

    return tagFeatures.keySet().stream().map(tag -> new TagIndex(
        tag,
        (int) tagFeatures.getOrDefault(tag, List.of()).stream().distinct().count(),
        tagScenarios.getOrDefault(tag, 0),
        tagFeatures.getOrDefault(tag, List.of()).stream().distinct().toList(),
        tagScenarioNames.getOrDefault(tag, List.of())
    )).collect(Collectors.toList());
  }

  // ── Utilities ─────────────────────────────────────────────────────

  private List<Path> scanJavaFiles(Path root) {
    List<Path> files = new ArrayList<>();
    try {
      Files.walkFileTree(root, new SimpleFileVisitor<>() {
        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
          String name = dir.getFileName() == null ? "" : dir.getFileName().toString();
          return name.equals("target") ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
        }
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
          if (file.toString().endsWith(".java")) files.add(file);
          return FileVisitResult.CONTINUE;
        }
        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) {
          LOG.fine("Skipping inaccessible path: " + file);
          return FileVisitResult.CONTINUE;
        }
      });
    } catch (IOException e) {
      LOG.warning("Error scanning Java files: " + e.getMessage());
    }
    return files;
  }

  private String extractClassName(String source) {
    Matcher m = CLASS_NAME.matcher(source);
    return m.find() ? m.group(1) : "";
  }

  private String extractReturnType(String source) {
    // e.g. "implements CommandLogic<String>" → "String"
    Pattern p = Pattern.compile("implements\\s+CommandLogic\\s*<\\s*([^>]+)\\s*>");
    Matcher m = p.matcher(source);
    return m.find() ? m.group(1).strip() : "Object";
  }

  private String extractGenericType(String source, String baseClass, int index) {
    Pattern p = Pattern.compile("extends\\s+" + baseClass + "\\s*<\\s*([^,>]+)(?:,\\s*([^>]+))?\\s*>");
    Matcher m = p.matcher(source);
    if (m.find()) {
      String type = index == 0 ? m.group(1) : m.group(2);
      return type != null ? type.strip() : "Object";
    }
    return "Object";
  }

  private boolean matchesScanPackage(Path javaFile, Set<String> scanPackages) {
    if (scanPackages.isEmpty()) return true;
    String path = javaFile.toString().replace('\\', '/');
    // Convert package prefix to path segment (e.g., "io.github.ygrip.testara" → "io/github/ygrip/testara")
    return scanPackages.stream().anyMatch(pkg ->
        path.contains(pkg.replace('.', '/')));
  }

  private List<String> parseStringArray(String raw) {
    if (raw == null || raw.isBlank()) return List.of();
    return Arrays.stream(raw.split(","))
        .map(s -> s.strip().replaceAll("^\"|\"$", ""))
        .filter(s -> !s.isBlank())
        .toList();
  }
}
