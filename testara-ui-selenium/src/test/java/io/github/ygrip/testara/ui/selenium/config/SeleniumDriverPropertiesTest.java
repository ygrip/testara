package io.github.ygrip.testara.ui.selenium.config;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.PageLoadStrategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SeleniumDriverPropertiesTest {

  @Test
  void defaultsToNormalPageLoadStrategy() {
    SeleniumDriverProperties properties = new SeleniumDriverProperties();

    assertEquals(PageLoadStrategy.NORMAL, properties.resolvePageLoadStrategy());
  }

  @Test
  void resolvesConfiguredPageLoadStrategyCaseInsensitively() {
    SeleniumDriverProperties properties = new SeleniumDriverProperties();
    properties.setPageLoadStrategy("EAGER");

    assertEquals(PageLoadStrategy.EAGER, properties.resolvePageLoadStrategy());
  }

  @Test
  void supportsNonePageLoadStrategy() {
    SeleniumDriverProperties properties = new SeleniumDriverProperties();
    properties.setPageLoadStrategy("none");

    assertEquals(PageLoadStrategy.NONE, properties.resolvePageLoadStrategy());
  }

  @Test
  void rejectsUnknownPageLoadStrategy() {
    SeleniumDriverProperties properties = new SeleniumDriverProperties();
    properties.setPageLoadStrategy("eventually-ish");

    assertThrows(IllegalArgumentException.class, properties::resolvePageLoadStrategy);
  }
}
