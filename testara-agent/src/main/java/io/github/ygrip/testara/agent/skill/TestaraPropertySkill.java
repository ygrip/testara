package io.github.ygrip.testara.agent.skill;

import io.github.ygrip.testara.agent.catalog.PropertyRuleEngine;
import io.github.ygrip.testara.agent.safety.SecretRedactionGuard;
import io.github.ygrip.testara.agent.catalog.RuntimeCatalogEntry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Skill: manage Testara property keys — list, suggest, generate, migrate.
 *
 * Hard rule: property-file values must use env placeholders (${ENV:fallback})
 * or concrete local fallbacks, not nested properties(...). Feature/request
 * values can reference application properties with properties(key).
 */
public class TestaraPropertySkill implements AgentSkill<TestaraPropertySkill.Input, String> {

  public record Input(String mode, String domain, String value, String slice) {}

  @Override
  public String name() { return "testara-property"; }

  @Override
  public String execute(Input input, AgentContext context) {
    String mode = input.mode() != null ? input.mode() : "list";
    boolean concise = "concise".equals(context.options().get("format"));
    return switch (mode) {
      case "list"     -> listProperties(context.projectRoot(), concise);
      case "suggest"  -> suggestKey(input.value(), input.domain(), concise);
      case "generate" -> generateBlock(input.slice(), input.domain(), context, concise);
      case "rules"    -> renderRules(concise);
      default         -> listProperties(context.projectRoot(), concise);
    };
  }

  // ── List ──────────────────────────────────────────────────────────────────

  private String listProperties(Path projectRoot, boolean concise) {
    Map<String, String> props;
    try {
      props = readProps(projectRoot);
    } catch (IOException e) {
      return "error: cannot read configuration.properties: " + e.getMessage();
    }
    if (props.isEmpty()) {
      return concise ? "no configuration.properties found" : "No `configuration.properties` found at " + projectRoot;
    }
    // Group by prefix
    Map<String, List<String>> byPrefix = new LinkedHashMap<>();
    props.forEach((k, v) -> {
      String prefix = k.contains(".") ? k.substring(0, k.indexOf('.')) : k;
      // Never echo credentials back into the agent transcript
      byPrefix.computeIfAbsent(prefix, x -> new ArrayList<>()).add(k + "=" + SecretRedactionGuard.redactValue(k, v));
    });

    if (concise) {
      StringBuilder sb = new StringBuilder();
      sb.append(props.size()).append(" properties. prefixes: ");
      sb.append(String.join(", ", byPrefix.keySet())).append("\n");
      byPrefix.forEach((prefix, keys) ->
          sb.append("[").append(prefix).append("] ").append(String.join(", ", keys.stream()
              .map(e -> e.split("=")[0]).collect(Collectors.toList()))).append("\n"));
      return sb.toString();
    }
    StringBuilder sb = new StringBuilder();
    sb.append("# Project Properties\n\n**Total:** ").append(props.size()).append(" keys\n\n");
    byPrefix.forEach((prefix, entries) -> {
      sb.append("## ").append(prefix).append("\n\n```properties\n");
      entries.forEach(e -> sb.append(e).append("\n"));
      sb.append("```\n\n");
    });
    return sb.toString();
  }

  // ── Suggest ───────────────────────────────────────────────────────────────

  private String suggestKey(String value, String domain, boolean concise) {
    if (value == null || value.isBlank()) {
      return concise ? "provide --value to get a suggestion"
          : "Provide a `--value` to get a property key suggestion.";
    }
    PropertyRuleEngine.Classification cls = PropertyRuleEngine.classify(value);
    String suggested = PropertyRuleEngine.suggestKey(value, domain != null ? domain : "app", null);
    String expr = "properties(" + suggested + ")";
    String env = PropertyKeys.toEnvKey(suggested);

    if (concise) {
      return cls == PropertyRuleEngine.Classification.ALLOWED_HARDCODED
          ? "'" + value + "' can be hardcoded (status code or boolean)"
          : "feature/request value: " + expr + "\nadd to src/test/resources/application.properties: " + suggested + "=${" + env + ":" + value + "}";
    }
    if (cls == PropertyRuleEngine.Classification.ALLOWED_HARDCODED) {
      return "Value `" + value + "` is a status code or boolean — it may be hardcoded in features.";
    }
    return "**Value:** `" + value + "`  \n"
        + "**Classification:** " + cls + "  \n"
        + "**Use in feature/request spec:** `" + expr + "`  \n"
        + "**Add to `src/test/resources/application.properties`:**\n```properties\n" + suggested + "=${" + env + ":" + value + "}\n```\n";
  }

  // ── Generate ──────────────────────────────────────────────────────────────

