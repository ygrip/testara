package io.github.ygrip.testara.ui.config;

import java.util.HashSet;
import java.util.Set;

import io.github.ygrip.testara.core.config.LoadProperties;

import lombok.Data;

@Data
@LoadProperties(prefix = "automation.engine")
public class EngineProperties {
  private String defaultEngine;
  private Set<String> activeEngines = new HashSet<>();
}
