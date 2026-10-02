package io.github.ygrip.testara.agent.skill;

import java.util.Locale;

/**
 * Skill: explain and generate DB (SQL/Mongo) and Kafka config and feature templates.
 *
 * Config values use ${ENV:fallback}; feature/request values can reference
 * application properties with properties(key).
 */
public class TestaraDbSkill implements AgentSkill<TestaraDbSkill.Input, String> {

  public record Input(String slice, String mode, String name) {}

  @Override
  public String name() { return "testara-db"; }

  @Override
  public String execute(Input input, AgentContext context) {
    String slice = input.slice() != null ? input.slice().toLowerCase(Locale.ROOT) : "sql";
    String mode  = input.mode()  != null ? input.mode() : "explain";
    String name  = input.name()  != null ? input.name() : "sample";
    boolean concise = "concise".equals(context.options().get("format"));

    return switch (slice) {
      case "sql", "database-sql" -> switch (mode) {
        case "config"  -> sqlConfig(name, concise);
        case "feature" -> sqlFeature(name, concise);
        default        -> sqlExplain(concise);
      };
      case "mongo", "database-mongo" -> switch (mode) {
        case "config"  -> mongoConfig(name, concise);
        case "feature" -> mongoFeature(name, concise);
        default        -> mongoExplain(concise);
      };
      case "kafka", "streaming" -> switch (mode) {
        case "config"  -> kafkaConfig(name, concise);
        case "feature" -> kafkaFeature(name, concise);
        default        -> kafkaExplain(concise);
      };
      case "elastic", "elastic-search" -> switch (mode) {
        case "config"  -> elasticConfig(name, concise);
        case "feature" -> elasticFeature(name, concise);
        default        -> elasticExplain(concise);
      };
      default -> sqlExplain(concise) + "\n" + mongoExplain(concise) + "\n" + kafkaExplain(concise) + "\n" + elasticExplain(concise);
    };
  }

  // ── SQL ───────────────────────────────────────────────────────────────────

  private String sqlExplain(boolean concise) {
    if (concise) return "sql steps: [sql] connect to database with name {name} | [sql] prepare query with value : (multiline) | [sql] execute database query | [sql] assign previous database response to {alias}. Config: sql.service.{name}.* uses ${ENV:fallback}; feature values use properties() or dynamic commands.";
    return """
        ## SQL Guide

        Config:
        ```properties
        sql.service.{name}.host-name=${DB_NAME_HOST:localhost}
        sql.service.{name}.db-name=${DB_NAME_NAME:testdb}
        sql.service.{name}.username=${DB_NAME_USERNAME:postgres}
        sql.service.{name}.password=${DB_NAME_PASSWORD:postgres}
        sql.service.{name}.db-type=POSTGRESQL
        ```

        Feature:
        ```gherkin
        Given [sql] connect to database with name {name}Db
        Given [sql] prepare query with value :
          \"\"\"
          select * from {table} where id = 'properties(test.{domain}.id)'
          \"\"\"
        When [sql] execute database query
        Then [sql] assign previous database response to {alias}Rows
        ```
        """;
  }

  private String sqlConfig(String name, boolean concise) {
    String block = sqlConfigBlock(name);
    return concise ? block : "```properties\n" + block + "```";
  }

  /** {@code sql.service.<name>Db.*} for {@code [sql] connect to database with name <name>Db}. */
  static String sqlConfigBlock(String name) {
    String alias = PropertyKeys.databaseAlias(name);
    String env = PropertyKeys.toEnvKey(name);
    return """
        # SQL database — %s
        sql.service.%s.host-name=${DB_%s_HOST:localhost}
        sql.service.%s.port=5432
        sql.service.%s.username=${DB_%s_USERNAME:postgres}
        sql.service.%s.password=${DB_%s_PASSWORD:postgres}
        sql.service.%s.db-name=${DB_%s_NAME:%s}
        sql.service.%s.db-type=POSTGRESQL
        sql.service.%s.timeout=3
        """.formatted(name, alias, env, alias, alias, env, alias, env,
        alias, env, PropertyKeys.toPropertyKey(name), alias, alias);
  }

  private String sqlFeature(String name, boolean concise) {
    String feature = """
        Given [sql] connect to database with name %s
        Given [sql] prepare query with value :
          \"\"\"
          select *
          from %s
          where id = 'properties(%s)'
          \"\"\"
        When [sql] execute database query
        Then [sql] assign previous database response to %sRows
        """.formatted(PropertyKeys.databaseAlias(name), name, PropertyKeys.testData(name, "id"), name);
    return concise ? feature : "```gherkin\n" + feature + "```";
  }

  // ── Mongo ─────────────────────────────────────────────────────────────────

