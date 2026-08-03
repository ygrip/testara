package io.github.ygrip.testara.ui.vibium;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.ygrip.testara.core.BaseTests;
import io.github.ygrip.testara.core.TestWith;
import io.github.ygrip.testara.ui.context.TestUI;
import io.github.ygrip.testara.ui.driver.AbstractDriver;
import io.github.ygrip.testara.ui.driver.DriverSessionManager;
import io.github.ygrip.testara.ui.playwright.engine.PlaywrightEngine;
import io.github.ygrip.testara.ui.selenium.engine.SeleniumEngine;
import io.github.ygrip.testara.ui.vibium.driver.VibiumChromium;
import io.github.ygrip.testara.ui.vibium.driver.VibiumSession;
import io.github.ygrip.testara.ui.vibium.engine.VibiumEngine;
import io.github.ygrip.testara.ui.vibium.error.UnsupportedVibiumCapabilityException;

import lombok.extern.log4j.Log4j2;

/**
 * Phase 1 discovery/lifecycle proof for the Vibium engine, plus the multi-engine driver-cache
 * regression required by plan section 6.1: {@link io.github.ygrip.testara.ui.factory.EngineFactory#DRIVERS}
 * is keyed per engine {@code Class}, so Selenium, Playwright, and Vibium must each resolve their
 * own, independently populated driver map in the same JVM.
 */
@Log4j2
@Tag("ui")
@Tag("vibium")
@TestWith(properties = {"classpath:application.properties", "classpath:configuration.properties"})
public class VibiumEngineDiscoveryTest extends BaseTests {

  @Test
  void resolvesChromeDriverMetadata() {
    VibiumEngine engine = new VibiumEngine();

    Map<String, Class<? extends AbstractDriver<?, ?>>> drivers = engine.loadDrivers();

    assertThat("vibium id", engine.id(), equalTo("vibium"));
    assertThat("chrome driver registered", drivers, hasKey("chrome"));
    assertThat("chrome driver class", drivers.get("chrome"), equalTo(VibiumChromium.class));
  }

  @Test
  void threeWayEngineDiscoveryPopulatesIndependentDriverMaps() {
    SeleniumEngine selenium = new SeleniumEngine();
    PlaywrightEngine playwright = new PlaywrightEngine();
    VibiumEngine vibium = new VibiumEngine();

    Map<String, Class<? extends AbstractDriver<?, ?>>> seleniumDrivers = selenium.loadDrivers();
    Map<String, Class<? extends AbstractDriver<?, ?>>> playwrightDrivers = playwright.loadDrivers();
    Map<String, Class<? extends AbstractDriver<?, ?>>> vibiumDrivers = vibium.loadDrivers();

    assertThat("selenium drivers non-empty", seleniumDrivers.isEmpty(), is(false));
    assertThat("playwright drivers non-empty", playwrightDrivers.isEmpty(), is(false));
    assertThat("vibium drivers non-empty", vibiumDrivers.isEmpty(), is(false));

    assertThat("vibium only lists its own chrome driver", vibiumDrivers.get("chrome"), equalTo(VibiumChromium.class));
    assertThat("selenium driver map is not the vibium driver map", seleniumDrivers, not(equalTo(vibiumDrivers)));
    assertThat("playwright driver map is not the vibium driver map", playwrightDrivers, not(equalTo(vibiumDrivers)));
  }

  @Test
  void launchesAndClosesRealHeadlessSession() throws Exception {
    try (VibiumSession session = TestUI.with("vibium")
      .forDriver("chrome")) {
      assertThat("session active right after launch", session.isActive(), is(true));
      assertThat("instance() returns the raw Vibium Browser", session.instance(), is(notNullValue()));
    }
  }

  @Test
  void requestingARealOutboundProxyFailsAtSessionCreationBeforeAnyBrowserOrPageExists() {
    // Plan §18.3: "requesting a real outbound proxy ... fails before navigation" — stronger still,
    // this must fail before a browser/page is even created, not merely before the first
    // interaction. VibiumChromium#proxyOptions() throws UnsupportedVibiumCapabilityException, and
    // VibiumEngine#createSession() calls it (when a proxyType is requested) strictly before
    // factory.create(options)/Vibium.start(...), so no VibiumSession is ever registered.
    assertThrows(
      UnsupportedVibiumCapabilityException.class,
      () -> TestUI.with("vibium")
        .forDriver("chrome", null, "STANDALONE")
    );

    // constructDriverName("chrome", DeviceType.DEFAULT, AvailableProxy.STANDALONE) — confirms no
    // VibiumSession was ever registered under this key, i.e. creation failed strictly before
    // VibiumEngine#forDriver's post-creation DriverSessionManager registration step.
    assertThat(
      "no session was registered under the failed call's driver key",
      DriverSessionManager.inThisTestThread()
        .getDriver("chrome-default-standalone"),
      is(nullValue())
    );
  }

  @AfterEach
  public void afterEach() {
    try {
      DriverSessionManager.inThisTestThread()
        .getCurrentDriver()
        .close();
    } catch (Exception err) {
      log.warn("Got issue while closing active driver : {}", err.getMessage());
    }
  }
}
