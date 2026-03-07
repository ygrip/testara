package io.github.ygrip.testara.streaming.publisher;

import io.github.ygrip.testara.core.context.TestComponent;
import io.github.ygrip.testara.core.mapper.MapperHelper;
import io.github.ygrip.testara.core.registry.RegistryScope;
import io.github.ygrip.testara.core.support.CommonHelper;
import io.github.ygrip.testara.core.support.StringHelper;
import io.github.ygrip.testara.streaming.KafkaHelper;
import io.github.ygrip.testara.streaming.config.KafkaProperties;
import io.github.ygrip.testara.streaming.model.KafkaMessage;
import lombok.extern.log4j.Log4j2;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderOptions;
import reactor.kafka.sender.SenderRecord;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;


/**
 * <p>KafkaPublisherHelper class.</p>
 *
 * @author yunaz.ramadhan on 1/1/2021
 * @version $Id: $Id
 */
@Log4j2
@TestComponent(scope = RegistryScope.GLOBAL)
public class KafkaPublisherHelper extends KafkaHelper<SenderOptions<String, String>> {

  /**
   * <p>Constructor for KafkaPublisherHelper.</p>
   *
   * @param properties a {@link io.github.ygrip.testara.streaming.config.KafkaProperties} object.
   */
  public KafkaPublisherHelper(KafkaProperties properties) {
    super(properties);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public KafkaPublisherHelper init(String serviceName) {
    setConnection(SenderOptions.create(constructProperties(serviceName, ProducerConfig.class)));
    log.info("#Kafka publisher is initialized on {}", serviceName);
    return this;
  }

  /**
   * <p>send.</p>
   *
   * @param topic   a {@link String} object.
   * @param message a {@link String} object.
   * @throws InterruptedException if any.
   */
  public void send(String topic, String message) throws InterruptedException {
    SenderRecord<String, String, Object> record =
        SenderRecord.create(new ProducerRecord<>(getResolvedTopic(topic), message), null);
    String prettyMessage = StringHelper.prettyPrint(message);
    log.debug("#Sending to topic {} with message :\n{}", topic, prettyMessage);
    KafkaSender.create(getConnection())
        .send(Mono.just(record))
        .doOnError(e -> log.error("#ERROR while publishing to {}, log : \n{}", getResolvedTopic(topic), e))
        .doOnNext(recordMetadata -> log.debug(
            "Received new message \nTopic : {}\nPartition : {}\nOffset : {}\nTimestamp : {}",
            recordMetadata.recordMetadata().topic(),
            recordMetadata.recordMetadata().partition(),
            recordMetadata.recordMetadata().offset(),
            recordMetadata.recordMetadata().timestamp()))
        .subscribe();
  }

  /**
   * <p>send.</p>
   *
   * @param topic   a {@link String} object.
   * @param key     a {@link String} object.
   * @param message a {@link String} object.
   * @throws InterruptedException if any.
   */
  public void send(String topic, String key, String message) throws InterruptedException {
    SenderRecord<String, String, Object> record =
        SenderRecord.create(new ProducerRecord<>(getResolvedTopic(topic), key, message), null);
    String prettyMessage = StringHelper.prettyPrint(message);
    log.debug("#Sending to topic {} with message :\n{}", topic, prettyMessage);
    KafkaSender.create(getConnection())
        .send(Mono.just(record))
        .doOnError(e -> log.error("#ERROR while publishing to {}, log : \n{}", getResolvedTopic(topic), e))
        .doOnNext(recordMetadata -> log.debug(
            "Received new message \nTopic : {}\nPartition : {}\nOffset : {}\nTimestamp : {}",
            recordMetadata.recordMetadata().topic(),
            recordMetadata.recordMetadata().partition(),
            recordMetadata.recordMetadata().offset(),
            recordMetadata.recordMetadata().timestamp()))
        .subscribe();
  }

  /**
   * <p>batchSend.</p>
   *
   * @param messages a {@link List} object.
   * @throws Exception if any.
   */
  public void batchSend(List<KafkaMessage> messages) throws Exception {
    List<SenderRecord<String, String, Object>> records = new ArrayList<>();
    for (KafkaMessage message : messages) {
      String resolvedTopic = getResolvedTopic(message.getTopic());
      String parsedMessage = MapperHelper.toString(message.getMessage());
      ProducerRecord<String, String> request;
      String prettyMessage = StringHelper.prettyPrint(parsedMessage);
      if (!CommonHelper.isBlank(message.getKey())) {
        request = new ProducerRecord<>(resolvedTopic, message.getKey(), parsedMessage);
        log.debug("#Sending to topic {} with key {} and message :\n{}", resolvedTopic, message.getKey(), prettyMessage);
      } else {
        request = new ProducerRecord<>(resolvedTopic, parsedMessage);
        log.debug("#Sending to topic {} with message :\n{}", resolvedTopic, prettyMessage);
      }
      SenderRecord<String, String, Object> record = SenderRecord.create(request, null);
      records.add(record);
    }

    KafkaSender.create(getConnection())
        .send(Flux.fromIterable(records))
        .doOnError(e -> log.error("#ERROR while publishing one of the message, log : \n", e))
        .doOnNext(recordMetadata -> log.debug(
            "Received new message \nTopic : {}\nPartition : {}\nOffset : {}\nTimestamp : {}",
            recordMetadata.recordMetadata().topic(),
            recordMetadata.recordMetadata().partition(),
            recordMetadata.recordMetadata().offset(),
            recordMetadata.recordMetadata().timestamp()))
        .blockLast(Duration.ofMillis(getRequestTimeoutMs()));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void close() {
    if (!CommonHelper.isBlank(getConnection())) {
      log.trace("#Close kafka publisher");
      KafkaSender.create(getConnection()).close();
    }
  }
}
