package io.github.ygrip.testara.streaming.context;

import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.streaming.consumer.KafkaConsumerHelper;
import io.github.ygrip.testara.streaming.publisher.KafkaPublisherHelper;

public final class TestKafka {
  public static KafkaConsumerHelper consumer() {
    return TestFramework.context().get(KafkaConsumerHelper.class);
  }

  public static KafkaConsumerHelper consumer(String serviceName) throws Exception {
    return consumer().init(serviceName);
  }

  public static KafkaPublisherHelper publisher() {
    return TestFramework.context().get(KafkaPublisherHelper.class);
  }

  public static KafkaPublisherHelper publisher(String serviceName) throws Exception {
    return publisher().init(serviceName);
  }
}
