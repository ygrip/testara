package io.github.ygrip.testara.agent.skill;

import io.github.ygrip.testara.agent.safety.JavaCompilationGuard;
import io.github.ygrip.testara.agent.safety.OutputValidator;
import io.github.ygrip.testara.agent.safety.ProjectPathGuard;
import io.github.ygrip.testara.agent.validation.TestCompileGate;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Writes generated artifacts inside the project root. Existing files are never replaced unless the
 * caller passes {@code overwrite=true}; property files are merged key by key so repeated
 * generation is idempotent.
 */
final class ArtifactFiles {

  static final String OVERWRITE_OPTION = "overwrite";
  /** Service config (connections, drivers, scan locations) the generated artifacts need. */
  static final String CONFIGURATION_PROPERTIES = "src/test/resources/configuration.properties";
  /** Environment values (endpoints, page URLs, test data) the generated artifacts reference. */
  static final String APPLICATION_PROPERTIES = "src/test/resources/application.properties";
  /** Properties files the runtime loads from the test classpath, where generated config may live. */
  static final List<String> RUNTIME_PROPERTY_FILES = List.of(CONFIGURATION_PROPERTIES, APPLICATION_PROPERTIES);

  enum Status {
    CREATED("created"), OVERWRITTEN("overwritten"), EXISTS("exists");

    private final String label;

    Status(String label) { this.label = label; }

    String label() { return label; }
  }

  /** Outcome of one artifact write; {@link Status#EXISTS} means the file was left untouched. */
  record Written(Status status, String path) {
    boolean changed() { return status != Status.EXISTS; }

    String line() {
      if (status == Status.EXISTS) return "exists " + path + " (pass overwrite=true to replace)";
      return status.label() + " " + path;
    }
  }

  private ArtifactFiles() {}

  static boolean overwrite(AgentContext context) {
    return "true".equals(context.options().get(OVERWRITE_OPTION));
  }

  /** Resolves {@code relative} inside {@code root}; path-guard rejections surface as {@link IOException}. */
  static Path resolve(Path root, String relative) throws IOException {
    try {
      return ProjectPathGuard.resolveInside(root, relative);
    } catch (IllegalArgumentException e) {
      throw new IOException(e.getMessage(), e);
    }
  }

  static Written write(Path root, String relative, String content, boolean overwrite) throws IOException {
    Path target = resolve(root, relative);
    boolean existed = Files.exists(target);
    if (existed && !overwrite) return new Written(Status.EXISTS, relative);
    Files.createDirectories(target.getParent());
    Files.writeString(target, content, StandardCharsets.UTF_8);
    if (existed) return new Written(Status.OVERWRITTEN, relative);
    return new Written(Status.CREATED, relative);
  }

  /** Writes Java source after checking its structure and that its package matches the target path. */
  static Written writeJava(Path root, String relative, String source, boolean overwrite,
      boolean isCommand, boolean isValidator) throws IOException {
    var validation = OutputValidator.validateJavaSource(source, isCommand, isValidator);
    if (!validation.valid()) {
      throw new IOException("Refusing to write " + relative + ": " + String.join("; ", validation.errors()));
    }
    FilePatch patch = new FilePatch(Path.of(relative), FilePatchOperation.CREATE, source, "generated");
    if (!JavaCompilationGuard.packageMatchesPath(source, patch)) {
      throw new IOException("Refusing to write " + relative + ": package declaration does not match the file path");
    }
    return write(root, relative, source, overwrite);
  }

  /** Writes a feature file after checking it parses as Gherkin. */
  static Written writeFeature(Path root, String relative, String content, boolean overwrite) throws IOException {
    var validation = OutputValidator.validateFeature(content);
    if (!validation.valid()) {
      throw new IOException("Refusing to write " + relative + ": " + String.join("; ", validation.errors()));
    }
    return write(root, relative, content, overwrite);
  }

  /** Writes JSON after checking it parses. */
  static Written writeJson(Path root, String relative, String content, boolean overwrite) throws IOException {
    var validation = OutputValidator.validateJson(content);
    if (!validation.valid()) {
      throw new IOException("Refusing to write " + relative + ": " + String.join("; ", validation.errors()));
    }
    return write(root, relative, content, overwrite);
  }

