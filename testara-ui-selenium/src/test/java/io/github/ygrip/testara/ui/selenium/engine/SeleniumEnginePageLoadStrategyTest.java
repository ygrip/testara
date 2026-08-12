package io.github.ygrip.testara.ui.selenium.engine;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.MutableCapabilities;

import io.github.ygrip.testara.ui.selenium.config.SeleniumDriverProperties;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SeleniumEnginePageLoadStrategyTest {

  @Test
  void appliesConfiguredStrategyToCapabilities() {
    SeleniumDriverProperties properties = new SeleniumDriverProperties();
    properties.setPageLoadStrategy("eager");
    MutableCapabilities capabilities = new MutableCapabilities();

    SeleniumEngine.applyPageLoadStrategy(capabilities, properties);

    assertEquals("eager", capabilities.getCapability("pageLoadStrategy"));
  }

  @Test
  void appliesNormalWhenConfigurationIsMissing() {
    MutableCapabilities capabilities = new MutableCapabilities();

    SeleniumEngine.applyPageLoadStrategy(capabilities, null);

    assertEquals("normal", capabilities.getCapability("pageLoadStrategy"));
  }

  @Test
  void preservesGenericCapabilityWhenDedicatedPropertyIsNotConfigured() {
    SeleniumDriverProperties properties = new SeleniumDriverProperties();
    MutableCapabilities capabilities = new MutableCapabilities();
    capabilities.setCapability("pageLoadStrategy", "eager");

    SeleniumEngine.applyPageLoadStrategy(capabilities, properties);

    assertEquals("eager", capabilities.getCapability("pageLoadStrategy"));
  }

  @Test
  void dedicatedPropertyOverridesGenericCapability() {
    SeleniumDriverProperties properties = new SeleniumDriverProperties();
    properties.setPageLoadStrategy("none");
    MutableCapabilities capabilities = new MutableCapabilities();
    capabilities.setCapability("pageLoadStrategy", "eager");

    SeleniumEngine.applyPageLoadStrategy(capabilities, properties);

    assertEquals("none", capabilities.getCapability("pageLoadStrategy"));
  }
}
