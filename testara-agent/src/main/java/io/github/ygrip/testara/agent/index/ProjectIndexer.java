package io.github.ygrip.testara.agent.index;

import io.github.ygrip.testara.agent.catalog.RuntimeCatalogEntry;
import io.github.ygrip.testara.agent.catalog.RuntimeCatalogIndexer;
import io.github.ygrip.testara.agent.config.AgentYamlConfig;
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
  // Escape-aware: the annotation value may contain \" and \\ Java escapes.
  private static final Pattern STEP_ANNOTATION = Pattern.compile(
      "@(Given|When|Then|And|But)\\s*\\(\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
  // Capture the whole annotation body; attributes are extracted separately so their order does not matter.
  private static final Pattern COMMAND_TAG = Pattern.compile("@CommandTag\\s*\\(([^)]*)\\)");
  private static final Pattern VALIDATION_TAG = Pattern.compile("@ValidationTag\\s*\\(([^)]*)\\)");
  private static final Pattern TAG_COMMAND = Pattern.compile("\\bcommand\\s*=\\s*\"([^\"]+)\"");
  private static final Pattern TAG_ALIAS = Pattern.compile("\\balias\\s*=\\s*(?:\\{([^}]*)}|(\"[^\"]*\"))");
  private static final Pattern TAG_CACHEABLE = Pattern.compile("\\bcacheable\\s*=\\s*(true|false)");
  private static final Pattern COMMAND_LOGIC = Pattern.compile("\\bCommandLogic\\s*<");
  private static final Pattern VALIDATOR_LOGIC = Pattern.compile("\\bValidatorLogic\\s*<");
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

  // Keys bound to CommandExecutorProperties / ValidatorProperties.scanLocations (list or indexed form).
  private static final Pattern COMMAND_SCAN_KEY = Pattern.compile(
      "command\\.executor\\.(?:scan-locations|scanLocations)(?:\\[\\d+])?");
  private static final Pattern VALIDATOR_SCAN_KEY = Pattern.compile(
      "validator\\.helper\\.(?:scan-locations|scanLocations)(?:\\[\\d+])?");
  private static final String TESTARA_PACKAGE = "io.github.ygrip.testara";
  private static final List<String> RESOURCE_DIRS = List.of("src/test/resources", "src/main/resources");
  private static final Set<String> EXCLUDED_DIRECTORIES = Set.of(
      "target", ".git", ".testara-agent", "node_modules", ".idea", ".gradle");

  private record ScanPackages(Set<String> commands, Set<String> validations) {}

  private record TagAttributes(String name, List<String> aliases, boolean cacheable) {}

  private final FeatureParser featureParser = new FeatureParser();
  private final TestaraFlavorIndexer flavorIndexer = new TestaraFlavorIndexer();
  private final RuntimeCatalogIndexer catalogIndexer = new RuntimeCatalogIndexer();

  public TestaraProjectProfile index(Path projectRoot) {
    LOG.info("Indexing project at " + projectRoot);

    List<String> modules = detectModules(projectRoot);
    String javaVersion = detectJavaVersion(projectRoot);
    BuildTool buildTool = Files.exists(projectRoot.resolve("pom.xml"))
        ? BuildTool.MAVEN
        : (Files.exists(projectRoot.resolve("build.gradle"))
            || Files.exists(projectRoot.resolve("build.gradle.kts")) ? BuildTool.GRADLE : null);

    // Collect all Java source roots: project root + all Maven module source dirs
    List<Path> javaSourceRoots = collectJavaSourceRoots(projectRoot, modules);

    // Read configured scan packages from every classpath *.properties file
    ScanPackages scanPackages = readScanPackages(projectRoot, modules);

    AgentYamlConfig.AgentConfig agentConfig = AgentYamlConfig.load(projectRoot);
    List<Path> featureRoots = configuredRoots(projectRoot, agentConfig.featureRoots(), findFeatureRoots(projectRoot));
    List<Path> requestSpecRoots = configuredRoots(projectRoot, agentConfig.requestSpecRoots(), findResourceDirs(projectRoot, "files"));
    List<Path> validationRoots = configuredRoots(projectRoot, agentConfig.validationRoots(), findResourceDirs(projectRoot, "validations"));

    List<String> parseErrors = new ArrayList<>();
    List<FeatureIndex> features = parseFeatures(projectRoot, featureRoots, parseErrors);
    List<StepDefinitionIndex> stepDefs = scanStepDefinitions(javaSourceRoots);
    List<CommandIndex> commands = scanCommands(javaSourceRoots, scanPackages.commands());
    List<ValidationIndex> validations = scanValidations(javaSourceRoots, scanPackages.validations());
    List<DriverIndex> drivers = scanDrivers(javaSourceRoots);
    List<TagIndex> tags = buildTagIndex(features);
    List<FlavorEntry> flavorSteps = flavorIndexer.index(projectRoot, modules);
    List<RuntimeCatalogEntry> runtimeCatalog = catalogIndexer.index(projectRoot, modules);
    LOG.info("Flavor index: " + flavorSteps.size() + " built-in steps, "
        + runtimeCatalog.size() + " config catalog entries");

    Set<String> allScanPackages = new LinkedHashSet<>(scanPackages.commands());
    allScanPackages.addAll(scanPackages.validations());
    return new TestaraProjectProfile(
        projectRoot, buildTool, javaVersion, modules,
        featureRoots, requestSpecRoots, validationRoots,
        features, stepDefs, commands, validations, drivers, tags,
        Map.of("scanPackages", String.join(",", allScanPackages),
            "commandScanPackages", String.join(",", scanPackages.commands()),
            "validationScanPackages", String.join(",", scanPackages.validations())),
        Map.of(), flavorSteps, runtimeCatalog, List.copyOf(parseErrors));
  }

  // ── Source root collection ────────────────────────────────────────

  /**
   * Returns the directories whose subtrees hold the project's sources: the project root plus any
   * declared module that lives outside it (e.g. {@code ../sibling-module}).
   */
  public static List<Path> collectJavaSourceRoots(Path root, List<String> modules) {
    // scanJavaFiles(root) already walks the entire subtree under root, so a module directory only
    // needs its own entry here if it's NOT nested under root (e.g. a "../sibling-module" reference)
    // - otherwise every file under it would be visited twice: once via root, once via its own entry.
    List<Path> roots = new ArrayList<>();
    roots.add(root);
    Path normalizedRoot = root.toAbsolutePath().normalize();
    for (String module : modules) {
      Path moduleDir = root.resolve(module).toAbsolutePath().normalize();
      if (!moduleDir.startsWith(normalizedRoot)) {
        roots.add(moduleDir);
      }
    }
    return List.copyOf(roots);
  }

  // ── Scan package config ───────────────────────────────────────────

  /**
   * The runtime loads every {@code classpath:*.properties} file, so scan locations may live in any
   * top-level properties file of a resources dir. Commands and validators have separate keys.
   */
  private ScanPackages readScanPackages(Path root, List<String> modules) {
    Set<String> commands = new LinkedHashSet<>();
    Set<String> validations = new LinkedHashSet<>();
    for (Path file : classpathPropertyFiles(root, modules)) {
      Properties properties = new Properties();
      try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
        properties.load(reader);
      } catch (IOException | IllegalArgumentException e) {
        LOG.warning("Cannot read " + file + " for scan locations: " + e.getMessage());
        continue;
      }
      for (String key : properties.stringPropertyNames()) {
        if (COMMAND_SCAN_KEY.matcher(key).matches()) {
          commands.addAll(splitPackages(properties.getProperty(key)));
        } else if (VALIDATOR_SCAN_KEY.matcher(key).matches()) {
          validations.addAll(splitPackages(properties.getProperty(key)));
        }
      }
    }
    // Always include testara built-ins (the runtime default for both properties)
    commands.add(TESTARA_PACKAGE);
    validations.add(TESTARA_PACKAGE);
    return new ScanPackages(commands, validations);
  }

  private List<Path> classpathPropertyFiles(Path root, List<String> modules) {
    Set<Path> moduleDirs = new LinkedHashSet<>();
    moduleDirs.add(root);
    modules.forEach(module -> moduleDirs.add(root.resolve(module).normalize()));
    List<Path> files = new ArrayList<>();
    for (Path moduleDir : moduleDirs) {
      for (String resourceDir : RESOURCE_DIRS) {
        Path dir = moduleDir.resolve(resourceDir);
        if (!Files.isDirectory(dir)) continue;
        try (Stream<Path> listing = Files.list(dir)) {
          listing.filter(p -> p.getFileName().toString().endsWith(".properties"))
              .filter(Files::isRegularFile)
              .sorted()
              .forEach(files::add);
        } catch (IOException e) {
          LOG.warning("Cannot list " + dir + ": " + e.getMessage());
        }
      }
    }
    return files;
  }

  private List<String> splitPackages(String value) {
    return Arrays.stream(value.split(","))
        .map(String::strip)
        .filter(pkg -> !pkg.isBlank())
        .toList();
  }

  // ── Module detection ──────────────────────────────────────────────

  /** Reads {@code <module>} entries from the root {@code pom.xml}; empty when absent or unreadable. */
  public static List<String> detectModules(Path root) {
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

  private List<Path> configuredRoots(Path root, List<String> configured, List<Path> discovered) {
    if (configured == null || configured.isEmpty()) return discovered;
    Path normalizedRoot = root.toAbsolutePath().normalize();
    List<Path> roots = configured.stream()
        .map(normalizedRoot::resolve)
        .map(Path::normalize)
        .filter(path -> path.startsWith(normalizedRoot))
        .filter(Files::isDirectory)
        .distinct()
        .toList();
    return roots.isEmpty() ? discovered : roots;
  }

  private List<Path> findFeatureRoots(Path root) {
    List<Path> roots = new ArrayList<>();
    try {
      Files.walkFileTree(root, new SimpleFileVisitor<>() {
        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
          if (isExcludedDirectory(dir)) return FileVisitResult.SKIP_SUBTREE;
          if (dir.endsWith("features")) {
            roots.add(dir);
            // parseFeatures walks this subtree; do not discover nested "features" roots again.
            return FileVisitResult.SKIP_SUBTREE;
          }
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
      Files.walkFileTree(root, new SimpleFileVisitor<>() {
        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
          if (isExcludedDirectory(dir)) return FileVisitResult.SKIP_SUBTREE;
          if (dir.endsWith(dirName)) {
            dirs.add(dir);
            return FileVisitResult.SKIP_SUBTREE;
          }
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

  private List<FeatureIndex> parseFeatures(Path projectRoot, List<Path> featureRoots, List<String> parseErrors) {
    List<FeatureIndex> features = new ArrayList<>();
    // Overlapping roots (e.g. "." and "src") must not parse the same file twice.
    Set<Path> parsed = new HashSet<>();
    for (Path root : featureRoots) {
      try {
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
          @Override
          public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
            if (isExcludedDirectory(dir)) return FileVisitResult.SKIP_SUBTREE;
            return FileVisitResult.CONTINUE;
          }
          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            if (!file.toString().endsWith(".feature")) return FileVisitResult.CONTINUE;
            try {
              if (parsed.add(file.toRealPath())) {
                features.add(featureParser.parse(file));
              }
            } catch (IOException | RuntimeException e) {
              recordParseError(projectRoot, file, e, parseErrors);
            }
            return FileVisitResult.CONTINUE;
          }
          @Override
          public FileVisitResult visitFileFailed(Path file, IOException exc) {
            recordParseError(projectRoot, file, exc, parseErrors);
            return FileVisitResult.CONTINUE;
          }
        });
      } catch (IOException e) {
        recordParseError(projectRoot, root, e, parseErrors);
      }
    }
    return List.copyOf(features);
  }

  private void recordParseError(Path projectRoot, Path file, Exception e, List<String> parseErrors) {
    Path absoluteRoot = projectRoot.toAbsolutePath().normalize();
    Path absoluteFile = file.toAbsolutePath().normalize();
    String location = absoluteFile.toString();
    if (absoluteFile.startsWith(absoluteRoot)) {
      location = absoluteRoot.relativize(absoluteFile).toString().replace('\\', '/');
    }
    LOG.warning("Cannot parse feature " + location + ": " + e.getMessage());
    parseErrors.add(location + ": " + e.getMessage());
  }

  // ── Step definition scanning ──────────────────────────────────────

  private List<StepDefinitionIndex> scanStepDefinitions(List<Path> roots) {
    Set<String> seen = new HashSet<>();
    List<StepDefinitionIndex> defs = new ArrayList<>();
    for (Path root : roots) {
      for (Path javaFile : scanJavaFiles(root)) {
        try {
          String content = Files.readString(javaFile, StandardCharsets.UTF_8);
          String className = extractClassName(content);
          Matcher m = STEP_ANNOTATION.matcher(content);
          while (m.find()) {
            // Store the runtime expression, not its Java-escaped source form (\\d+ -> \d+).
            String expression = TestaraFlavorIndexer.unescapeAnnotation(m.group(2));
            if (seen.add(javaFile + ":" + expression)) {
              defs.add(new StepDefinitionIndex(m.group(1), expression, javaFile, className, ""));
            }
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
            Optional<TagAttributes> tag = parseTagAttributes(m.group(1));
            if (tag.isPresent() && seen.add(tag.get().name())) {
              String returnType = genericArgument(content, COMMAND_LOGIC, 0);
              String className = extractClassName(content);
              commands.add(new CommandIndex(tag.get().name(), tag.get().aliases(), returnType,
                  tag.get().cacheable(), javaFile, className));
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
            Optional<TagAttributes> tag = parseTagAttributes(m.group(1));
            if (tag.isPresent() && seen.add(tag.get().name())) {
              String actualType = genericArgument(content, VALIDATOR_LOGIC, 0);
              String expectedType = genericArgument(content, VALIDATOR_LOGIC, 1);
              String className = extractClassName(content);
              validations.add(new ValidationIndex(tag.get().name(), tag.get().aliases(), actualType,
                  expectedType, tag.get().cacheable(), javaFile, className));
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
    Set<String> seen = new HashSet<>();
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
            if (!name.isBlank() && seen.add(javaFile + ":" + name)) {
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
    Map<String, Set<Path>> tagFeatures = new TreeMap<>();
    Map<String, Set<String>> tagScenarioKeys = new TreeMap<>();
    Map<String, LinkedHashSet<String>> tagScenarioNames = new TreeMap<>();
    Map<String, Integer> tagCases = new TreeMap<>();

    for (FeatureIndex feature : features) {
      // Keep feature-only tags visible even when a feature currently has no scenarios.
      for (String tag : feature.tags()) {
        tagFeatures.computeIfAbsent(tag, k -> new LinkedHashSet<>()).add(feature.path());
      }

      for (int ordinal = 0; ordinal < feature.scenarios().size(); ordinal++) {
        ScenarioIndex scenario = feature.scenarios().get(ordinal);
        Set<String> baseTags = new LinkedHashSet<>(feature.tags());
        baseTags.addAll(scenario.tags());

        Set<String> definitionTags = new LinkedHashSet<>(baseTags);
        scenario.examples().forEach(ex -> definitionTags.addAll(ex.tags()));

        // Keyed by position: two scenarios may share a name and still count separately.
        String scenarioKey = feature.path() + "\u0000" + ordinal;
        for (String tag : definitionTags) {
          tagFeatures.computeIfAbsent(tag, k -> new LinkedHashSet<>()).add(feature.path());
          tagScenarioKeys.computeIfAbsent(tag, k -> new LinkedHashSet<>()).add(scenarioKey);
          tagScenarioNames.computeIfAbsent(tag, k -> new LinkedHashSet<>()).add(scenario.name());
        }

        if (scenario.type() == ScenarioType.SCENARIO_OUTLINE) {
          for (ExamplesIndex examples : scenario.examples()) {
            Set<String> caseTags = new LinkedHashSet<>(baseTags);
            caseTags.addAll(examples.tags());
            for (String tag : caseTags) {
              tagCases.merge(tag, examples.rowCount(), Integer::sum);
            }
          }
        } else {
          for (String tag : baseTags) {
            tagCases.merge(tag, 1, Integer::sum);
          }
        }
      }
    }

    return tagFeatures.keySet().stream().map(tag -> new TagIndex(
        tag,
        tagFeatures.getOrDefault(tag, Set.of()).size(),
        tagScenarioKeys.getOrDefault(tag, Set.of()).size(),
        tagCases.getOrDefault(tag, 0),
        List.copyOf(tagFeatures.getOrDefault(tag, Set.of())),
        List.copyOf(tagScenarioNames.getOrDefault(tag, new LinkedHashSet<>()))
    )).toList();
  }

  // ── Utilities ─────────────────────────────────────────────────────

  private List<Path> scanJavaFiles(Path root) {
    List<Path> files = new ArrayList<>();
    try {
      Files.walkFileTree(root, new SimpleFileVisitor<>() {
        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
          if (isExcludedDirectory(dir)) return FileVisitResult.SKIP_SUBTREE;
          return FileVisitResult.CONTINUE;
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

  /**
   * True for build output, VCS and tool directories that must never be indexed or fingerprinted.
   * {@code build} is only excluded next to a Gradle build script, so a {@code build} package survives.
   */
  public static boolean isExcludedDirectory(Path dir) {
    Path fileName = dir.getFileName();
    if (fileName == null) return false;
    String name = fileName.toString();
    if (EXCLUDED_DIRECTORIES.contains(name)) return true;
    if (!"build".equals(name)) return false;
    return Files.exists(dir.resolveSibling("build.gradle"))
        || Files.exists(dir.resolveSibling("build.gradle.kts"));
  }

  private String extractClassName(String source) {
    Matcher m = CLASS_NAME.matcher(source);
    return m.find() ? m.group(1) : "";
  }

  private Optional<TagAttributes> parseTagAttributes(String body) {
    Matcher command = TAG_COMMAND.matcher(body);
    if (!command.find()) return Optional.empty();
    List<String> aliases = List.of();
    Matcher alias = TAG_ALIAS.matcher(body);
    if (alias.find()) {
      if (alias.group(1) != null) {
        aliases = parseStringArray(alias.group(1));
      } else {
        aliases = parseStringArray(alias.group(2));
      }
    }
    Matcher cacheable = TAG_CACHEABLE.matcher(body);
    boolean isCacheable = cacheable.find() && "true".equals(cacheable.group(1));
    return Optional.of(new TagAttributes(command.group(1), aliases, isCacheable));
  }

  /**
   * Returns the {@code index}-th type argument of the first {@code Base<...>} occurrence, keeping
   * nested generics intact (e.g. {@code CommandLogic<Map<String, List<Integer>>>}).
   */
  private String genericArgument(String source, Pattern baseType, int index) {
    Matcher m = baseType.matcher(source);
    if (!m.find()) return "Object";
    List<String> arguments = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    int depth = 1;
    for (int i = m.end(); i < source.length() && depth > 0; i++) {
      char c = source.charAt(i);
      if (c == '<') {
        depth++;
      } else if (c == '>') {
        depth--;
      } else if (c == ',' && depth == 1) {
        arguments.add(current.toString().strip());
        current.setLength(0);
        continue;
      }
      if (depth > 0) current.append(c);
    }
    if (depth > 0) return "Object";
    arguments.add(current.toString().strip());
    if (index >= arguments.size() || arguments.get(index).isBlank()) return "Object";
    return arguments.get(index);
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
