package io.github.ygrip.testara.streaming.model;

import lombok.Data;

import java.util.Map;

/**
 * <p>KafkaModel class.</p>
 *
 * @author yunaz.ramadhan on 10/4/2019
 * @version $Id: $Id
 */
@Data
public class KafkaModel {
  private String servers;
  private String groupId;
  private Long maxConsumerLivenessInMilis = 60000L;
  private Map<String, String> topics;
}
