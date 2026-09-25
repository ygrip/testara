package io.github.ygrip.testara.agent.skill;

import java.util.Locale;

/**
 * Single source of the property keys and service aliases generated artifacts share, so a feature,
 * its request spec and the properties written for it always agree on the same names.
 */
final class PropertyKeys {

  private PropertyKeys() {}

  /** Service alias used by {@code using service with alias} and {@code api.service.<alias>.*}. */
  static String apiAlias(String domain) {
    return domain + "-api";
  }

  /** Application property holding the endpoint path a request spec calls. */
  static String apiEndpoint(String domain) {
    return domain + ".api.endpoint";
  }

  /** Application property holding reusable test data for a domain. */
  static String testData(String domain, String field) {
    return "test." + domain + "." + field;
  }

  /** {@code sql.service.<alias>} / {@code mongo.service.<alias>} alias. */
  static String databaseAlias(String name) {
    return name + "Db";
  }

  /** {@code kafka.service.<alias>} alias. */
  static String kafkaAlias(String name) {
    return name + "Kafka";
  }

  /** Topic alias under {@code kafka.service.<alias>.topics.<topicAlias>}; Kafka steps accept the alias. */
  static String kafkaTopicAlias(String name) {
    return name + "Event";
  }

  /** {@code web.page.<device>.<page>.url} — the page URL {@code user open "<page>" page} navigates to. */
  static String pageUrl(String device, String page) {
    return "web.page." + device + "." + page + ".url";
  }

  /** Wraps a fallback in an environment placeholder named after the key, e.g. {@code ${TEST_USER_ID:x}}. */
  static String envValue(String key, String fallback) {
    return "${" + toEnvKey(key) + ":" + fallback + "}";
  }

  static String toPropertyKey(String value) {
    return value.toLowerCase(Locale.ROOT)
        .replaceAll("[^a-z0-9]+", "-")
        .replaceAll("^-|-$", "");
  }

  static String toEnvKey(String value) {
    return value.toUpperCase(Locale.ROOT)
        .replaceAll("[^A-Z0-9]+", "_")
        .replaceAll("^_|_$", "");
  }
}