  /**
   * Appends the {@code key=value} lines of {@code block} whose key is not yet defined in any
   * {@link #RUNTIME_PROPERTY_FILES runtime properties file} to {@code relative} (created when
   * missing). Comment lines are kept only when at least one key is added. Returns the keys that were
   * added; an empty list means nothing changed.
   */
  static PropertyMerge mergeProperties(Path root, String relative, String block) throws IOException {
    Path target = resolve(root, relative);
    String existing = "";
    if (Files.exists(target)) existing = Files.readString(target, StandardCharsets.UTF_8);
    Properties defined = new Properties();
    defined.load(new StringReader(existing));
    for (String candidate : RUNTIME_PROPERTY_FILES) {
      Path file = resolve(root, candidate);
      if (!file.equals(target) && Files.exists(file)) {
        defined.load(new StringReader(Files.readString(file, StandardCharsets.UTF_8)));
      }
    }

    List<String> comments = new ArrayList<>();
    List<String> additions = new ArrayList<>();
    List<String> addedKeys = new ArrayList<>();
    for (String line : block.split("\n")) {
      String stripped = line.strip();
      if (stripped.isEmpty()) continue;
      if (stripped.startsWith("#")) {
        comments.add(stripped);
        continue;
      }
      int separator = stripped.indexOf('=');
      if (separator <= 0) continue;
      String key = stripped.substring(0, separator).strip();
      if (defined.containsKey(key) || addedKeys.contains(key)) continue;
      additions.add(stripped);
      addedKeys.add(key);
    }
    if (additions.isEmpty()) return new PropertyMerge(relative, List.of());

    StringBuilder content = new StringBuilder(existing);
    if (!existing.isEmpty() && !existing.endsWith("\n")) content.append("\n");
    if (!existing.isBlank()) content.append("\n");
    comments.forEach(comment -> content.append(comment).append("\n"));
    additions.forEach(addition -> content.append(addition).append("\n"));
    Files.createDirectories(target.getParent());
    Files.writeString(target, content.toString(), StandardCharsets.UTF_8);
    return new PropertyMerge(relative, List.copyOf(addedKeys));
  }

  /**
   * Returns a warning when any test properties file (the runtime loads them all) sets
   * {@code automation.config.script-folder} to a folder other than the one generated request specs
   * are written for; otherwise null.
   */
  static String scriptFolderWarning(Path root) throws IOException {
    for (String candidate : RUNTIME_PROPERTY_FILES) {
      Path file = resolve(root, candidate);
      if (!Files.exists(file)) continue;
      Properties defined = new Properties();
      defined.load(new StringReader(Files.readString(file, StandardCharsets.UTF_8)));
      String folder = defined.getProperty(PropertyKeys.SCRIPT_FOLDER_KEY);
      if (folder == null || folder.strip().equals(PropertyKeys.SCRIPT_FOLDER)) continue;
      return "warning: " + candidate + " sets " + PropertyKeys.SCRIPT_FOLDER_KEY + "=" + folder.strip()
          + "; generated request specs under src/test/resources/files/ resolve only with "
          + PropertyKeys.scriptFolderEntry();
    }
    return null;
  }

  /** Result of {@link #mergeProperties}: the file touched and the keys that were missing and added. */
  record PropertyMerge(String path, List<String> addedKeys) {
    String line() {
      if (addedKeys.isEmpty()) return "unchanged " + path + " (all keys present)";
      return "updated " + path + " (+" + addedKeys.size() + " keys: " + String.join(", ", addedKeys) + ")";
    }
  }

  /** Runs the compile gate when the caller passed {@code compile=true}; otherwise returns null. */
  static String compileLine(AgentContext context) {
    if (!"true".equals(context.options().get("compile"))) return null;
    return new TestCompileGate().run(context.projectRoot()).toLine();
  }

  /** Makes a generated name a legal Java identifier (identifiers may not start with a digit). */
  static String javaIdentifier(String name, String fallback) {
    if (name == null || name.isBlank()) return fallback;
    if (Character.isDigit(name.charAt(0))) return "_" + name;
    return name;
  }
}
