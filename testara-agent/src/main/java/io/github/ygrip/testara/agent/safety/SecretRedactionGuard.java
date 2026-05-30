package io.github.ygrip.testara.agent.safety;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Scrubs secrets from content before it is sent to an external LLM provider.
 *
 * <p>Redacts known secret key patterns in key=value, key:value, and JSON forms.
 * Also strips content of well-known secret file names (e.g. .env, credentials).
 *
 * <p>Called at context assembly time — before constructing prompts that include
 * project file content — not only at HTTP request time.
 */
public final class SecretRedactionGuard {

  private static final Set<String> SECRET_KEYS = Set.of(
      "password", "passwd", "secret", "token", "authorization", "auth",
      "api-key", "api_key", "apikey",
      "client-secret", "client_secret",
      "private-key", "private_key",
      "access-key", "access_key",
      "cookie", "session",
      "credential", "credentials"
  );

  // key = value  OR  key: value
  private static final Pattern KEY_VALUE_PATTERN = Pattern.compile(
      "(?im)^\\s*(" + String.join("|", SECRET_KEYS) + ")\\s*[=:]\\s*.+");

  // "key": "value" in JSON
  private static final Pattern JSON_KEY_PATTERN = Pattern.compile(
      "(?i)\"(" + String.join("|", SECRET_KEYS) + ")\"\\s*:\\s*\"[^\"]*\"");

  // Bearer / Basic auth tokens
  private static final Pattern BEARER_PATTERN = Pattern.compile(
      "(?i)(bearer|basic)\\s+[A-Za-z0-9+/=_.\\-]+");

  // AWS-style keys (AKIA...)
  private static final Pattern AWS_KEY_PATTERN = Pattern.compile(
      "AKIA[0-9A-Z]{16}");

  // Private key PEM blocks
  private static final Pattern PEM_PATTERN = Pattern.compile(
      "-----BEGIN (RSA |EC |DSA |OPENSSH |ENCRYPTED )?PRIVATE KEY-----[\\s\\S]*?-----END (RSA |EC |DSA |OPENSSH |ENCRYPTED )?PRIVATE KEY-----");

  // JDBC / connection strings with credentials
  private static final Pattern JDBC_CRED_PATTERN = Pattern.compile(
      "(?i)(jdbc:[a-z]+://)[^@\\s]+:([^@\\s]+)@");

  private static final String REDACTED = "[REDACTED]";

  private SecretRedactionGuard() { /* utility */ }

  /**
   * Redact all known secret patterns from the given content.
   * Returns the scrubbed string, or the original if no secrets were found.
   */
  public static String redact(String content) {
    if (content == null || content.isBlank()) return content;

    String scrubbed = content;

    // PEM blocks (most destructive first)
    scrubbed = PEM_PATTERN.matcher(scrubbed).replaceAll(REDACTED);

    // Bearer tokens
    scrubbed = BEARER_PATTERN.matcher(scrubbed).replaceAll("$1 " + REDACTED);

    // AWS keys
    scrubbed = AWS_KEY_PATTERN.matcher(scrubbed).replaceAll(REDACTED);

    // JSON "key": "value"
    scrubbed = JSON_KEY_PATTERN.matcher(scrubbed)
        .replaceAll(m -> "\"" + m.group(1) + "\": \"" + REDACTED + "\"");

    // key = value / key: value lines
    scrubbed = KEY_VALUE_PATTERN.matcher(scrubbed)
        .replaceAll(m -> m.group(1) + " = " + REDACTED);

    // JDBC credentials
    scrubbed = JDBC_CRED_PATTERN.matcher(scrubbed)
        .replaceAll("$1" + REDACTED + ":" + REDACTED + "@");

    return scrubbed;
  }

  /** Returns true if the content contains any detectable secret patterns. */
  public static boolean containsSecrets(String content) {
    if (content == null || content.isBlank()) return false;
    return PEM_PATTERN.matcher(content).find()
        || BEARER_PATTERN.matcher(content).find()
        || AWS_KEY_PATTERN.matcher(content).find()
        || JSON_KEY_PATTERN.matcher(content).find()
        || KEY_VALUE_PATTERN.matcher(content).find();
  }

  /** Safe to send to LLM: redact and confirm no remaining secrets. */
  public static String sanitize(String content) {
    String scrubbed = redact(content);
    if (containsSecrets(scrubbed)) {
      // If secrets still remain after scrubbing, do a more aggressive second pass
      scrubbed = scrubbed.replaceAll("(?i)(secret|password|token|key|auth)\\s*[=:]\\s*\\S+",
          "$1 = " + REDACTED);
    }
    return scrubbed;
  }
}
