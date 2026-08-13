package io.github.ygrip.testara.reporter.config;

import java.util.LinkedHashMap;
import java.util.Map;

import io.github.ygrip.testara.core.config.LoadProperties;
import lombok.Data;

@Data
@LoadProperties(prefix = "testara.report")
public class ReportConfiguration {
  private Map<String, Object> customFields = new LinkedHashMap<>();
  private String style = "modern";
  private String organizationName;
  private String organizationLogo;
  private String organizationDetail;
}
