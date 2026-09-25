package io.github.ygrip.testara.agent.skill;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Normalizes the capability choices accepted by the init surfaces. */
final class InitCapabilities {
  private static final Set<String> SUPPORTED = Set.of("api", "ui", "sql", "mongo", "kafka", "elastic", "fullstack");

  private InitCapabilities() {}

  /**
   * Union of the legacy {@code type} and the requested slices. {@code api} is the surfaces' default
   * type, so it only applies when no slices were requested; any other explicit type is kept
   * ({@code type=ui, slices=[sql]} is a UI project with SQL, not an API project).
   */
  static List<String> normalize(String legacyType, List<String> requested) {
    LinkedHashSet<String> values = new LinkedHashSet<>();
    // MCP/CLI pass null when no type was given, so an explicit type (including api) is always kept.
    if (legacyType != null && !legacyType.isBlank()) {
      values.add(canonical(legacyType));
    }
    if (requested != null) {
      requested.stream().filter(value -> value != null && !value.isBlank())
          .flatMap(value -> List.of(value.split(",")).stream())
          .map(value -> canonical(value.trim())).filter(SUPPORTED::contains).forEach(values::add);
    }
    if (values.isEmpty()) values.add("api");
    if (values.contains("fullstack")) {
      values.remove("fullstack"); values.add("api"); values.add("ui");
    }
    values.removeIf(value -> !SUPPORTED.contains(value));
    if (values.isEmpty()) values.add("api");
    return List.copyOf(values);
  }

  static String baseType(List<String> capabilities) {
    boolean api = capabilities.contains("api");
    boolean ui = capabilities.contains("ui");
    return api && ui ? "fullstack" : ui ? "ui" : "api";
  }

  static String contentType(String legacyType, List<String> capabilities) {
    if (legacyType != null && !legacyType.isBlank() && (capabilities == null || capabilities.isEmpty())) {
      return canonical(legacyType);
    }
    return baseType(normalize(legacyType, capabilities));
  }

  /** True for a content type the scaffold knows how to generate; anything else must be rejected. */
  static boolean isSupported(String type) {
    return SUPPORTED.contains(type);
  }

  private static String canonical(String value) {
    return switch (value.toLowerCase(Locale.ROOT)) {
      case "all", "full", "fullstack" -> "fullstack";
      case "database", "db", "postgres", "mysql" -> "sql";
      case "mongodb" -> "mongo";
      case "streaming" -> "kafka";
      case "elasticsearch", "elastic-search" -> "elastic";
      default -> value.toLowerCase(Locale.ROOT);
    };
  }
}
