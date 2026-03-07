package io.github.ygrip.testara.ui.config;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.ygrip.testara.ui.model.DeviceType;

import lombok.Data;

@Data
public abstract class AbstractDriverProperties {
  private String name;
  private String owner;
  private boolean remote;
  private boolean headless;
  private boolean containerized;
  private Set<String> scanLocations = new HashSet<>(Collections.singletonList("io.github.ygrip.testara"));
  private Set<String> pageScanLocations = new HashSet<>(Collections.singletonList("io.github.ygrip.testara"));
  private Set<String> actionScanLocations = new HashSet<>(Collections.singletonList("io.github.ygrip.testara"));
}