  private String mongoExplain(boolean concise) {
    if (concise) return "mongo steps: [mongo] connect to database with name {name} | [mongo] select collection with name {col} | [mongo] select data with query : (|key|value| table — keys: query/sort/project/limit/skip) | [mongo] assign previous database response to {alias}. Config: mongo.service.{name}.* uses ${ENV:fallback}. IMPORTANT: query DataTable MUST use |key|value| headers.";
    return """
        ## MongoDB Guide

        Config:
        ```properties
        mongo.service.{name}.hosts=${MONGO_NAME_HOSTS:localhost:27017}
        mongo.service.{name}.db-name=${MONGO_NAME_DB:testdb}
        mongo.service.{name}.username=${MONGO_NAME_USERNAME:}
        mongo.service.{name}.password=${MONGO_NAME_PASSWORD:}
        ```

        DataTable format for query steps — MUST use |key|value| headers:
        Supported keys: query, sort, project, limit, skip (select)
                        query, useMany (delete/count)
                        query, update, useMany (update)
                        field, query (distinct)

        Feature:
        ```gherkin
        Given [mongo] connect to database with name {name}Db
        Given [mongo] select collection with name {collection}
        When [mongo] select data with query :
          | key    | value                                    |
          | query  | {"sku": "properties(test.{domain}.sku)"} |
          | limit  | 1                                        |
        Then [mongo] assign previous database response to {alias}Rows
        ```
        """;
  }

  private String mongoConfig(String name, boolean concise) {
    String block = mongoConfigBlock(name);
    return concise ? block : "```properties\n" + block + "```";
  }

  /** {@code mongo.service.<name>Db.*} for {@code [mongo] connect to database with name <name>Db}. */
  static String mongoConfigBlock(String name) {
    String alias = PropertyKeys.databaseAlias(name);
    String env = PropertyKeys.toEnvKey(name);
    return """
        # MongoDB — %s
        mongo.service.%s.hosts=${MONGO_%s_HOSTS:localhost:27017}
        mongo.service.%s.db-name=${MONGO_%s_NAME:%s}
        mongo.service.%s.username=${MONGO_%s_USERNAME:}
        mongo.service.%s.password=${MONGO_%s_PASSWORD:}
        mongo.service.%s.ssl-enabled=false
        """.formatted(name, alias, env, alias, env, PropertyKeys.toPropertyKey(name),
        alias, env, alias, env, alias);
  }

  private String mongoFeature(String name, boolean concise) {
    String feature = """
        Given [mongo] connect to database with name %s
        Given [mongo] select collection with name %s
        When [mongo] select data with query :
          | key    | value                              |
          | query  | {"_id": "properties(%s)"} |
          | limit  | 1                                  |
        Then [mongo] assign previous database response to %sRows
        """.formatted(PropertyKeys.databaseAlias(name), name, PropertyKeys.testData(name, "id"), name);
    return concise ? feature : "```gherkin\n" + feature + "```";
  }

  // ── Kafka ─────────────────────────────────────────────────────────────────

  private String kafkaExplain(boolean concise) {
    if (concise) return "kafka steps: [kafka] start kafka producer for {name} | [kafka] send kafka message to topic \"{topicAlias}\" with key \"uuid()\" or properties(test.*.id) and data \"request($['event'])\" | [kafka] stop kafka producer. Topic alias = key under kafka.service.{name}.topics.{topicAlias} (a raw topic name also works). Config uses ${ENV:fallback}.";
    return """
        ## Kafka Guide

        Config:
        ```properties
        kafka.service.{name}.servers=${KAFKA_NAME_SERVERS:localhost:9092}
        kafka.service.{name}.group-id=${KAFKA_NAME_GROUP_ID:testara-name-test}
        kafka.service.{name}.topics.{alias}=${KAFKA_TOPIC_ALIAS:alias.event.v1}
        ```

        Producer feature:
        ```gherkin
        Given [kafka] start kafka producer for {name}
        When [kafka] send kafka message to topic "{alias}" with key "properties(test.{domain}.id)" and data "properties(test.{domain}.payload)"
        Then [kafka] stop kafka producer
        ```

        Consumer feature:
        ```gherkin
        Given [kafka] start kafka consumer for {name}
        Given [kafka] listen kafka from topic {alias}
        When [kafka] assign 5 latest records from topic "{alias}" to {domain}Events
        Then [kafka] stop kafka consumer
        ```
        """;
  }

  private String kafkaConfig(String name, boolean concise) {
    String block = kafkaConfigBlock(name);
    return concise ? block : "```properties\n" + block + "```";
  }

