package io.github.ygrip.testara.streaming.consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import io.github.ygrip.testara.core.BaseTests;
import io.github.ygrip.testara.core.TestWith;
import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.core.registry.RootRegistry;
import io.github.ygrip.testara.streaming.config.KafkaProperties;
import io.github.ygrip.testara.streaming.model.KafkaMetaData;
import org.hamcrest.Matchers;
import org.hamcrest.core.Is;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

@Tag("consumer")
@Tag("kafka")
@TestWith(properties = {"classpath:application.properties", "classpath:configuration.properties"})
public class KafkaConsumerTests extends BaseTests {

  private KafkaConsumerHelper kafkaConsumer;

  @Override
  protected void registerInfrastructure(RootRegistry registry) {
    kafkaConsumer = registry.get(KafkaConsumerHelper.class);
  }

  @Test
  public void bindProperties() {
    KafkaProperties props = TestFramework.context().configuration().get("kafka", KafkaProperties.class);
    assertThat(props, Is.is(Matchers.notNullValue()));
  }

  @Test
  public void checkConnection() throws Throwable {
    boolean connected = kafkaConsumer.init("message").isConnected();

    assertThat(connected, equalTo(true));
  }

  @Test
  public void consumerWithFilters() {
    String field = "categoryCode";
    String value = "order";
    List<KafkaMetaData<LinkedHashMap<String, Object>>> result = kafkaConsumer.init("message")
        .subscribeTopicAs("insert_inbox", new TypeReference<LinkedHashMap<String, Object>>() {
        })
        .addCondition(message -> message.get(field).equals(value))
        .addCondition(message -> message.get("subCategoryCode").equals("push_retail"))
        .getFilteredMessages(10);
    assertThat(result, is(notNullValue()));
    kafkaConsumer.close();
  }

  @Test
  public void countTotalEvents() {
    long result = kafkaConsumer.init("message").subscribeTopic("insert_inbox").getTotalRecords();
    assertThat(result, is(notNullValue()));
    assertThat(result, Matchers.greaterThan(0L));
    kafkaConsumer.close();
  }

  @Test
  public void consumeEvent() {
    List<KafkaMetaData<LinkedHashMap<String, Object>>> result = kafkaConsumer.init("message")
        .subscribeTopicAs("insert_inbox", new TypeReference<LinkedHashMap<String, Object>>() {
        })
        .getMostRecentMessages(2);
    assertThat(result, is(notNullValue()));
    assertThat(result.size(), equalTo(2));
    kafkaConsumer.close();
  }
}
