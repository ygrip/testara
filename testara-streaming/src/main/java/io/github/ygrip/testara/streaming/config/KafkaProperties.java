package io.github.ygrip.testara.streaming.config;

import io.github.ygrip.testara.core.config.LoadProperties;
import io.github.ygrip.testara.streaming.model.KafkaModel;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * <p>KafkaProperties class.</p>
 *
 * @author yunaz.ramadhan on 10/4/2019
 * @version $Id: $Id
 */
@Data
@LoadProperties(prefix = "kafka")
public class KafkaProperties {
  private Map<String, KafkaModel> service = new HashMap<>();
  private Integer sessionTimeoutMs = 60000;
  private Integer requestTimeoutMs = 60000;
  private Integer heartBeatIntervalMs = 1000;
  private Integer pollIntervalMs = 300;
  private Integer maxPollRecords = 1000;
  private Integer maxBytes = 10485760;
  private boolean enableAutoCommit = false;
  private String autoOffsetReset = "latest";
}
