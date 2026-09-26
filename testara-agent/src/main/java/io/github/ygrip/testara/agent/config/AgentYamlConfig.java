package io.github.ygrip.testara.agent.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;

/**
 * Parses an optional {@code testara-agent.yaml} file at the project root.
 *
 * <p>Parsed with Jackson YAML into a tree; only scalar values of the known sections and string
 * lists (or a single scalar) for list keys are read.
 *
 * <p>Config priority: CLI flags > env vars > testara-agent.yaml > properties > defaults.
 * {@code write.enabled} (or a scalar {@code write}) may only <em>disable</em> writes: enabling them
 * requires an explicit tool argument or CLI flag, and {@code TESTARA_AGENT_WRITE_ENABLED=false}
 * always wins.
 */
public final class AgentYamlConfig {

  private static final Logger LOG = Logger.getLogger(AgentYamlConfig.class.getName());
  private static final String CONFIG_FILE = "testara-agent.yaml";
  /** Options that grant writes; a checked-in file must never turn them on (per-call args only). */
  private static final Set<String> CALL_ONLY_KEYS = Set.of("write", "overwrite", "createFiles");
  private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

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
      putSafely(general, opts);
      putSafely(run, opts);
      write.forEach((k, v) -> {
        if (!"enabled".equals(k)) {
          putSafely(Map.of(k, v), opts);
        } else if ("false".equalsIgnoreCase(v.strip())) {
          // A checked-in file may switch writes off, never on (no silent APPLY mode).
          opts.put("write", "false");
        }
      });
      llm.forEach((k, v) -> opts.putIfAbsent("llm." + k, v));
      tagAliases.forEach((alias, tags) -> {
        if (!tags.isEmpty()) {
          String expression = tags.size() == 1 ? tags.get(0) : "(" + String.join(" or ", tags) + ")";
          opts.putIfAbsent("tag-alias." + alias, expression);
        }
      });
    }

    /** Copies settings except the ones only an explicit call may set (see {@link #CALL_ONLY_KEYS}). */
    private static void putSafely(Map<String, String> source, Map<String, String> opts) {
      source.forEach((k, v) -> {
        if (CALL_ONLY_KEYS.contains(k)) {
          LOG.warning("Ignoring '" + k + "' in " + CONFIG_FILE + ": it can only be set per call");
          return;
        }
        opts.putIfAbsent(k, v);
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

  static AgentConfig parse(String yaml) {
    JsonNode root;
    try {
      root = YAML.readTree(yaml);
    } catch (JsonProcessingException e) {
      LOG.warning("Ignoring invalid " + CONFIG_FILE + ": " + e.getOriginalMessage());
      return AgentConfig.empty();
    }
    if (root == null || root.isMissingNode() || root.isNull()) {
      LOG.warning("Ignoring empty " + CONFIG_FILE);
      return AgentConfig.empty();
    }
    if (!root.isObject()) {
      LOG.warning("Ignoring " + CONFIG_FILE + ": expected a mapping at the top level");
      return AgentConfig.empty();
    }

    Map<String, String> general = new LinkedHashMap<>();
    Map<String, String> run = new LinkedHashMap<>();
    Map<String, String> write = new LinkedHashMap<>();
    Map<String, String> llm = new LinkedHashMap<>();
    Map<String, List<String>> tagAliases = new LinkedHashMap<>();
    List<String> featureRoots = new ArrayList<>();
    List<String> requestSpecRoots = new ArrayList<>();
    List<String> validationRoots = new ArrayList<>();

    root.properties().forEach(section -> {
      String name = section.getKey();
      JsonNode value = section.getValue();
      if (value.isValueNode() && "write".equals(name)) {
        // Scalar "write: false" is the same off switch as "write.enabled: false"; "true" never enables
        putScalar(write, "enabled", value);
        return;
      }
      if (value.isValueNode()) {
        putScalar(general, name, value);
        return;
      }
      switch (name) {
        case "run" -> putScalars(run, value);
        case "write" -> putScalars(write, value);
        case "llm" -> putScalars(llm, value);
        case "tagAliases" -> value.properties().forEach(alias ->
            tagAliases.put(alias.getKey(), stringList(alias.getValue())));
        case "project" -> {
          featureRoots.addAll(stringList(value.path("featureRoots")));
          requestSpecRoots.addAll(stringList(value.path("requestSpecRoots")));
          validationRoots.addAll(stringList(value.path("validationRoots")));
          putScalars(general, value);
        }
        default -> putScalars(general, value);
      }
    });

    return new AgentConfig(general, run, write, llm, tagAliases,
        List.copyOf(featureRoots), List.copyOf(requestSpecRoots),
        List.copyOf(validationRoots));
  }

  private static void putScalars(Map<String, String> target, JsonNode section) {
    section.properties().forEach(entry -> putScalar(target, entry.getKey(), entry.getValue()));
  }

  private static void putScalar(Map<String, String> target, String key, JsonNode value) {
    if (value.isNull()) return;
    if (value.isValueNode()) {
      target.put(key, value.asText());
    } else {
      LOG.fine("Ignoring non-scalar " + CONFIG_FILE + " key '" + key + "'");
    }
  }

  /** A YAML list of scalars, or a single scalar treated as a one-element list. */
  private static List<String> stringList(JsonNode node) {
    if (node.isArray()) {
      List<String> values = new ArrayList<>();
      node.forEach(item -> {
        if (item.isValueNode() && !item.isNull() && !item.asText().isBlank()) values.add(item.asText());
      });
      return List.copyOf(values);
    }
    if (node.isValueNode() && !node.isNull() && !node.asText().isBlank()) return List.of(node.asText());
    return List.of();
  }
}
