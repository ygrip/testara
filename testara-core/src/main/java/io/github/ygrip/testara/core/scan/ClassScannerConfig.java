package io.github.ygrip.testara.core.scan;

import io.github.ygrip.testara.core.config.LoadProperties;
import io.github.ygrip.testara.core.context.TestComponent;
import io.github.ygrip.testara.core.registry.RegistryScope;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@TestComponent(scope = RegistryScope.GLOBAL)
@LoadProperties(prefix = "class.loader")
public final class ClassScannerConfig {

  private final int bufferSize;
  private final boolean enableParallelScanning;
  private final Set<String> rejectPackages;
  private final Set<String> defaultScanLocations;
  private final Map<String, Set<String>> scanLocations;

  public ClassScannerConfig(int bufferSize,
      boolean enableParallelScanning,
      Set<String> rejectPackages,
      Set<String> defaultScanLocations,
      Map<String, Set<String>> scanLocations) {
    this.bufferSize = bufferSize;
    this.enableParallelScanning = enableParallelScanning;
    if (ObjectUtils.isEmpty(rejectPackages)) {
      this.rejectPackages = defaultRejectPackages();
    } else {
      rejectPackages.addAll(defaultRejectPackages());
      this.rejectPackages = rejectPackages;
    }
    this.defaultScanLocations = defaultScanLocations;
    this.scanLocations = scanLocations;
  }

  private static Set<String> defaultRejectPackages() {
    return new HashSet<>(Arrays.asList("com.sun.*",
        "java.*",
        "javax.*",
        "jdk.*",
        "sun.*",
        "io.netty.*",
        "org.springframework.*",
        "net.bytebuddy.*",
        "com.fasterxml.*",
        "org.apache.*",
        "org.junit.*",
        "org.hamcrest.*",
        "org.mockito.*",
        "com.google.*",
        "org.slf4j.*",
        "ch.qos.logback.*",
        "org.seleniumhq.*",
        "net.serenitybdd.*",
        "io.restassured.*",
        "com.browserup.*",
        "org.json.*",
        "org.yaml.*",
        "com.jayway.*",
        "org.objenesis.*",
        "net.sf.*",
        "org.w3c.*",
        "org.xml.*",
        "com.squareup.*",
        "okhttp3.*",
        "retrofit2.*",
        "com.github.*",
        "io.github.classgraph.*",
        "io.github.bonigarcia.*",
        "org.jetbrains.*",
        "kotlin.*",
        "kotlinx.*",
        "org.hibernate.*",
        "jakarta.*",
        "com.fasterxml.jackson.*",
        "org.apache.commons.*",
        "scala.*"));
  }

  public int maxBuffer() {
    return bufferSize;
  }

  public boolean enableParallelScanning() {
    return enableParallelScanning;
  }

  public Set<String> rejectPackages() {
    return rejectPackages;
  }

  private Set<String> defaultScanLocations() {
    return defaultScanLocations;
  }

  public Set<String> scanLocations(String key) {
    return StringUtils.isBlank(key) ?
        defaultScanLocations() :
        ObjectUtils.isEmpty(this.scanLocations) ?
            defaultScanLocations() :
            this.scanLocations.getOrDefault(key, defaultScanLocations());
  }
}

