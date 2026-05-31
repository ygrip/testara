package io.github.ygrip.testara.agent.catalog;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Classifies step/spec values and converts env-specific ones to properties() expressions.
 *
 * Rules (from testara-agent-runtime-knowledge-pack-plan):
 *  MUST use properties() — URLs, hosts, ports, credentials, topics, DB names, emails,
 *                          reusable test data, tokens, request IDs
 *  ALLOWED hardcoded     — HTTP status codes, boolean assertions, stable enums, short labels
 */
public final class PropertyRuleEngine {

  private static final Pattern URL_LIKE     = Pattern.compile("https?://.*|localhost.*|.*:\\d{3,5}(/.*)?");
  private static final Pattern EMAIL_LIKE   = Pattern.compile("[\\w.+-]+@[\\w-]+\\.[a-z]{2,}");
  private static final Pattern PORT_LIKE    = Pattern.compile("\\d{4,5}");
  private static final Pattern STATUS_CODE  = Pattern.compile("[1-5]\\d{2}");
  private static final Pattern UUID_LIKE    = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", Pattern.CASE_INSENSITIVE);

  private PropertyRuleEngine() {}

  public enum Classification { MUST_USE_PROPERTIES, ALLOWED_HARDCODED, NEUTRAL }

  /** Classify a value to determine whether it should use properties(). */
  public static Classification classify(String value) {
    if (value == null || value.isBlank()) return Classification.NEUTRAL;
    String v = value.trim();

    // Hardcoded-allowed: status codes, booleans, short enum-like tokens
    if (STATUS_CODE.matcher(v).matches()) return Classification.ALLOWED_HARDCODED;
    if (v.equals("true") || v.equals("false")) return Classification.ALLOWED_HARDCODED;
    if (v.length() <= 3 && v.matches("[A-Z0-9_]+")) return Classification.ALLOWED_HARDCODED;

    // Already uses properties() or commands — no change needed
    if (v.startsWith("properties(") || v.startsWith("prop(")
        || v.contains("uuid()") || v.contains("timestamp()")) return Classification.NEUTRAL;

    // Must use properties()
    if (URL_LIKE.matcher(v).matches()) return Classification.MUST_USE_PROPERTIES;
    if (EMAIL_LIKE.matcher(v).matches()) return Classification.MUST_USE_PROPERTIES;
    if (UUID_LIKE.matcher(v).matches()) return Classification.MUST_USE_PROPERTIES;

    String lower = v.toLowerCase(Locale.ROOT);
    if (lower.contains("password") || lower.contains("token") || lower.contains("secret")
        || lower.contains("credential") || lower.contains("api-key") || lower.contains("apikey"))
      return Classification.MUST_USE_PROPERTIES;
    if (lower.contains("topic") || lower.contains("kafka") || lower.contains("queue"))
      return Classification.MUST_USE_PROPERTIES;
    if (lower.contains("localhost") || lower.contains("127.0.0.1"))
      return Classification.MUST_USE_PROPERTIES;

    return Classification.NEUTRAL;
  }

  /**
   * Convert an env-specific value to a properties() expression using a suggested key.
   * If the value is already OK, returns it unchanged.
   */
  public static String toPropertiesExpr(String value, String domain, String fieldHint) {
    if (value == null) return value;
    Classification cls = classify(value);
    if (cls != Classification.MUST_USE_PROPERTIES) return value;
    String key = suggestKey(value, domain, fieldHint);
    return "properties(" + key + ")";
  }

  /** Suggest a property key for a given value and context. */
  public static String suggestKey(String value, String domain, String fieldHint) {
    if (value == null) return "test." + domain + "." + sanitize(fieldHint);
    String v = value.trim().toLowerCase(Locale.ROOT);

    if (URL_LIKE.matcher(value).matches()) {
      if (v.contains("localhost") || v.contains("127.0.0.1"))
        return "api.service." + domain + "-api.host";
      return "app." + domain + ".url";
    }
    if (EMAIL_LIKE.matcher(value).matches()) return "test." + domain + ".email";
    if (UUID_LIKE.matcher(value).matches()) return "test." + domain + ".id";
    if (v.contains("topic")) return "kafka.topic." + domain + "-event";
    if (v.contains("password")) return "test." + domain + ".password";
    if (v.contains("token") || v.contains("secret")) return "test." + domain + ".token";

    if (fieldHint != null && !fieldHint.isBlank())
      return "test." + sanitize(domain) + "." + sanitize(fieldHint);
    return "test." + sanitize(domain) + ".value";
  }

  private static String sanitize(String s) {
    return s == null ? "value" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
  }
}
