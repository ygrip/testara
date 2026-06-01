package io.github.ygrip.testara.agent.skill;

import io.github.ygrip.testara.agent.catalog.PropertyRuleEngine;
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
 * Hard rule: feature-file values must use properties() for URLs, credentials,
 * topic names, DB names, emails, test data, and any environment-specific value.
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
    Map<String, String> props = readProps(projectRoot);
    if (props.isEmpty()) {
      return concise ? "no configuration.properties found" : "No `configuration.properties` found at " + projectRoot;
    }
    // Group by prefix
    Map<String, List<String>> byPrefix = new LinkedHashMap<>();
    props.forEach((k, v) -> {
      String prefix = k.contains(".") ? k.substring(0, k.indexOf('.')) : k;
      byPrefix.computeIfAbsent(prefix, x -> new ArrayList<>()).add(k + "=" + v);
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

    if (concise) {
      return cls == PropertyRuleEngine.Classification.ALLOWED_HARDCODED
          ? "'" + value + "' can be hardcoded (status code or boolean)"
          : "suggestion: " + expr + "\nadd to configuration.properties: " + suggested + "=" + value;
    }
    if (cls == PropertyRuleEngine.Classification.ALLOWED_HARDCODED) {
      return "Value `" + value + "` is a status code or boolean — it may be hardcoded in features.";
    }
    return "**Value:** `" + value + "`  \n"
        + "**Classification:** " + cls + "  \n"
        + "**Use in feature:** `" + expr + "`  \n"
        + "**Add to configuration.properties:**\n```properties\n" + suggested + "=" + value + "\n```\n";
  }

  // ── Generate ──────────────────────────────────────────────────────────────

  private String generateBlock(String slice, String domain, AgentContext context, boolean concise) {
    if (slice == null) slice = "api";
    if (domain == null) domain = "sample";
    String d = domain;
    String s = slice.toLowerCase(Locale.ROOT);
    String block = switch (s) {
      case "api" -> generateApiBlock(d);
      case "ui", "ui-selenium" -> generateUiSeleniumBlock(d);
      case "sql", "database-sql" -> generateSqlBlock(d);
      case "mongo", "database-mongo" -> generateMongoBlock(d);
      case "kafka", "streaming" -> generateKafkaBlock(d);
      default -> "# No template for slice: " + s;
    };
    if (concise) return block;
    return "## " + s.toUpperCase(Locale.ROOT) + " Config Block — " + d + "\n\n```properties\n" + block + "```\n";
  }

  private String generateApiBlock(String domain) {
    return """
        # API service — %s
        api.service.%s-api.host=properties(%s.host)
        api.service.%s-api.basePath=properties(%s.basePath)
        api.service.%s-api.default_specification=%s-api
        spec.api.%s-api.header.Content-Type=application/json
        spec.api.%s-api.header.Accept=application/json

        # Actual values (replace with real environment values)
        %s.host=http://localhost:8080
        %s.basePath=/api/v1
        """.formatted(domain, domain, domain + ".api.host", domain, domain + ".api.basePath",
        domain, domain, domain, domain, domain + ".api", domain + ".api");
  }

  private String generateUiSeleniumBlock(String domain) {
    return """
        # UI engine config
        automation.engine.default-engine=selenium
        automation.engine.active-engines=selenium
        class.loader.default-scan-locations=io.github.ygrip.testara,{basePackage}
        selenium.driver.headless=false
        selenium.driver.page-scan-locations=io.github.ygrip.testara,{basePackage}
        selenium.driver.action-scan-locations=io.github.ygrip.testara,{basePackage}

        # Page URLs — %s
        web.page.desktop.%s.url=properties(app.web.%s-url)
        app.web.%s-url=http://localhost:3000/%s
        """.formatted(domain, domain, domain, domain, domain);
  }

  private String generateSqlBlock(String domain) {
    return """
        # SQL database — %s
        sql.service.%sDb.host-name=properties(db.%s.host)
        sql.service.%sDb.port=5432
        sql.service.%sDb.username=properties(db.%s.username)
        sql.service.%sDb.password=properties(db.%s.password)
        sql.service.%sDb.db-name=properties(db.%s.name)
        sql.service.%sDb.db-type=POSTGRESQL
        sql.service.%sDb.timeout=3

        # Actual values
        db.%s.host=localhost
        db.%s.username=postgres
        db.%s.password=postgres
        db.%s.name=%s
        """.formatted(domain, domain, domain, domain, domain, domain, domain, domain,
        domain, domain, domain, domain, domain, domain, domain, domain, domain);
  }

  private String generateMongoBlock(String domain) {
    return """
        # MongoDB — %s
        mongo.service.%sDb.hosts=properties(db.%s.hosts)
        mongo.service.%sDb.db-name=properties(db.%s.name)
        mongo.service.%sDb.username=properties(db.%s.username)
        mongo.service.%sDb.password=properties(db.%s.password)
        mongo.service.%sDb.ssl-enabled=false

        # Actual values
        db.%s.hosts=localhost:27017
        db.%s.name=%s
        db.%s.username=
        db.%s.password=
        """.formatted(domain, domain, domain, domain, domain, domain, domain, domain, domain,
        domain, domain, domain, domain, domain, domain);
  }

  private String generateKafkaBlock(String domain) {
    return """
        # Kafka — %s
        kafka.service.%sKafka.servers=properties(kafka.%s.servers)
        kafka.service.%sKafka.group-id=properties(kafka.%s.group-id)
        kafka.service.%sKafka.topics.%sEvent=properties(kafka.topic.%s-event)

        # Actual values
        kafka.%s.servers=localhost:9092
        kafka.%s.group-id=testara-%s-test
        kafka.topic.%s-event=%s.event.v1
        """.formatted(domain, domain, domain, domain, domain, domain, domain, domain,
        domain, domain, domain, domain, domain);
  }

  // ── Rules ─────────────────────────────────────────────────────────────────

  private String renderRules(boolean concise) {
    if (concise) {
      return """
          properties() rules:
          MUST: URLs, hosts, ports, emails, passwords, tokens, topic names, DB names, request IDs, test data
          ALLOWED hardcoded: status codes (200/400), booleans (true/false), stable enum labels
          NEVER: hardcode localhost, credentials, or env-specific values directly in feature files
          Usage: properties(key) or prop(key) — resolved from configuration.properties at runtime
          """;
    }
    return """
        ## properties() Usage Rules

        ### Must use properties()
        - URLs and hostnames: `http://localhost:8080` → `properties(api.service.*.host)`
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

  private Map<String, String> readProps(Path root) {
    Map<String, String> props = new LinkedHashMap<>();
    for (String c : List.of("src/test/resources/configuration.properties", "configuration.properties")) {
      Path p = root.resolve(c);
      if (Files.exists(p)) {
        try {
          Files.readAllLines(p, StandardCharsets.UTF_8).stream()
              .filter(l -> !l.isBlank() && !l.startsWith("#") && l.contains("="))
              .forEach(l -> { int i = l.indexOf('='); props.put(l.substring(0, i).trim(), l.substring(i + 1).trim()); });
        } catch (IOException ignored) {}
        break;
      }
    }
    return props;
  }
}
