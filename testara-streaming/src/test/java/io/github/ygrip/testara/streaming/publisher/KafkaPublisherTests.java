package io.github.ygrip.testara.streaming.publisher;

import io.github.ygrip.testara.core.BaseTests;
import io.github.ygrip.testara.core.TestWith;
import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.core.mapper.MapperHelper;
import io.github.ygrip.testara.core.registry.RootRegistry;
import io.github.ygrip.testara.streaming.config.KafkaProperties;
import org.hamcrest.Matchers;
import org.hamcrest.core.Is;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

@Tag("publisher")
@Tag("kafka")
@TestWith(properties = {"classpath:application.properties", "classpath:configuration.properties"})
public class KafkaPublisherTests extends BaseTests {

  private KafkaPublisherHelper kafkaPublisher;

  @Override
  protected void registerInfrastructure(RootRegistry registry) {
    kafkaPublisher = registry.get(KafkaPublisherHelper.class);
  }

  @Test
  public void bindProperties() {
    KafkaProperties props = TestFramework.context().configuration().get("kafka", KafkaProperties.class);
    assertThat(props, Is.is(Matchers.notNullValue()));
  }

  @Test
  public void checkConnection() throws Throwable {
    boolean connected = kafkaPublisher.init("message").isConnected();

    assertThat(connected, equalTo(true));
  }

  @Test
  public void publishEvent() throws InterruptedException {
    Map<String, String> payload = new HashMap<>();
    payload.put("identifier", "visit-promo-page");
    payload.put("memberId", "some.member@gmail.com");
    payload.put("value", "kke-bbf");
    payload.put("pageUrl", "https://www.github.com/ygrip/promosi/kke-bbf");
    kafkaPublisher.init("quest").send("user_action", MapperHelper.toString(payload));
  }
}
