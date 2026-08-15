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
  void resolvesSupportedPageLoadStrategiesCaseInsensitively() {
    SeleniumDriverProperties properties = new SeleniumDriverProperties();

    properties.setPageLoadStrategy("EAGER");
    assertEquals(PageLoadStrategy.EAGER, properties.resolvePageLoadStrategy());

    properties.setPageLoadStrategy("none");
    assertEquals(PageLoadStrategy.NONE, properties.resolvePageLoadStrategy());

    properties.setPageLoadStrategy(" normal ");
    assertEquals(PageLoadStrategy.NORMAL, properties.resolvePageLoadStrategy());
  }

  @Test
  void rejectsUnsupportedPageLoadStrategy() {
    SeleniumDriverProperties properties = new SeleniumDriverProperties();
    properties.setPageLoadStrategy("eventually");

    IllegalArgumentException error = assertThrows(
      IllegalArgumentException.class,
      properties::resolvePageLoadStrategy
    );

    assertEquals(
      "Unsupported Selenium page load strategy 'eventually'. Supported values: normal, eager, none",
      error.getMessage()
    );
  }
}
