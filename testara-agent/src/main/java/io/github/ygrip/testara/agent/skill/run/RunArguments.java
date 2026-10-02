package io.github.ygrip.testara.agent.skill.run;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import io.cucumber.tagexpressions.TagExpressionParser;

/**
 * Validation shared by the Maven and Gradle command builders for user-supplied run arguments.
 *
 * <p>A module is either a relative directory path ({@code api-tests}, {@code modules/api}) or a
 * colon form ({@code :api-tests} for Maven's {@code -pl :artifactId}, {@code :modules:api} for a
 * Gradle project path). Absolute paths, {@code .}/{@code ..} segments and shell characters are
 * rejected with {@link IllegalArgumentException}.
 */
public final class RunArguments {

  private static final Pattern SAFE_SEGMENT = Pattern.compile("^[\\w.-]+$");

  private RunArguments() { /* utility */ }

  public static void requireValidTagExpression(String tagExpression) {
    if (tagExpression == null || tagExpression.isBlank()) {
      throw new IllegalArgumentException("Tag expression must not be blank");
    }
    if (tagExpression.indexOf('\n') >= 0 || tagExpression.indexOf('\r') >= 0
        || tagExpression.indexOf('\0') >= 0) {
      throw new IllegalArgumentException("Tag expression contains control characters");
    }
    try {
      TagExpressionParser.parse(tagExpression);
    } catch (RuntimeException e) {
      throw new IllegalArgumentException("Invalid Cucumber tag expression: " + tagExpression, e);
    }
  }

  /** No-op for a blank module; otherwise throws when any segment is unsafe. */
  public static void requireValidModule(String module) {
    if (module == null || module.isBlank()) {
      return;
    }
    List<String> segments = segments(module);
    for (String segment : segments) {
      if (!SAFE_SEGMENT.matcher(segment).matches() || ".".equals(segment) || "..".equals(segment)) {
        throw new IllegalArgumentException("Module must be a relative module path (e.g. modules/api) "
            + "or :artifactId, got: " + module);
      }
    }
  }

  /** Gradle project path for a module ({@code modules/api} → {@code :modules:api}), or null when blank. */
  public static String gradleProjectPath(String module) {
    if (module == null || module.isBlank()) {
      return null;
    }
    requireValidModule(module);
    return ":" + String.join(":", segments(module));
  }

  /**
   * Directory the selected module runs in (where its reports and rerun file are written). A colon
   * form resolves to the matching directory when it exists, otherwise to the project root.
   */
  public static Path moduleDirectory(Path projectRoot, String module) {
    if (module == null || module.isBlank()) {
      return projectRoot;
    }
    requireValidModule(module);
    Path directory = projectRoot.resolve(String.join("/", segments(module))).normalize();
    if (module.strip().startsWith(":") && !Files.isDirectory(directory)) {
      return projectRoot;
    }
    return directory;
  }

  private static List<String> segments(String module) {
    String value = module.strip();
    if (value.startsWith(":")) {
      return List.of(value.substring(1).split(":", -1));
    }
    if (value.endsWith("/")) {
      value = value.substring(0, value.length() - 1);
    }
    return List.of(value.split("/", -1));
  }
}
