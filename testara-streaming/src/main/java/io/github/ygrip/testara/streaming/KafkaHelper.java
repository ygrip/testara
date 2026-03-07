package io.github.ygrip.testara.streaming;

import io.github.ygrip.testara.streaming.config.KafkaProperties;
import io.github.ygrip.testara.streaming.model.KafkaModel;
import lombok.extern.log4j.Log4j2;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.AbstractConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.Properties;

import static io.github.ygrip.testara.core.support.CommonHelper.isBlank;


/**
 * <p>Abstract KafkaHelper class.</p>
 *
 * @author yunaz.ramadhan on 1/1/2021
 * @version $Id: $Id
 */
@Log4j2
public abstract class KafkaHelper<TYPE> {
  private final KafkaProperties properties;
  private KafkaModel currentModel;
  private String currentServiceName;
  private TYPE connection;

  /**
   * <p>Constructor for KafkaHelper.</p>
   *
   * @param properties a {@link io.github.ygrip.testara.streaming.config.KafkaProperties} object.
   */
  public KafkaHelper(KafkaProperties properties) {
    this.properties = properties;
  }

  /**
   * <p>Getter for the field <code>currentServiceName</code>.</p>
   *
   * @return a {@link String} object.
   */
  public String getCurrentServiceName() {
    return this.currentServiceName;
  }

  /**
   * <p>getCurrentConfiguration.</p>
   *
   * @return a {@link io.github.ygrip.testara.streaming.model.KafkaModel} object.
   */
  protected KafkaModel getCurrentConfiguration() {
    return this.currentModel;
  }

  /**
   * <p>Getter for the field <code>connection</code>.</p>
   *
   * @return a TYPE object.
   */
  public TYPE getConnection() {
    if (isBlank(getCurrentServiceName())) {
      log.debug("No kafka {} connection is establised yet", this.getClass().getSimpleName());
    } else if (this.connection == null) {
      init(getCurrentServiceName());
    }
    return this.connection;
  }

  /**
   * <p>Setter for the field <code>connection</code>.</p>
   *
   * @param connection a TYPE object.
   */
  protected void setConnection(TYPE connection) {
    this.connection = connection;
  }

  /**
   * <p>Check kafka connection status</p>
   *
   * @return a boolean object.
   */
  public boolean isConnected() {
    if (isBlank(getCurrentServiceName()) || this.connection == null) {
      log.debug("No kafka {} connection is establised yet", this.getClass().getSimpleName());
      return false;
    } else {
      log.debug("Kafka {} is connected to {}", this.getClass().getSimpleName(), getCurrentServiceName());
      return true;
    }
  }

  /**
   * <p>init.</p>
   *
   * @param serviceName a {@link String} object.
   * @return a {@link io.github.ygrip.testara.streaming.KafkaHelper} object.
   */
  public abstract KafkaHelper<TYPE> init(String serviceName);

  /**
   * <p>constructProperties.</p>
   *
   * @param serviceName a {@link String} object.
   * @param config      a {@link Class} object.
   * @return a {@link Properties} object.
   */
  protected Properties constructProperties(String serviceName, Class<? extends AbstractConfig> config) {
    String serializer = StringSerializer.class.getName();
    String deserializer = StringDeserializer.class.getName();
    Properties props = new Properties();
    if (!this.properties.getService().containsKey(serviceName)) {
      log.warn("#WARN : Unable to find kafka configuration for {}", serviceName);
    }
    this.currentModel = properties.getService().getOrDefault(serviceName, new KafkaModel());
    this.currentServiceName = serviceName;
    String host = isBlank(this.currentModel.getServers()) ? "localhost:9092" : this.currentModel.getServers();
    if (config.isAssignableFrom(ProducerConfig.class)) {
      props.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, host);
      props.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, serializer);
      props.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, serializer);
      props.setProperty(ProducerConfig.MAX_REQUEST_SIZE_CONFIG, String.valueOf(properties.getMaxBytes()));
    } else if (config.isAssignableFrom(ConsumerConfig.class)) {
      props.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, host);
      props.setProperty(ConsumerConfig.FETCH_MAX_BYTES_CONFIG, String.valueOf(properties.getMaxBytes()));
      props.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, deserializer);
      props.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, deserializer);
      props.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, properties.getAutoOffsetReset().trim().toLowerCase());
      props.setProperty(ConsumerConfig.GROUP_ID_CONFIG, this.currentModel.getGroupId());
      props.setProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, String.valueOf(properties.isEnableAutoCommit()));
      props.setProperty(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, String.valueOf(properties.getSessionTimeoutMs()));
      props.setProperty(ConsumerConfig.REQUEST_TIMEOUT_MS_CONFIG, String.valueOf(properties.getSessionTimeoutMs()));
      props.setProperty(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG,
          String.valueOf(properties.getHeartBeatIntervalMs()));
      props.setProperty(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, String.valueOf(properties.getPollIntervalMs()));
      props.setProperty(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, String.valueOf(properties.getMaxPollRecords()));
      props.setProperty(ConsumerConfig.EXCLUDE_INTERNAL_TOPICS_CONFIG, "true");
    }
    log.debug("#Construct kafka connection to servers : {}", host);
    return props;
  }

  /**
   * <p>close.</p>
   */
  public abstract void close();

  protected String getResolvedTopic(String topic) {
    return isBlank(getCurrentConfiguration().getTopics()) ?
        topic :
        getCurrentConfiguration().getTopics().getOrDefault(topic, topic);
  }

  protected int getRequestTimeoutMs() {
    return this.properties.getRequestTimeoutMs();
  }
}
