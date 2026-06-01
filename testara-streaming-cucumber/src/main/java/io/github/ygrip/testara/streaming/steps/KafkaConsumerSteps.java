package io.github.ygrip.testara.streaming.steps;

import com.fasterxml.jackson.core.type.TypeReference;
import io.github.ygrip.testara.core.context.Inject;
import io.github.ygrip.testara.core.context.TestComponent;
import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.core.data.DataHolder;
import io.github.ygrip.testara.core.json.JsonHelper;
import io.github.ygrip.testara.core.model.RetryableMethod;
import io.github.ygrip.testara.core.registry.RegistryScope;
import io.github.ygrip.testara.core.transformer.TransformerService;
import io.github.ygrip.testara.streaming.consumer.KafkaConsumerHelper;
import io.github.ygrip.testara.validation.ValidatorHelper;
import io.github.ygrip.testara.validation.model.DataValidation;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.LinkedHashMap;
import java.util.List;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * @author yunaz.ramadhan on 12/11/2019
 */
@TestComponent(scope = RegistryScope.TEST)
public class KafkaConsumerSteps {
  @Inject
  private KafkaConsumerHelper kafkaConsumer;
  @Inject
  private DataHolder dataHolder;

  @RetryableMethod
  @Given("{actor} start kafka consumer for {word}")
  public void startKafkaConsumer(String identifier, String serviceName) {
    kafkaConsumer.init(serviceName);
  }

  @RetryableMethod
  @Given("{actor} listen kafka from topic {word}")
  public void listenKafkaTopic(String identifier, String topic) {
    topic = TestFramework.context().converter().convert(topic);
    kafkaConsumer.subscribeTopic(topic);
  }

  @RetryableMethod
  @When("{actor} assign count of total records from topic {string} to {word}")
  public void getTotalRecordsFromTopic(String identifier, String topic, String responseKey) throws Throwable {
    assertThat("No kafka connection", kafkaConsumer.isConnected(), equalTo(true));
    topic = TestFramework.context().converter().convert(topic);
    dataHolder.setResponse(responseKey, kafkaConsumer.subscribeTopic(topic).getTotalRecords());
  }

  @RetryableMethod
  @When("{actor} assign {int} latest records from topic {string} to {word}")
  public void getNRecordsFromTopic(String identifier, Integer number, String topic, String responseKey)
      throws Throwable {
    assertThat("No kafka connection", kafkaConsumer.isConnected(), equalTo(true));
    topic = TestFramework.context().converter().convert(topic);
    dataHolder.setResponse(responseKey,
        kafkaConsumer.subscribeTopicAs(topic, new TypeReference<LinkedHashMap<String, Object>>() {
        }).getMostRecentMessages(number));
  }

  @RetryableMethod
  @When("{actor} assign {int} latest records from topic {string} to {word} and filter by")
  public void getNRecordsFromTopicWithFilter(String identifier,
      Integer number,
      String topic,
      String responseKey,
      DataTable table) throws Throwable {
    assertThat("No kafka connection", kafkaConsumer.isConnected(), equalTo(true));
    List<DataValidation> filters = new TransformerService().sourceData(table.cells()).toList(DataValidation.class);
    topic = TestFramework.context().converter().convert(topic);
    KafkaConsumerHelper.ConsumerPool<LinkedHashMap<String, Object>> consumer =
        kafkaConsumer.subscribeTopicAs(topic, new TypeReference<>() {
        });
    for (DataValidation filter : filters) {
      final String filterField = String.valueOf(filter.getActual());
      consumer.addCondition(item -> {
        try (JsonHelper.JsonPathHolder jsonPath = JsonHelper.instance()) {
          filter.setActual(jsonPath.parse(item).read(filterField));
          ValidatorHelper.validate(filter);
          return true;
        } catch (Throwable ignored) {
          return false;
        }
      });
    }
    dataHolder.setResponse(responseKey, consumer.getFilteredMessages(number));
  }

  @RetryableMethod
  @Then("{actor} stop kafka consumer")
  public void stopKafkaConsumer(String identifier) {
    kafkaConsumer.close();
  }
}
