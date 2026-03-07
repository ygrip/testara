package io.github.ygrip.testara.streaming.model;

import lombok.Data;

/**
 * <p>KafkaMetaData class.</p>
 *
 * @author yunaz.ramadhan on 1/1/2021
 * @version $Id: $Id
 */
@Data
public class KafkaMetaData<T> {
  private Integer partition;
  private Long offset;
  private String topic;
  private String key;
  private Long timestamp;
  private T value;
}
