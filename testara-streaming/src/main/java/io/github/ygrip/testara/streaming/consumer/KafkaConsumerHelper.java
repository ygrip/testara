package io.github.ygrip.testara.streaming.consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import io.github.ygrip.testara.core.context.TestComponent;
import io.github.ygrip.testara.core.mapper.MapperHelper;
import io.github.ygrip.testara.core.registry.RegistryScope;
import io.github.ygrip.testara.streaming.KafkaHelper;
import io.github.ygrip.testara.streaming.config.KafkaProperties;
import io.github.ygrip.testara.streaming.model.KafkaMetaData;
import lombok.extern.log4j.Log4j2;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeTopicsResult;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverOffset;
import reactor.kafka.receiver.ReceiverOptions;
import reactor.kafka.receiver.ReceiverRecord;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * <p>KafkaConsumerHelper class.</p>
 *
 * @author yunaz.ramadhan on 1/1/2021
 * @version $Id: $Id
 */
@Log4j2
@TestComponent(scope = RegistryScope.GLOBAL)
public class KafkaConsumerHelper extends KafkaHelper<ReceiverOptions<String, String>> {
  private final Integer MAX_POLL_RECORDS;
  private final ConcurrentHashMap<String, Map<String, ConsumerPool<?>>> consumers;

