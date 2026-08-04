package io.github.ygrip.testara.agent.safety;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Safety checks before executing Maven test commands.
 *
 * <p>Validates project root, Maven availability, tag expression safety,
 * and rejects any shell injection patterns.
 */
public final class TestExecutionGuard {

  // Patterns that indicate shell injection attempts
  private static final Set<String> BLOCKED_PATTERNS = Set.of(
      "&&", "||", ";", "`", "$(", "${", ">", ">>", "<", "|", "&"
  );

  private static final Pattern SAFE_TAG_EXPR = Pattern.compile(
      "^[@\\w\\s()\\-]+$");

  private TestExecutionGuard() { /* utility */ }

  /**
   * Validate that a Maven command is safe to execute.
   * Returns an error message, or null if safe.
   */
  public static String validateCommand(String command) {
    if (command == null || command.isBlank()) {
      return "Command is blank";
    }
    for (String blocked : BLOCKED_PATTERNS) {
      if (command.contains(blocked) && !isSafeUsage(command, blocked)) {
        return "Command contains blocked shell pattern: " + blocked;
      }
    }
    if (!command.startsWith("mvn ") && !command.startsWith("./mvnw ") && !command.startsWith("mvnw ")) {
      return "Command must start with 'mvn', './mvnw', or 'mvnw'";
    }
    return null; // safe
  }

  /**
   * Validate an argv (one token per element, as passed directly to {@link ProcessBuilder} with no
   * shell involved) instead of a shell command string. Use this for real execution - {@code argv}
   * is never re-parsed by a shell, so this exists purely as defense-in-depth against a future
   * regression that reintroduces shell string execution.
   *
   * <p>The executable ({@code argv.get(0)}) is matched by filename rather than
   * {@link String#startsWith}, since it may be an absolute path to a resolved {@code mvnw}
   * wrapper rather than the bare literal {@code "mvn"}/{@code "./mvnw"}.
   */
  public static String validateArgv(List<String> argv) {
    if (argv == null || argv.isEmpty()) {
      return "Command is blank";
    }
    Path executable = Path.of(argv.get(0));
    String exeName = executable.getFileName() == null ? argv.get(0) : executable.getFileName().toString();
    if (!"mvn".equals(exeName) && !"mvnw".equals(exeName) && !"mvnw.cmd".equals(exeName)) {
      return "Command must invoke 'mvn', 'mvnw', or 'mvnw.cmd', got: " + exeName;
    }
    String args = String.join(" ", argv.subList(1, argv.size()));
    for (String blocked : BLOCKED_PATTERNS) {
      if (args.contains(blocked) && !isSafeUsage(args, blocked)) {
        return "Argument contains blocked shell pattern: " + blocked;
      }
    }
    return null; // safe
  }

  /** Allow '>' inside quoted arguments (e.g. tag expressions with '>') */
  private static boolean isSafeUsage(String command, String blocked) {
    // '&&' and '||' are always dangerous
    if ("&&".equals(blocked) || "||".equals(blocked) || ";".equals(blocked)) {
      return false;
    }
    // '>' and '<' may appear inside Cucumber tag expressions in quotes
    if (">".equals(blocked) || "<".equals(blocked)) {
      return command.contains(">" + blocked) || command.contains(blocked + "<")
          || command.contains("@" + blocked); // likely tag expression, not redirect
    }
    return false;
  }

  /** Check that the project root is valid and has a pom.xml. */
  public static boolean isValidProjectRoot(Path projectRoot) {
    return projectRoot != null
        && Files.exists(projectRoot.resolve("pom.xml"))
        && Files.isDirectory(projectRoot);
  }

  /** Validate a tag expression for safe characters. */
  public static boolean isValidTagExpression(String tagExpression) {
    return tagExpression != null
        && !tagExpression.isBlank()
        && SAFE_TAG_EXPR.matcher(tagExpression).matches();
  }
}
