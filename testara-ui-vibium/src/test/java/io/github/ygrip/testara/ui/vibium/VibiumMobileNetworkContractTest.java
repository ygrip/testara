package io.github.ygrip.testara.ui.vibium;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;
import com.vibium.Browser;
import com.vibium.Vibium;
import com.vibium.types.StartOptions;

import io.github.ygrip.testara.core.BaseTests;
import io.github.ygrip.testara.core.TestWith;
import io.github.ygrip.testara.ui.capability.InteractionCapability;
import io.github.ygrip.testara.ui.capability.ObservationCapability;
import io.github.ygrip.testara.ui.driver.DriverSessionManager;
import io.github.ygrip.testara.ui.model.DeviceType;
import io.github.ygrip.testara.ui.model.Locator;
import io.github.ygrip.testara.ui.page.Element;
import io.github.ygrip.testara.ui.vibium.capability.VibiumMediaEmulation;
import io.github.ygrip.testara.ui.vibium.capability.VibiumMobileCapabilityReport;
import io.github.ygrip.testara.ui.vibium.capability.VibiumMobileEmulation;
import io.github.ygrip.testara.ui.vibium.capability.VibiumNetworkSupport;
import io.github.ygrip.testara.ui.vibium.capability.VibiumNetworkSupport.NetworkState;
import io.github.ygrip.testara.ui.vibium.driver.VibiumSession;
import io.github.ygrip.testara.ui.vibium.error.UnsupportedVibiumCapabilityException;

import lombok.extern.log4j.Log4j2;

/**
 * Real headless-browser contract tests for Phase 3 Part C: {@link VibiumMobileEmulation} and
 * {@link VibiumNetworkSupport}, dispatched through the real {@link VibiumSession#capability(Class)}.
 * Mirrors {@code VibiumCapabilityContractTest}'s per-test session bootstrap (own browser/session per
 * test, registered under a name unique to this JVM run) for the same reason documented there:
 * avoiding {@code VibiumEngine#forDriver}'s by-name session cache resurrecting another test's
 * already-closed session.
 */
@Log4j2
@Tag("ui")
@Tag("vibium")
@TestWith(properties = {"classpath:application.properties", "classpath:configuration.properties"})
class VibiumMobileNetworkContractTest extends BaseTests {

  private static final String FIXTURE_HTML = loadFixtureHtml();

  private VibiumSession session;