  /**
   * <p>Constructor for KafkaConsumerHelper.</p>
   *
   * @param properties a {@link io.github.ygrip.testara.streaming.config.KafkaProperties} object.
   */
  public KafkaConsumerHelper(KafkaProperties properties) {
    super(properties);
    MAX_POLL_RECORDS = Math.max(1, properties.getMaxPollRecords());
    this.consumers = new ConcurrentHashMap<>();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public KafkaConsumerHelper init(String serviceName) {
    setConnection(ReceiverOptions.create(constructProperties(serviceName, ConsumerConfig.class)));
    log.debug("#Kafka consumer is initialized on {}", serviceName);
    return this;
  }

  /**
   * <p>subscribeTopic.</p>
   *
   * @param topic a {@link String} object.
   * @return a {@link io.github.ygrip.testara.streaming.consumer.KafkaConsumerHelper.ConsumerPool} object.
   */
  @SuppressWarnings("unchecked")
  public ConsumerPool<LinkedHashMap<String, Object>> subscribeTopic(String topic) {
    String resolvedTopic = getResolvedTopic(topic);
    return (ConsumerPool<LinkedHashMap<String, Object>>) this.consumers.computeIfAbsent(getCurrentServiceName(),
            group -> new HashMap<>())
        .computeIfAbsent(resolvedTopic,
            consumer -> new ConsumerPool<>(getCurrentServiceName(),
                resolvedTopic,
                MapperHelper.getGenericType(LinkedHashMap.class)));
  }

  /**
   * <p>subscribeTopicAs.</p>
   *
   * @param topic a {@link String} object.
   * @param type  a {@link Class} object.
   * @param <T>   a T object.
   * @return a {@link io.github.ygrip.testara.streaming.consumer.KafkaConsumerHelper.ConsumerPool} object.
   */
  @SuppressWarnings("unchecked")
  public <T> ConsumerPool<T> subscribeTopicAs(String topic, Class<T> type) {
    String resolvedTopic = getResolvedTopic(topic);
    return (ConsumerPool<T>) this.consumers.computeIfAbsent(getCurrentServiceName(), group -> new HashMap<>())
        .computeIfAbsent(resolvedTopic,
            consumer -> new ConsumerPool<>(getCurrentServiceName(), resolvedTopic, MapperHelper.getGenericType(type)));
  }

  /**
   * <p>subscribeTopicAs.</p>
   *
   * @param topic a {@link String} object.
   * @param type  a {@link TypeReference} object.
   * @param <T>   a T object.
   * @return a {@link io.github.ygrip.testara.streaming.consumer.KafkaConsumerHelper.ConsumerPool} object.
   */
  @SuppressWarnings("unchecked")
  public <T> ConsumerPool<T> subscribeTopicAs(String topic, TypeReference<T> type) {
    String resolvedTopic = getResolvedTopic(topic);
    return (ConsumerPool<T>) this.consumers.computeIfAbsent(getCurrentServiceName(), group -> new HashMap<>())
        .computeIfAbsent(resolvedTopic,
            consumer -> new ConsumerPool<>(getCurrentServiceName(), resolvedTopic, MapperHelper.getGenericType(type)));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void close() {
    log.trace("#Close kafka consumer");
    this.consumers.keySet().forEach(this::close);
  }

  public void close(String serviceName) {
    log.debug("Start closing kafka consumer on service {}", serviceName);
    this.consumers.getOrDefault(serviceName, new HashMap<>()).values().forEach(consumer -> {
      try {
        consumer.close();
        log.trace("Successfully close kafka consumer for {} on topic {}", serviceName, consumer.topic);
      } catch (Exception err) {
        log.error("Fail to close kafka consumer for {} on topic {}", serviceName, consumer.topic);
      }
    });
    this.consumers.remove(serviceName);
    log.debug("Done closing kafka consumer on service {}", serviceName);
  }

  public class ConsumerPool<T> implements AutoCloseable {
    private static final long DEFAULT_QUICK_TIMEOUT_MS = 5000L;  // 5 seconds for quick operations
    
    private final String topic;
    private final JavaType javaType;
    private final KafkaReceiver<String, String> receiver;
    private final Flux<ReceiverRecord<String, String>> consumer;
    private final AtomicReference<List<TopicPartition>> partitions;
    private final String serviceName;
    private List<Predicate<T>> conditions;
    private long customTimeoutMs = -1;  // -1 means use default

    protected ConsumerPool(String service, String topic, JavaType javaType) {
      log.info("#Kafka consumer subscribed to topic {}", topic);
      this.topic = topic;
      this.conditions = new ArrayList<>();
      this.javaType = javaType;
      this.receiver = KafkaReceiver.create(getConnection().subscription(Collections.singleton(topic)));
      this.consumer = this.receiver.receive();
      this.partitions = new AtomicReference<>(Collections.emptyList());
      this.serviceName = service;
    }

    private Flux<ReceiverRecord<String, String>> getConsumer() {
      return this.consumer;
    }

    /**
     * Set a custom timeout for subsequent operations on this consumer pool.
     *
     * @param timeoutMs timeout in milliseconds
     * @return this consumer pool for chaining
     */
    public ConsumerPool<T> withTimeout(long timeoutMs) {
      this.customTimeoutMs = Math.max(100, timeoutMs);
      return this;
    }

    /**
     * Reset to default timeout from configuration.
     *
     * @return this consumer pool for chaining
     */
    public ConsumerPool<T> withDefaultTimeout() {
      this.customTimeoutMs = -1;
      return this;
    }

    private long getEffectiveTimeout() {
      return customTimeoutMs > 0 ? customTimeoutMs : getRequestTimeoutMs();
    }

    public List<TopicPartition> getPartitions() {
      if (this.partitions.get().isEmpty()) {
        try (AdminClient adminClient = AdminClient.create(constructProperties(this.serviceName,
            ConsumerConfig.class))) {
          DescribeTopicsResult desc = adminClient.describeTopics(Collections.singleton(topic));
          TopicDescription topicDescription = desc.topicNameValues().get(topic).get();
          partitions.set(topicDescription.partitions()
              .stream()
              .map(p -> new TopicPartition(topic, p.partition()))
              .collect(Collectors.toList()));
        } catch (Exception err) {
          log.warn("Fail to get partition info for service {} on topic {} , error : {}",
              serviceName,
              topic,
              err.getMessage());
        }
      }
      return this.partitions.get();
    }

    public long getTotalRecords() {
      Mono<Long> totalRecords = Mono.fromCallable(() -> {
        try (AdminClient admin = AdminClient.create(constructProperties(serviceName, ConsumerConfig.class))) {
          List<TopicPartition> currentPartition = getPartitions();

          // Step 1: Request earliest/latest offsets
          Map<TopicPartition, OffsetSpec> earliestReq = new HashMap<>();
          Map<TopicPartition, OffsetSpec> latestReq = new HashMap<>();
          for (TopicPartition tp : currentPartition) {
            earliestReq.put(tp, OffsetSpec.earliest());
            latestReq.put(tp, OffsetSpec.latest());
          }

          Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> earliest =
              admin.listOffsets(earliestReq).all().get();
          Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> latest =
              admin.listOffsets(latestReq).all().get();

          // Step 2: Compute total
          return currentPartition.stream().mapToLong(tp -> latest.get(tp).offset() - earliest.get(tp).offset()).sum();
        }
      }).subscribeOn(Schedulers.boundedElastic());
      return Optional.ofNullable(totalRecords.doOnSubscribe(sub -> log.trace("Counting records... on topic {}", topic))
          .doOnNext(count -> log.debug("Topic {} has {} records", topic, count))
          .block()).orElse(0L);
    }

    public ConsumerPool<T> setConditions(List<Predicate<T>> conditions) {
      this.conditions = conditions;
      return this;
    }

    public ConsumerPool<T> addCondition(Predicate<T> condition) {
      this.conditions.add(condition);
      return this;
    }

    private Predicate<T> getCondition() {
      return this.conditions.stream().reduce(Predicate::and).orElse(result -> true);
    }

    public ConsumerPool<T> setCondition(Predicate<T> condition) {
      this.conditions = Collections.singletonList(condition);
      return this;
    }

    /**
     * Get messages with default timeout. Returns immediately when maxSize messages are collected,
     * or returns partial results when timeout expires.
     *
     * @param maxSize maximum number of messages to retrieve
     * @return list of messages (may be less than maxSize if timeout expires first)
     */
    public List<KafkaMetaData<T>> getMostRecentMessages(long maxSize) {
      return getMostRecentMessages(maxSize, getEffectiveTimeout());
    }

    /**
     * Quick poll for messages with short timeout (5 seconds).
     * Useful for fast checks when messages are expected to be immediately available.
     *
     * @param maxSize maximum number of messages to retrieve
     * @return list of messages
     */
    public List<KafkaMetaData<T>> pollMessages(long maxSize) {
      return getMostRecentMessages(maxSize, DEFAULT_QUICK_TIMEOUT_MS);
    }

    /**
     * Get messages with custom timeout. Returns immediately when maxSize messages are collected,
     * or returns partial results when timeout expires.
     *
     * @param maxSize   maximum number of messages to retrieve
     * @param timeoutMs maximum time to wait in milliseconds
     * @return list of messages (may be less than maxSize if timeout expires first)
     */
    public List<KafkaMetaData<T>> getMostRecentMessages(long maxSize, long timeoutMs) {
      maxSize = Math.max(1, Math.min(MAX_POLL_RECORDS, maxSize));
      timeoutMs = Math.max(100, timeoutMs);
      int maxSizeInt = (int) maxSize;
      long startTime = System.currentTimeMillis();

      log.debug("Starting to consume up to {} messages from topic {}, timeout={}ms", maxSizeInt, topic, timeoutMs);

      // Collect messages as they arrive - this list captures partial results on timeout
      List<KafkaMetaData<T>> collected = Collections.synchronizedList(new ArrayList<>());

      try {
        getConsumer()
            .map(this::toKafkaMetaData)
            .doOnNext(meta -> {
              collected.add(meta);
              log.trace("Received message #{}: key={}, offset={}", collected.size(), meta.getKey(), meta.getOffset());
            })
            .take(maxSizeInt)               // Complete immediately when count reached
            .blockLast(Duration.ofMillis(timeoutMs));  // Timeout if not enough messages
      } catch (IllegalStateException e) {
        // Timeout occurred - we keep whatever was collected
        if (!e.getMessage().contains("Timeout")) {
          throw e;
        }
        log.trace("Timeout reached, collected {} of {} requested messages", collected.size(), maxSizeInt);
      }

      long elapsed = System.currentTimeMillis() - startTime;
      log.debug("Consumed {} messages from topic {} in {}ms (requested: {}, timeout: {}ms)",
          collected.size(), topic, elapsed, maxSizeInt, timeoutMs);

      return collected;
    }

    private KafkaMetaData<T> toKafkaMetaData(ReceiverRecord<String, String> record) {
      KafkaMetaData<T> meta = new KafkaMetaData<>();
      meta.setTopic(record.topic());
      meta.setPartition(record.partition());
      meta.setOffset(record.offset());
      meta.setTimestamp(record.timestamp());
      meta.setKey(record.key());

      try {
        meta.setValue(MapperHelper.toObject(record.value(), javaType));
      } catch (Exception e) {
        meta.setValue(null);
      }
      // Acknowledge if needed
      ReceiverOffset offset = record.receiverOffset();
      offset.acknowledge();
      return meta;
    }

    /**
     * Get filtered messages with default settings. Streams messages and filters reactively.
     * Returns immediately when enough matching messages are found.
     *
     * @return list of messages matching the configured conditions
     */
    public List<KafkaMetaData<T>> getFilteredMessages() {
      return getFilteredMessages(MAX_POLL_RECORDS, getEffectiveTimeout());
    }

    /**
     * Get filtered messages with custom max size. Returns immediately when maxSize matching
     * messages are found, or returns partial results when timeout expires.
     *
     * @param maxSize maximum number of matching messages to retrieve
     * @return list of messages matching the configured conditions
     */
    public List<KafkaMetaData<T>> getFilteredMessages(long maxSize) {
      return getFilteredMessages(maxSize, getEffectiveTimeout());
    }

    /**
     * Quick poll for filtered messages with short timeout (5 seconds).
     *
     * @param maxSize maximum number of matching messages to retrieve
     * @return list of messages matching the configured conditions
     */
    public List<KafkaMetaData<T>> pollFilteredMessages(long maxSize) {
      return getFilteredMessages(maxSize, DEFAULT_QUICK_TIMEOUT_MS);
    }

    /**
     * Get filtered messages with custom max size and timeout. Filters reactively in the stream,
     * returns immediately when maxSize matching messages are found.
     *
     * @param maxSize   maximum number of matching messages to retrieve
     * @param timeoutMs maximum time to wait in milliseconds
     * @return list of messages matching the configured conditions
     */
    public List<KafkaMetaData<T>> getFilteredMessages(long maxSize, long timeoutMs) {
      if (this.conditions == null || this.conditions.isEmpty()) {
        return getMostRecentMessages(maxSize, timeoutMs);
      }

      maxSize = Math.max(1, Math.min(MAX_POLL_RECORDS, maxSize));
      timeoutMs = Math.max(100, timeoutMs);
      int maxSizeInt = (int) maxSize;
      Predicate<T> condition = getCondition();
      long startTime = System.currentTimeMillis();

      log.debug("Starting filtered consume: up to {} matching messages from topic {}, timeout={}ms",
          maxSizeInt, topic, timeoutMs);

      // Collect matching messages as they arrive
      List<KafkaMetaData<T>> collected = Collections.synchronizedList(new ArrayList<>());

      try {
        getConsumer()
            .map(this::toKafkaMetaData)
            .filter(meta -> {
              try {
                return meta.getValue() != null && condition.test(meta.getValue());
              } catch (Exception e) {
                log.trace("Filter condition failed for message: {}", e.getMessage());
                return false;
              }
            })
            .doOnNext(meta -> {
              collected.add(meta);
              log.trace("Matched message #{}: key={}, offset={}", collected.size(), meta.getKey(), meta.getOffset());
            })
            .take(maxSizeInt)
            .blockLast(Duration.ofMillis(timeoutMs));
      } catch (IllegalStateException e) {
        if (!e.getMessage().contains("Timeout")) {
          throw e;
        }
        log.trace("Timeout reached, collected {} of {} requested matching messages", collected.size(), maxSizeInt);
      }

      long elapsed = System.currentTimeMillis() - startTime;
      log.debug("Filtered consume: {} matching messages from topic {} in {}ms (requested: {}, timeout: {}ms)",
          collected.size(), topic, elapsed, maxSizeInt, timeoutMs);

      return collected;
    }

    /**
     * Get one message matching the configured conditions. Returns immediately when found,
     * or null if timeout expires without finding a match.
     *
     * @return first message matching conditions, or null if none found within timeout
     */
    public KafkaMetaData<T> getOneMessageMatchingCondition() {
      return getOneMessageMatchingCondition(getEffectiveTimeout());
    }

    /**
     * Quick poll for one matching message with short timeout (5 seconds).
     *
     * @return first message matching conditions, or null if none found
     */
    public KafkaMetaData<T> pollOneMatchingMessage() {
      return getOneMessageMatchingCondition(DEFAULT_QUICK_TIMEOUT_MS);
    }

    /**
     * Get one message matching the configured conditions with custom timeout.
     * Returns immediately when found.
     *
     * @param timeoutMs maximum time to wait in milliseconds
     * @return first message matching conditions, or null if none found within timeout
     */
    public KafkaMetaData<T> getOneMessageMatchingCondition(long timeoutMs) {
      if (this.conditions == null || this.conditions.isEmpty()) {
        List<KafkaMetaData<T>> messages = getMostRecentMessages(1, timeoutMs);
        return messages.isEmpty() ? null : messages.get(0);
      }

      timeoutMs = Math.max(100, timeoutMs);
      Predicate<T> condition = getCondition();
      long startTime = System.currentTimeMillis();

      log.debug("Looking for one matching message from topic {}, timeout={}ms", topic, timeoutMs);

      AtomicReference<KafkaMetaData<T>> found = new AtomicReference<>(null);

      try {
        getConsumer()
            .map(this::toKafkaMetaData)
            .filter(meta -> {
              try {
                return meta.getValue() != null && condition.test(meta.getValue());
              } catch (Exception e) {
                return false;
              }
            })
            .doOnNext(meta -> {
              found.set(meta);
              log.trace("Found matching message: key={}, offset={}", meta.getKey(), meta.getOffset());
            })
            .take(1)
            .blockLast(Duration.ofMillis(timeoutMs));
      } catch (IllegalStateException e) {
        if (!e.getMessage().contains("Timeout")) {
          throw e;
        }
        log.trace("Timeout reached while looking for matching message");
      }

      long elapsed = System.currentTimeMillis() - startTime;
      log.debug("Found matching message: {} from topic {} in {}ms", found.get() != null, topic, elapsed);

      return found.get();
    }

    @Override
    public void close() throws Exception {
      try {
        ((Disposable) this.receiver).dispose();
      } catch (Exception ignored) {

      }
    }
  }
}