  private String generateBlock(String slice, String domain, AgentContext context, boolean concise) {
    if (slice == null) slice = "api";
    if (domain == null) domain = "sample";
    String d = domain;
    String s = slice.toLowerCase(Locale.ROOT);
    String block = switch (s) {
      case "api" -> generateApiBlock(d);
      case "ui", "ui-selenium" -> generateUiSeleniumBlock(d, basePackage(context));
      case "sql", "database-sql" -> TestaraDbSkill.sqlConfigBlock(d) + "\n";
      case "mongo", "database-mongo" -> TestaraDbSkill.mongoConfigBlock(d) + "\n";
      case "kafka", "streaming" -> TestaraDbSkill.kafkaConfigBlock(d) + "\n";
      default -> "# No template for slice: " + s;
    };
    if (concise) return block;
    return "## " + s.toUpperCase(Locale.ROOT) + " Config Block — " + d + "\n\n```properties\n" + block + "```\n";
  }

  private String generateApiBlock(String domain) {
    return TestaraApiSkill.serviceConfigBlock(domain) + """

        # Application values referenced from features/request specs
        %s=/%s/{id}
        """.formatted(PropertyKeys.apiEndpoint(domain), domain);
  }

  private String basePackage(AgentContext context) {
    String explicit = context.options().get("package");
    if (explicit != null && !explicit.isBlank()) return explicit;
    return PackageInference.inferBasePackage(context.projectRoot()).orElse("io.github.ygrip.automation");
  }

  private String generateUiSeleniumBlock(String domain, String basePackage) {
    String key = PropertyKeys.toPropertyKey(domain);
    return """
        # UI engine config
        automation.engine.default-engine=selenium
        automation.engine.active-engines=selenium
        class.loader.default-scan-locations=io.github.ygrip.testara,%s
        selenium.driver.headless=false
        selenium.driver.page-scan-locations=io.github.ygrip.testara,%s
        selenium.driver.action-scan-locations=io.github.ygrip.testara,%s

        # Page URLs — %s
        %s=${APP_WEB_%s_URL:http://localhost:3000/%s}
        """.formatted(basePackage, basePackage, basePackage, domain, PropertyKeys.pageUrl("desktop", key),
        PropertyKeys.toEnvKey(key), key);
  }

  // ── Rules ─────────────────────────────────────────────────────────────────

  private String renderRules(boolean concise) {
    if (concise) {
      return """
          properties() rules:
          MUST: URLs, hosts, ports, emails, passwords, tokens, topic names, DB names, request IDs, test data
          ALLOWED hardcoded: status codes (200/400), booleans (true/false), stable enum labels
          NEVER: hardcode localhost, credentials, or env-specific values directly in feature files
          Property files: use ${ENV:fallback}; do not set a property value to properties(other.key)
          Feature/request values: use properties(key) for application.properties values, or uuid()/random()/oneOf() for dynamic data
          """;
    }
    return """
        ## properties() Usage Rules

        ### Property-file values
        - Use env placeholders: `api.service.orders-api.host=${ORDERS_API_HOST:http://localhost:8080}`
        - Do not nest indirection: avoid `api.service.orders-api.host=properties(orders.api.host)`

        ### Feature/request values
        - URLs, credentials, topics, DB names, and reusable test data can use `properties(key)` when the key lives in `application.properties`
        - Dynamic data should use commands: `uuid()`, `random(6,NUMERIC)`, `oneOf(a,b,c)`
        - Credentials: passwords, tokens, secrets, API keys
        - Topic names: `payment.event.v1` → `properties(kafka.topic.payment-event)`
        - DB names and hosts
        - Email addresses and user data
        - Request IDs, correlation IDs, reusable test constants

        ### Allowed hardcoded
        - HTTP status codes: `200`, `400`, `404`
        - Boolean assertions: `true`, `false`
        - Stable enum labels: `APPROVED`, `PENDING`
        - Non-secret local defaults in sample projects

        ### Usage
        ```gherkin
        Given [api] prepare header "Authorization" with value "properties(test.user.token)"
        When user enter value "properties(test.user.email)" on "emailInput"
        Given [sql] prepare query with value "select * from orders where id = 'properties(test.order-id)'"
        ```
        """;
  }

  private Map<String, String> readProps(Path root) throws IOException {
    Map<String, String> props = new LinkedHashMap<>();
    for (String c : List.of("src/test/resources/configuration.properties", "configuration.properties")) {
      Path p = root.resolve(c);
      if (Files.exists(p)) {
        Files.readAllLines(p, StandardCharsets.UTF_8).stream()
            .filter(l -> !l.isBlank() && !l.startsWith("#") && l.contains("="))
            .forEach(l -> { int i = l.indexOf('='); props.put(l.substring(0, i).trim(), l.substring(i + 1).trim()); });
        break;
      }
    }
    return props;
  }
}