  /** {@code kafka.service.<name>Kafka.*} with topic alias {@code <name>Event}, which Kafka steps resolve. */
  static String kafkaConfigBlock(String name) {
    String alias = PropertyKeys.kafkaAlias(name);
    String env = PropertyKeys.toEnvKey(name);
    return """
        # Kafka — %s
        kafka.service.%s.servers=${KAFKA_%s_SERVERS:localhost:9092}
        kafka.service.%s.group-id=${KAFKA_%s_GROUP_ID:testara-%s-test}
        kafka.service.%s.topics.%s=${KAFKA_TOPIC_%s_EVENT:%s.event.v1}
        """.formatted(name, alias, env, alias, env, PropertyKeys.toPropertyKey(name),
        alias, PropertyKeys.kafkaTopicAlias(name), env, PropertyKeys.toPropertyKey(name));
  }

  private String kafkaFeature(String name, boolean concise) {
    String feature = """
        # Producer
        Given [kafka] start kafka producer for %s
        When [kafka] send kafka message to topic "%s" with key "properties(%s)" and data "properties(%s)"
        Then [kafka] stop kafka producer
        """.formatted(PropertyKeys.kafkaAlias(name), PropertyKeys.kafkaTopicAlias(name),
        PropertyKeys.testData(name, "id"), PropertyKeys.testData(name, "payload"));
    return concise ? feature : "```gherkin\n" + feature + "```";
  }

  // ── Elastic ───────────────────────────────────────────────────────────────

  private String elasticExplain(boolean concise) {
    if (concise) return "elastic steps: [elastic-search] connect to elastic search with name {name} | assign data {alias} from index {index} with query : (|key|value| table — keys: luceneQuery/routing/type/sortBy/from/size) | insert to index \"{index}\" with data : (horizontal single-row doc) | assign previous elastic search response to {alias}. Config: elasticsearch.service.{name}.hosts[0]/username/password/secured/requireAuthentication. IMPORTANT: search query DataTable MUST use |key|value| headers.";
    return """
        ## ElasticSearch Guide

        Config:
        ```properties
        elasticsearch.service.{name}.hosts[0]=${ELASTICSEARCH_NAME_HOST:http://localhost:9200}
        elasticsearch.service.{name}.username=${ELASTICSEARCH_NAME_USERNAME:}
        elasticsearch.service.{name}.password=${ELASTICSEARCH_NAME_PASSWORD:}
        elasticsearch.service.{name}.secured=false
        elasticsearch.service.{name}.requireAuthentication=false
        ```

        DataTable format for search/assign steps — MUST use |key|value| headers:
        Supported keys: luceneQuery (Lucene query string or JSON), routing, type,
                        sortBy (field:ASC or JSON map), from (offset), size (page size)

        DataTable format for insert/update steps — horizontal single-row document:
        Row 0 = field names, row 1 = values (last row used as document map)

        Feature:
        ```gherkin
        # Search
        Given [elastic-search] connect to elastic search with name {name}
        When [elastic-search] assign data {alias} from index {index} with query :
          | key         | value                               |
          | luceneQuery | {"term":{"id":"properties(x.id)"}}  |
          | size        | 10                                  |
        Then [elastic-search] assign previous elastic search response to {alias}

        # Insert document
        When [elastic-search] insert to index "{index}" with data :
          | name           | status | price |
          | Sample Product | active | 99.9  |
        ```
        """;
  }

  private String elasticConfig(String name, boolean concise) {
    String block = elasticConfigBlock(name);
    return concise ? block : "```properties\n" + block + "```";
  }

  /** {@code elasticsearch.service.<name>.*} ({@code ElasticSearchProperties}/{@code ElasticSearchModel}). */
  static String elasticConfigBlock(String name) {
    String env = PropertyKeys.toEnvKey(name);
    return """
        # ElasticSearch — %s
        elasticsearch.service.%s.hosts[0]=${ELASTICSEARCH_%s_HOST:http://localhost:9200}
        elasticsearch.service.%s.username=${ELASTICSEARCH_%s_USERNAME:}
        elasticsearch.service.%s.password=${ELASTICSEARCH_%s_PASSWORD:}
        elasticsearch.service.%s.secured=false
        elasticsearch.service.%s.requireAuthentication=false
        """.formatted(name, name, env, name, env, name, env, name, name);
  }

  private String elasticFeature(String name, boolean concise) {
    String feature = """
        Given [elastic-search] connect to elastic search with name %s
        When [elastic-search] assign data %sResults from index %s with query :
          | key         | value                                       |
          | luceneQuery | {"term":{"id":"properties(test.%s.id)"}}    |
          | size        | 10                                          |
        Then [elastic-search] assign previous elastic search response to %sResults
        """.formatted(name, name, name, name, name);
    return concise ? feature : "```gherkin\n" + feature + "```";
  }
}
