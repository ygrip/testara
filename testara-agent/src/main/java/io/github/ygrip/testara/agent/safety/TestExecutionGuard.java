package io.github.ygrip.testara.agent.safety;

import java.nio.file.Files;
import java.nio.file.Path;
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
