package io.github.ygrip.testara.agent.skill;

import io.github.ygrip.testara.agent.AgentMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestaraDbSkillTest {

  @TempDir
  Path projectRoot;

  @Test
  void elasticConfigUsesElasticSearchPropertiesShape() {
    String config = new TestaraDbSkill().execute(new TestaraDbSkill.Input("elastic", "config", "catalog"), context());

    assertFalse(config.contains("elastic-search.service"), config);
    assertTrue(config.contains("elasticsearch.service.catalog.hosts[0]="), config);
    assertTrue(config.contains("elasticsearch.service.catalog.secured=false"), config);
    assertTrue(config.contains("elasticsearch.service.catalog.requireAuthentication=false"), config);
  }

  @Test
  void kafkaFeatureUsesTheTopicAliasItsConfigDefines() {
    String config = new TestaraDbSkill().execute(new TestaraDbSkill.Input("kafka", "config", "order"), context());
    String feature = new TestaraDbSkill().execute(new TestaraDbSkill.Input("kafka", "feature", "order"), context());

    assertTrue(config.contains("kafka.service.orderKafka.topics.orderEvent="), config);
    assertTrue(feature.contains("send kafka message to topic \"orderEvent\""), feature);
    assertFalse(feature.contains("kafka.topic."), feature);
  }

  private AgentContext context() {
    return new AgentContext(projectRoot, null, AgentMode.READ_ONLY, null, Map.of("format", "concise"));
  }
}
