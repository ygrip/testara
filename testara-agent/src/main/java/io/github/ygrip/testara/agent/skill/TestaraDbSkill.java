package io.github.ygrip.testara.agent.skill;

import java.util.Locale;

/**
 * Skill: explain and generate DB (SQL/Mongo) and Kafka config and feature templates.
 *
 * All config values use properties() for env-specific data.
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
      default -> sqlExplain(concise) + "\n" + mongoExplain(concise) + "\n" + kafkaExplain(concise);
    };
  }

  // ── SQL ───────────────────────────────────────────────────────────────────

  private String sqlExplain(boolean concise) {
    if (concise) return "sql steps: [sql] connect to database with name {name} | [sql] prepare query with value : (multiline) | [sql] execute database query | [sql] assign previous database response to {alias}. Config: sql.service.{name}.* Use properties() for host/user/password/db-name.";
    return """
        ## SQL Guide

        Config:
        ```properties
        sql.service.{name}.host-name=properties(db.{name}.host)
        sql.service.{name}.db-name=properties(db.{name}.name)
        sql.service.{name}.username=properties(db.{name}.username)
        sql.service.{name}.password=properties(db.{name}.password)
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
        sql.service.%sDb.host-name=properties(db.%s.host)
        sql.service.%sDb.port=5432
        sql.service.%sDb.username=properties(db.%s.username)
        sql.service.%sDb.password=properties(db.%s.password)
        sql.service.%sDb.db-name=properties(db.%s.name)
        sql.service.%sDb.db-type=POSTGRESQL
        sql.service.%sDb.timeout=3

        db.%s.host=localhost
        db.%s.username=postgres
        db.%s.password=postgres
        db.%s.name=%s
        """.formatted(name, name, name, name, name, name, name, name, name, name, name, name, name, name, name, name);
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
    if (concise) return "mongo steps: [mongo] connect to database with name {name} | [mongo] select collection with name {col} | [mongo] select data with query : (DataTable: query/sort/project/limit/skip) | [mongo] assign previous database response to {alias}. Config: mongo.service.{name}.* Use properties() for hosts/user/password/db-name.";
    return """
        ## MongoDB Guide

        Config:
        ```properties
        mongo.service.{name}.hosts=properties(db.{name}.hosts)
        mongo.service.{name}.db-name=properties(db.{name}.name)
        mongo.service.{name}.username=properties(db.{name}.username)
        mongo.service.{name}.password=properties(db.{name}.password)
        ```

        Feature:
        ```gherkin
        Given [mongo] connect to database with name {name}Db
        Given [mongo] select collection with name {collection}
        When [mongo] select data with query :
          | query | {"sku": "properties(test.{domain}.sku)"} |
          | limit | 1 |
        Then [mongo] assign previous database response to {alias}Rows
        ```
        """;
  }

  private String mongoConfig(String name, boolean concise) {
    String block = """
        mongo.service.%sDb.hosts=properties(db.%s.hosts)
        mongo.service.%sDb.db-name=properties(db.%s.name)
        mongo.service.%sDb.username=properties(db.%s.username)
        mongo.service.%sDb.password=properties(db.%s.password)
        mongo.service.%sDb.ssl-enabled=false

        db.%s.hosts=localhost:27017
        db.%s.name=%s
        db.%s.username=
        db.%s.password=
        """.formatted(name, name, name, name, name, name, name, name, name, name, name, name, name, name);
    return concise ? block : "```properties\n" + block + "```";
  }

  private String mongoFeature(String name, boolean concise) {
    String feature = """
        Given [mongo] connect to database with name %sDb
        Given [mongo] select collection with name %s
        When [mongo] select data with query :
          | query | {"_id": "properties(test.%s.id)"} |
          | limit | 1                                  |
        Then [mongo] assign previous database response to %sRows
        """.formatted(name, name, name, name);
    return concise ? feature : "```gherkin\n" + feature + "```";
  }

  // ── Kafka ─────────────────────────────────────────────────────────────────

  private String kafkaExplain(boolean concise) {
    if (concise) return "kafka steps: [kafka] start kafka producer for {name} | [kafka] send kafka message to topic \"properties(kafka.topic.*)\" with key \"properties(test.*.id)\" and data \"properties(test.*.payload)\" | [kafka] stop kafka producer. Consumer: [kafka] start kafka consumer for {name} | [kafka] listen kafka from topic {topicAlias} | [kafka] assign N latest records from topic \"properties(kafka.topic.*)\" to {alias} | [kafka] stop kafka consumer. Config: kafka.service.{name}.* Use properties() for servers/group-id/topic names.";
    return """
        ## Kafka Guide

        Config:
        ```properties
        kafka.service.{name}.servers=properties(kafka.{name}.servers)
        kafka.service.{name}.group-id=properties(kafka.{name}.group-id)
        kafka.service.{name}.topics.{alias}=properties(kafka.topic.{alias})
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
        kafka.service.%sKafka.servers=properties(kafka.%s.servers)
        kafka.service.%sKafka.group-id=properties(kafka.%s.group-id)
        kafka.service.%sKafka.topics.%sEvent=properties(kafka.topic.%s-event)

        kafka.%s.servers=localhost:9092
        kafka.%s.group-id=testara-%s-test
        kafka.topic.%s-event=%s.event.v1
        """.formatted(name, name, name, name, name, name, name, name, name, name, name, name);
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
}
