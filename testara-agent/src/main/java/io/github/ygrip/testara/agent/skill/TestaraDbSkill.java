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
    String block = """
        sql.service.%sDb.host-name=${DB_%s_HOST:localhost}
        sql.service.%sDb.port=5432
        sql.service.%sDb.username=${DB_%s_USERNAME:postgres}
        sql.service.%sDb.password=${DB_%s_PASSWORD:postgres}
        sql.service.%sDb.db-name=${DB_%s_NAME:%s}
        sql.service.%sDb.db-type=POSTGRESQL
        sql.service.%sDb.timeout=3
        """.formatted(name, toEnvKey(name), name, name, toEnvKey(name), name, toEnvKey(name),
        name, toEnvKey(name), toPropertyKey(name), name, name);
    return concise ? block : "```properties\n" + block + "```";
  }

  private String sqlFeature(String name, boolean concise) {
    String feature = """
        Given [sql] connect to database with name %sDb
        Given [sql] prepare query with value :
          \"\"\"
          select *
          from %s
          where id = 'properties(test.%s.id)'
          \"\"\"
        When [sql] execute database query
        Then [sql] assign previous database response to %sRows
        """.formatted(name, name, name, name);
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
    String block = """
        mongo.service.%sDb.hosts=${MONGO_%s_HOSTS:localhost:27017}
        mongo.service.%sDb.db-name=${MONGO_%s_NAME:%s}
        mongo.service.%sDb.username=${MONGO_%s_USERNAME:}
        mongo.service.%sDb.password=${MONGO_%s_PASSWORD:}
        mongo.service.%sDb.ssl-enabled=false
        """.formatted(name, toEnvKey(name), name, toEnvKey(name), toPropertyKey(name),
        name, toEnvKey(name), name, toEnvKey(name), name);
    return concise ? block : "```properties\n" + block + "```";
  }

  private String mongoFeature(String name, boolean concise) {
    String feature = """
        Given [mongo] connect to database with name %sDb
        Given [mongo] select collection with name %s
        When [mongo] select data with query :
          | key    | value                              |
          | query  | {"_id": "properties(test.%s.id)"} |
          | limit  | 1                                  |
        Then [mongo] assign previous database response to %sRows
        """.formatted(name, name, name, name);
    return concise ? feature : "```gherkin\n" + feature + "```";
  }

  // ── Kafka ─────────────────────────────────────────────────────────────────

  private String kafkaExplain(boolean concise) {
    if (concise) return "kafka steps: [kafka] start kafka producer for {name} | [kafka] send kafka message to topic \"properties(kafka.topic.*)\" with key \"uuid()\" or properties(test.*.id) and data \"request($['event'])\" | [kafka] stop kafka producer. Config uses ${ENV:fallback}.";
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
        When [kafka] send kafka message to topic "properties(kafka.topic.{alias})" with key "properties(test.{domain}.id)" and data "properties(test.{domain}.payload)"
        Then [kafka] stop kafka producer
        ```

        Consumer feature:
        ```gherkin
        Given [kafka] start kafka consumer for {name}
        Given [kafka] listen kafka from topic {alias}
        When [kafka] assign 5 latest records from topic "properties(kafka.topic.{alias})" to {domain}Events
        Then [kafka] stop kafka consumer
        ```
        """;
  }

  private String kafkaConfig(String name, boolean concise) {
    String block = """
        kafka.service.%sKafka.servers=${KAFKA_%s_SERVERS:localhost:9092}
        kafka.service.%sKafka.group-id=${KAFKA_%s_GROUP_ID:testara-%s-test}
        kafka.service.%sKafka.topics.%sEvent=${KAFKA_TOPIC_%s_EVENT:%s.event.v1}
        """.formatted(name, toEnvKey(name), name, toEnvKey(name), toPropertyKey(name),
        name, name, toEnvKey(name), toPropertyKey(name));
    return concise ? block : "```properties\n" + block + "```";
  }

  private String kafkaFeature(String name, boolean concise) {
    String feature = """
        # Producer
        Given [kafka] start kafka producer for %sKafka
        When [kafka] send kafka message to topic "properties(kafka.topic.%s-event)" with key "properties(test.%s.id)" and data "properties(test.%s.payload)"
        Then [kafka] stop kafka producer
        """.formatted(name, name, name, name);
    return concise ? feature : "```gherkin\n" + feature + "```";
  }

  // ── Elastic ───────────────────────────────────────────────────────────────

  private String elasticExplain(boolean concise) {
    if (concise) return "elastic steps: [elastic-search] connect to elastic search with name {name} | assign data {alias} from index {index} with query : (|key|value| table — keys: luceneQuery/routing/type/sortBy/from/size) | insert to index \"{index}\" with data : (horizontal single-row doc) | assign previous elastic search response to {alias}. Config: elastic-search.service.{name}.* IMPORTANT: search query DataTable MUST use |key|value| headers.";
    return """
        ## ElasticSearch Guide

        Config:
        ```properties
        elastic-search.service.{name}.host=${ELASTIC_NAME_HOST:localhost}
        elastic-search.service.{name}.port=${ELASTIC_NAME_PORT:9200}
        elastic-search.service.{name}.scheme=https
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
    String block = """
        elastic-search.service.%s.host=${ELASTIC_%s_HOST:localhost}
        elastic-search.service.%s.port=${ELASTIC_%s_PORT:9200}
        elastic-search.service.%s.scheme=https
        """.formatted(name, toEnvKey(name), name, toEnvKey(name), name);
    return concise ? block : "```properties\n" + block + "```";
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

  private String toPropertyKey(String value) {
    return value.toLowerCase(Locale.ROOT)
        .replaceAll("[^a-z0-9]+", "-")
        .replaceAll("^-|-$", "");
  }

  private String toEnvKey(String value) {
    return value.toUpperCase(Locale.ROOT)
        .replaceAll("[^A-Z0-9]+", "_")
        .replaceAll("^_|_$", "");
  }
}
