package io.github.ygrip.testara.agent.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses an optional {@code testara-agent.yaml} file at the project root.
 *
 * <p>Simple line-based YAML parser that avoids heavy dependencies.
 * Supports scalar keys, list values, and nested maps up to 2 levels deep.
 *
 * <p>Config priority: CLI flags > env vars > testara-agent.yaml > properties > defaults.
 */
public final class AgentYamlConfig {

  private static final Logger LOG = Logger.getLogger(AgentYamlConfig.class.getName());
  private static final String CONFIG_FILE = "testara-agent.yaml";

  // Top-level sections
  private static final Pattern TOP_KEY = Pattern.compile("^(\\w+)\\s*:\\s*$");
  // Nested key: value
  private static final Pattern NESTED_KV = Pattern.compile("^\\s{2,}(\\w[\\w-]*)\\s*:\\s*(.+)$");
  // List item
  private static final Pattern LIST_ITEM = Pattern.compile("^\\s{4,}-\\s+\"([^\"]+)\"$|^\\s{4,}-\\s+([^-].*)$");

  public record AgentConfig(
      Map<String, String> general,
      Map<String, String> run,
      Map<String, String> write,
      Map<String, String> llm,
      Map<String, List<String>> tagAliases,
      List<String> featureRoots,
      List<String> requestSpecRoots,
      List<String> validationRoots
  ) {
    public static AgentConfig empty() {
      return new AgentConfig(Map.of(), Map.of(), Map.of(), Map.of(),
          Map.of(), List.of(), List.of(), List.of());
    }

    /** Apply config overrides to a mutable options map. */
    public void apply(Map<String, String> opts) {
      general.forEach((k, v) -> opts.putIfAbsent("agent." + k, v));
      run.forEach((k, v) -> opts.putIfAbsent("run." + k, v));
      llm.forEach((k, v) -> opts.putIfAbsent("llm." + k, v));
      tagAliases.forEach((alias, tags) -> {
        if (!tags.isEmpty()) {
          opts.putIfAbsent("tag-alias." + alias, String.join(",", tags));
        }
      });
    }
  }

  /** Load config from the project root, or return empty if not found. */
  public static AgentConfig load(Path projectRoot) {
    Path configFile = projectRoot.resolve(CONFIG_FILE);
    if (!Files.exists(configFile)) {
      LOG.fine("No " + CONFIG_FILE + " found at " + projectRoot);
      return AgentConfig.empty();
    }
    try {
      return parse(Files.readString(configFile, StandardCharsets.UTF_8));
    } catch (IOException e) {
      LOG.warning("Cannot read " + CONFIG_FILE + ": " + e.getMessage());
      return AgentConfig.empty();
    }
  }

  @SuppressWarnings("java:S3776")
  static AgentConfig parse(String yaml) {
    Map<String, String> general = new LinkedHashMap<>();
    Map<String, String> run = new LinkedHashMap<>();
    Map<String, String> write = new LinkedHashMap<>();
    Map<String, String> llm = new LinkedHashMap<>();
    Map<String, List<String>> tagAliases = new LinkedHashMap<>();
    List<String> featureRoots = new ArrayList<>();
    List<String> requestSpecRoots = new ArrayList<>();
    List<String> validationRoots = new ArrayList<>();

    String section = null;
    String subSection = null;
    boolean inList = false;
    String listKey = null;
    List<String> currentList = new ArrayList<>();

    for (String line : yaml.split("\n")) {
      String stripped = line.strip();
      if (stripped.isEmpty() || stripped.startsWith("#")) continue;

      // Top-level section
      Matcher topKey = TOP_KEY.matcher(stripped);
      if (topKey.matches() && !stripped.startsWith(" ")) {
        flushList(listKey, currentList, tagAliases, featureRoots,
            requestSpecRoots, validationRoots);
        section = topKey.group(1);
        subSection = null;
        inList = false;
        listKey = null;
        currentList = new ArrayList<>();
        continue;
      }

      // Nested key: value
      Matcher nested = NESTED_KV.matcher(stripped);
      if (nested.matches()) {
        String key = nested.group(1);
        String value = nested.group(2).strip();
        if (value.endsWith("\"")) value = value.substring(1, value.length() - 1);

        if ("project".equals(section)) {
          if ("featureRoots".equals(key)) { inList = true; listKey = "featureRoots"; continue; }
          if ("requestSpecRoots".equals(key)) { inList = true; listKey = "requestSpecRoots"; continue; }
          if ("validationRoots".equals(key)) { inList = true; listKey = "validationRoots"; continue; }
        }
        if ("tagAliases".equals(section)) {
          inList = true;
          listKey = key;
          continue;
        }

        Map<String, String> target = sectionMap(section, general, run, write, llm);
        if (target != null) target.put(key, value);
        continue;
      }

      // List items
      Matcher item = LIST_ITEM.matcher(line);
      if (item.matches() && inList && listKey != null) {
        String val = item.group(1) != null ? item.group(1) : item.group(2);
        if (val != null) currentList.add(val.strip().replaceAll("^\"|\"$", ""));
        continue;
      }

      // End of list
      if (!stripped.startsWith("  -") && inList) {
        flushList(listKey, currentList, tagAliases, featureRoots,
            requestSpecRoots, validationRoots);
        inList = false;
        listKey = null;
        currentList = new ArrayList<>();
      }
    }

    flushList(listKey, currentList, tagAliases, featureRoots,
        requestSpecRoots, validationRoots);

    return new AgentConfig(general, run, write, llm, tagAliases,
        List.copyOf(featureRoots), List.copyOf(requestSpecRoots),
        List.copyOf(validationRoots));
  }

  private static Map<String, String> sectionMap(String section,
      Map<String, String> general, Map<String, String> run,
      Map<String, String> write, Map<String, String> llm) {
    return switch (section) {
      case "run" -> run;
      case "write" -> write;
      case "llm" -> llm;
      default -> general;
    };
  }

  private static void flushList(String key, List<String> values,
      Map<String, List<String>> tagAliases, List<String> featureRoots,
      List<String> requestSpecRoots, List<String> validationRoots) {
    if (key == null || values.isEmpty()) return;
    switch (key) {
      case "featureRoots" -> featureRoots.addAll(values);
      case "requestSpecRoots" -> requestSpecRoots.addAll(values);
      case "validationRoots" -> validationRoots.addAll(values);
      default -> tagAliases.put(key, List.copyOf(values));
    }
    values.clear();
  }
}