  private static String loadFixtureHtml() {
    try (var in = VibiumMobileNetworkContractTest.class.getResourceAsStream("/fixtures/vibium-contract.html")) {
      if (in == null) {
        throw new IllegalStateException("fixtures/vibium-contract.html not found on classpath");
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to load vibium-contract.html fixture", e);
    }
  }

  @BeforeEach
  void launchSession() {
    Browser browser = Vibium.start(new StartOptions().headless(true));
    session = new VibiumSession();
    session.using(browser)
      .on(DeviceType.DEFAULT);

    String uniqueDriverName = "vibium-mobile-network-contract-" + System.nanoTime();
    DriverSessionManager.inThisTestThread()
      .registerDriver(uniqueDriverName)
      .forDriver(session);
    DriverSessionManager.inThisTestThread()
      .setCurrentActiveDriver(session);
  }

  @AfterEach
  void closeSession() {
    try {
      DriverSessionManager.tearDown();
    } catch (Exception err) {
      log.warn("Got issue while tearing down driver session manager : {}", err.getMessage());
    }
  }

  private void loadFixture() {
    session.pageForApi()
      .setContent(FIXTURE_HTML);
  }

  private static Element cssElement(String selector) {
    return Element.of(Locator.css(selector))
      .build();
  }

  @Test
  void tapDispatchesARealTouchCompatiblePointerEvent() {
    loadFixture();
    VibiumMobileEmulation mobile = session.capability(VibiumMobileEmulation.class);
    ObservationCapability<?> observation = session.capability(ObservationCapability.class);

    assertThat("touch flag starts untouched", observation.getText(cssElement("#touch-flag")), equalTo("no-touch"));
    mobile.tap(cssElement("#touch-target"));
    assertThat(
      "a real tap() call dispatches a touchstart or pointerdown the fixture can observe",
      observation.getText(cssElement("#touch-flag")),
      equalTo("touched")
    );
  }

  @Test
  void emulateMediaChangesTheRealComputedColorScheme() {
    loadFixture();
    VibiumMobileEmulation mobile = session.capability(VibiumMobileEmulation.class);
    InteractionCapability interaction = session.capability(InteractionCapability.class);

    mobile.emulateMedia(VibiumMediaEmulation.colorScheme("dark"));
    Boolean matchesDark = interaction.executeScript(
      "return window.matchMedia('(prefers-color-scheme: dark)').matches;"
    );
    assertThat("emulateMedia(colorScheme=dark) is reflected by a real matchMedia query", matchesDark, equalTo(true));

    mobile.emulateMedia(VibiumMediaEmulation.colorScheme("light"));
    Boolean matchesLight = interaction.executeScript(
      "return window.matchMedia('(prefers-color-scheme: light)').matches;"
    );
    assertThat("switching the override to light is reflected by a real matchMedia query", matchesLight, equalTo(true));
  }

  @Test
  void setGeolocationIsReflectedByNavigatorGeolocationGetCurrentPosition() {
    // Real end-to-end verification: navigator.geolocation.getCurrentPosition is asynchronous, so
    // this polls the fixture's #geolocation-probe marker (populated by the callback) rather than
    // asserting immediately. Vibium's setGeolocation is a WebDriver BiDi session-level override, not
    // a page permission grant, so it takes effect without a permission prompt (confirmed by this
    // test actually passing rather than timing out on a PERMISSION_DENIED probe value).
    loadFixture();
    VibiumMobileEmulation mobile = session.capability(VibiumMobileEmulation.class);
    InteractionCapability interaction = session.capability(InteractionCapability.class);
    ObservationCapability<?> observation = session.capability(ObservationCapability.class);

    mobile.setGeolocation(37.7749, -122.4194, 10.0);

    interaction.executeScript(
      "navigator.geolocation.getCurrentPosition("
        + "function(pos){document.getElementById('geolocation-probe').textContent = "
        + "pos.coords.latitude + ',' + pos.coords.longitude;},"
        + "function(err){document.getElementById('geolocation-probe').textContent = 'error:' + err.message;});"
    );

    // pollInSameThread() matters here: DriverSessionManager keys the current session by
    // ThreadLocal, and Awaitility polls off the test thread by default, which would otherwise make
    // every capability/resolver call inside the polled condition see no current driver at all.
    Awaitility.await()
      .pollInSameThread()
      .atMost(Duration.ofSeconds(5))
      .pollInterval(Duration.ofMillis(100))
      .until(() -> !"no-position".equals(observation.getText(cssElement("#geolocation-probe"))));

    assertThat(
      "getCurrentPosition reports back exactly the coordinates set via setGeolocation",
      observation.getText(cssElement("#geolocation-probe")),
      equalTo("37.7749,-122.4194")
    );
  }

  @Test
  void capabilityReportHonestlyReflectsTheSupportedAndUnsupportedPrimitives() {
    VibiumMobileEmulation mobile = session.capability(VibiumMobileEmulation.class);

    VibiumMobileCapabilityReport report = mobile.capabilityReport();

    assertThat(report.viewport(), equalTo(true));
    assertThat(report.touch(), equalTo(true));
    assertThat(report.media(), equalTo(true));
    assertThat(report.geolocation(), equalTo(true));
    assertThat("no Java-configurable DPR in this pinned client", report.devicePixelRatio(), equalTo(false));
    assertThat("no user-agent override option in this pinned client", report.userAgentOverride(), equalTo(false));
    assertThat("no named device profile option in this pinned client", report.namedDeviceProfile(), equalTo(false));
  }

  @Test
  void networkStatesReportsLocalProxyUnsupportedForAPlainLocalSession() {
    // NetworkState.INTERCEPTION is deliberately never asserted as present: direct, repeatable
    // manual testing against vibium-26.5.31.jar (see VibiumNetworkSupport's class javadoc and
    // VibiumNetworkSupportImpl#unsupportedRouting) confirmed Page#route(...)'s registered handler
    // is never actually invoked in this pinned client — the paused request just hangs — so this
    // module never reports interception as a genuinely available state.
    VibiumNetworkSupport network = session.capability(VibiumNetworkSupport.class);

    Set<NetworkState> states = network.networkStates();

    assertThat("a plain local session reports outbound proxy as unsupported",
      states, hasItem(NetworkState.LOCAL_PROXY_UNSUPPORTED));
    assertThat("a plain local session never reports EXTERNAL_PROXY",
      states, not(hasItem(NetworkState.EXTERNAL_PROXY)));
    assertThat("interception is never reported as available in this pinned client",
      states, not(hasItem(NetworkState.INTERCEPTION)));
  }

  @Test
  void networkStatesReportsExternalProxyForARemoteConnectedSession() {
    // Lighter/mocked per this task's own instructions: standing up a real second Vibium
    // remote-connect endpoint just to exercise this enum branch is not worth the infrastructure.
    // VibiumSession#markRemoteConnected is the exact (and only) signal VibiumEngine#createSession
    // itself sets from real StartOptions.connectURL configuration (see that class), so flipping it
    // directly here exercises the real reporting logic without needing a second live browser.
    session.markRemoteConnected(true);
    VibiumNetworkSupport network = session.capability(VibiumNetworkSupport.class);

    Set<NetworkState> states = network.networkStates();

    assertThat("a remote-connected session reports EXTERNAL_PROXY, since Testara neither created "
      + "nor can verify any proxy the remote endpoint's owner may have configured",
      states, hasItem(NetworkState.EXTERNAL_PROXY));
    assertThat("a remote-connected session never reports LOCAL_PROXY_UNSUPPORTED",
      states, not(hasItem(NetworkState.LOCAL_PROXY_UNSUPPORTED)));
    assertThat("interception is never reported as available in this pinned client",
      states, not(hasItem(NetworkState.INTERCEPTION)));
  }

  @Test
  void routeAndUnrouteFailFastInsteadOfSilentlyHangingTheNextRealRequest() {
    // See VibiumNetworkSupport's class javadoc for the full empirical finding: three independent,
    // repeatable manual runs against the real jar (fulfill() after navigation, fulfill() before
    // navigation, doContinue() before navigation) all showed the SAME result — Page#route(...)'s
    // registered handler is never actually invoked; the request is genuinely paused (confirmed via
    // a real HTTP fixture server logging zero hits for the matched path) but the Java client never
    // delivers the corresponding event back, so the request (and whatever awaited it) hangs until a
    // native 60s VibiumTimeoutException. Registration itself is a local, non-blocking call and does
    // NOT hang — only a subsequent matched real request does — but shipping route()/unroute() as
    // silently "working" would still hand every caller a capability that hangs the very next
    // real request for a full minute before failing anyway. Failing fast here is the honest choice.
    VibiumNetworkSupport network = session.capability(VibiumNetworkSupport.class);

    assertThrows(UnsupportedVibiumCapabilityException.class, () -> network.route("**", route -> route.doContinue()));
    assertThrows(UnsupportedVibiumCapabilityException.class, () -> network.unroute("**"));
  }

  @Test
  void onRequestAndOnResponseFireForRealNavigationAndFetchTraffic() throws Exception {
    // The genuinely-working half of plan §14's "request interception" line item: passive
    // observation, confirmed via manual testing to fire correctly for both the initial navigation
    // and an in-page fetch() — unlike route()/fulfill(), which is confirmed broken (see the
    // dedicated route/unroute test above).
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", exchange -> {
      byte[] body = "<html><body>network fixture</body></html>".getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders()
        .add("Content-Type", "text/html; charset=utf-8");
      exchange.sendResponseHeaders(200, body.length);
      try (OutputStream out = exchange.getResponseBody()) {
        out.write(body);
      }
    });
    server.start();
    try {
      VibiumNetworkSupport network = session.capability(VibiumNetworkSupport.class);
      InteractionCapability interaction = session.capability(InteractionCapability.class);

      AtomicReference<String> lastRequestUrl = new AtomicReference<>();
      AtomicReference<Integer> lastResponseStatus = new AtomicReference<>();
      network.onRequest(req -> lastRequestUrl.set(req.url()));
      network.onResponse(res -> lastResponseStatus.set(res.status()));

      String baseUrl = "http://127.0.0.1:" + server.getAddress()
        .getPort() + "/";
      session.pageForApi()
        .go(baseUrl);

      Awaitility.await()
        .pollInSameThread()
        .atMost(Duration.ofSeconds(5))
        .pollInterval(Duration.ofMillis(100))
        .until(() -> lastResponseStatus.get() != null);

      assertThat("onRequest observed the real navigation request", lastRequestUrl.get(), equalTo(baseUrl));
      assertThat("onResponse observed the real navigation response status", lastResponseStatus.get(), equalTo(200));

      interaction.executeScript("fetch('/observed-fetch').catch(function(e){});");

      Awaitility.await()
        .pollInSameThread()
        .atMost(Duration.ofSeconds(5))
        .pollInterval(Duration.ofMillis(100))
        .until(() -> lastRequestUrl.get()
          .endsWith("/observed-fetch"));

      assertThat("onRequest also observes an in-page fetch(), not just navigation",
        lastRequestUrl.get(), equalTo(baseUrl + "observed-fetch"));
    } finally {
      server.stop(0);
    }
  }
}
