package io.github.ygrip.testara.streaming.model;

import lombok.Data;

/**
 * <p>KafkaMessage class.</p>
 *
 * @author yunaz.ramadhan on 1/10/2020
 * @version $Id: $Id
 */
@Data
public class KafkaMessage {
  private String topic;
  private String key;
  private Object message;
}
