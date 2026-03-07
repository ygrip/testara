package io.github.ygrip.testara.core.model;

import io.github.ygrip.testara.core.config.LoadProperties;
import lombok.Data;

import java.time.Duration;
import java.util.Set;

/**
 * <p>DefaultProperties class.</p>
 *
 * @author yunaz.ramadhan on 1/21/2020
 * @version $Id: $Id
 */
@Data
@LoadProperties(prefix = "automation.config")
public class DefaultProperties {
  private String targetDateFormat = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";
  private String sourceDateFormat = "yyyy-MM-dd HH:mm:ss";
  private String templateFolder = "/src/test/resources/templates/request/body/";
  private String schemaFolder = "/src/test/resources/schemas/";
  private String scriptFolder = "/src/test/resources/templates/script/";
  private String timeZone;
  private String userName = "user";
  private Duration retryableTimeout = Duration.ofSeconds(10);
  private Duration retryableInterval = Duration.ofSeconds(1);
  private Set<String> retryableScanLocation = Set.of("io.github.ygrip.testara");
  private Boolean resetRequestAfterEachScenario = false;
  private Boolean resetResponseAfterEachScenario = false;
}
