package io.github.ygrip.testara.streaming.steps;

import io.github.ygrip.testara.core.context.Inject;
import io.github.ygrip.testara.core.context.TestComponent;
import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.core.mapper.MapperHelper;
import io.github.ygrip.testara.core.registry.RegistryScope;
import io.github.ygrip.testara.core.transformer.TransformerService;
import io.github.ygrip.testara.streaming.model.KafkaMessage;
import io.github.ygrip.testara.streaming.publisher.KafkaPublisherHelper;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.List;

import static io.github.ygrip.testara.core.support.CommonHelper.isBlank;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * @author yunaz.ramadhan on 12/11/2019
 */
@TestComponent(scope = RegistryScope.TEST)
public class KafkaPublisherSteps {
  @Inject
  private KafkaPublisherHelper kafkaPublisher;

  @Given("{actor} start kafka producer for {word}")
  public void startKafkaProducer(String identifier, String serviceName) {
    kafkaPublisher.init(serviceName);
  }

  @When("{actor} send kafka message to topic {string} with data {string}")
  public void sendKafkaMessageToTopicWithData(String identifier, String topic, String message) throws Throwable {
    assertThat("No kafka connection", kafkaPublisher.isConnected(), equalTo(true));
    topic = TestFramework.context().converter().convert(topic);
    Object result = TestFramework.context().converter().convert(message);
    message = isBlank(result) ? message : MapperHelper.toString(result);
    kafkaPublisher.send(topic, message);
  }

  @When("{actor} send kafka message to topic {string} with key {string} and data {string}")
  public void sendKafkaMessageToTopicWithData(String identifier, String topic, String key, String message)
      throws Throwable {
    assertThat("No kafka connection", kafkaPublisher.isConnected(), equalTo(true));
    topic = TestFramework.context().converter().convert(topic);
    key = TestFramework.context().converter().convert(key);
    Object result = TestFramework.context().converter().convert(message);
    message = isBlank(result) ? message : MapperHelper.toString(result);
    kafkaPublisher.send(topic, key, message);
  }

  @When("{actor} send batch message to kafka with data")
  public void batchSendKafkaMessageToTopicWithData(String identifier, DataTable table) throws Throwable {
    assertThat("No kafka connection", kafkaPublisher.isConnected(), equalTo(true));
    List<KafkaMessage> messages = new TransformerService().sourceData(table.cells()).toList(KafkaMessage.class);
    kafkaPublisher.batchSend(messages);
  }

  @Then("{actor} stop kafka producer")
  public void stopKafkaProducer(String identifier) {
    kafkaPublisher.close();
  }

}
