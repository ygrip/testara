package io.github.ygrip.testara.ui.vibium;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

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
import io.github.ygrip.testara.ui.capability.AssertionCapability;
import io.github.ygrip.testara.ui.capability.InteractionCapability;
import io.github.ygrip.testara.ui.capability.NavigationCapability;
import io.github.ygrip.testara.ui.capability.ObservationCapability;
import io.github.ygrip.testara.ui.capability.WaitCapability;
import io.github.ygrip.testara.ui.driver.DriverSessionManager;
import io.github.ygrip.testara.ui.model.CapturedCookie;
import io.github.ygrip.testara.ui.model.DeviceType;
import io.github.ygrip.testara.ui.model.Locator;
import io.github.ygrip.testara.ui.page.Element;
import io.github.ygrip.testara.ui.vibium.driver.VibiumSession;
import io.github.ygrip.testara.ui.vibium.error.UnsupportedVibiumCapabilityException;
import io.github.ygrip.testara.ui.vibium.error.VibiumOperationException;

import lombok.extern.log4j.Log4j2;

/**
 * Real headless-browser contract tests for Phase 3 Part A: {@link
 * io.github.ygrip.testara.ui.vibium.capability.VibiumNavigationCapability}, {@link
 * io.github.ygrip.testara.ui.vibium.capability.VibiumWaitCapability}, and {@link
 * io.github.ygrip.testara.ui.vibium.capability.VibiumAssertionCapability}, dispatched through the
 * real {@link VibiumSession#capability(Class)}. Each test launches its own real browser/session
 * (mirrors {@code VibiumSessionLifecycleTest}'s style) and registers it under a name unique to
 * this JVM run — deliberately bypassing {@code VibiumEngine#forDriver}'s by-name session cache in
 * {@link DriverSessionManager}, which is shared (thread-local) across every test class in this
 * module's Surefire run: reusing a fixed name like {@code "chrome"} across many tests here would
 * risk one test resurrecting another, already-closed test's stale session instead of getting its
 * own fresh one.
 */
@Log4j2
@Tag("ui")
@Tag("vibium")
@TestWith(properties = {"classpath:application.properties", "classpath:configuration.properties"})
class VibiumCapabilityContractTest extends BaseTests {

  private static final String FIXTURE_HTML = loadFixtureHtml();

  private VibiumSession session;

  private static String loadFixtureHtml() {
    try (var in = VibiumCapabilityContractTest.class.getResourceAsStream("/fixtures/vibium-contract.html")) {
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

    String uniqueDriverName = "vibium-capability-contract-" + System.nanoTime();
    DriverSessionManager.inThisTestThread()
      .registerDriver(uniqueDriverName)
      .forDriver(session);
    DriverSessionManager.inThisTestThread()
      .setCurrentActiveDriver(session);
  }

  @AfterEach
  void closeSession() {
    // Full teardown (close + drop the thread-local driver registry), not just close(): keeps this
    // test's uniquely-named registration from lingering in DriverSessionManager for later tests
    // (in this or any other class sharing the Surefire test thread) to see.
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
  void navigateBackAndForwardAndReloadWorkAgainstRealHistory() {
    NavigationCapability nav = session.capability(NavigationCapability.class);

    nav.to("data:text/html,<title>PageA</title><h1 id=pageA>A</h1>");
    String urlA = nav.getCurrentUrl();
    assertThat("title reflects page A", nav.getTitle(), equalTo("PageA"));

    nav.to("data:text/html,<title>PageB</title><h1 id=pageB>B</h1>");
    String urlB = nav.getCurrentUrl();
    assertThat("navigated to a different url", urlB, not(equalTo(urlA)));
    assertThat("title reflects page B", nav.getTitle(), equalTo("PageB"));

    nav.back();
    assertThat("back() returns to page A", nav.getCurrentUrl(), equalTo(urlA));

    nav.forward();
    assertThat("forward() returns to page B", nav.getCurrentUrl(), equalTo(urlB));

    nav.reload();
    assertThat("reload() stays on page B", nav.getCurrentUrl(), equalTo(urlB));
  }

  @Test
  void tabOpenSwitchCloseAndCountReflectRealBrowserState() {
    NavigationCapability nav = session.capability(NavigationCapability.class);
    int initialCount = nav.getTabCount();

    nav.openNewTab("data:text/html,<h1 id=tab2>Tab2</h1>");
    assertThat("tab count increased after opening a new tab", nav.getTabCount(), equalTo(initialCount + 1));

    // Browser#pages() ordering is not guaranteed stable across bringToFront() calls, so each
    // expected url is read fresh immediately before switching to that index, rather than assumed
    // from an earlier snapshot.
    for (int index = 0; index < nav.getTabCount(); index++) {
      String expectedUrl = session.instance()
        .pages()
        .get(index)
        .url();
      nav.switchToTab(index);
      assertThat("switching to index " + index + " lands on that tab's real url", nav.getCurrentUrl(), equalTo(expectedUrl));
    }

    nav.closeTab();
    assertThat("tab count back to initial after closing", nav.getTabCount(), equalTo(initialCount));
  }

  @Test
  void switchToTabOutOfRangeThrowsVibiumOperationException() {
    NavigationCapability nav = session.capability(NavigationCapability.class);

    VibiumOperationException thrown = assertThrows(VibiumOperationException.class, () -> nav.switchToTab(99));
    assertThat(thrown.getMessage(), containsString("switchToTab"));
  }

  @Test
  void waitCapabilitySucceedsForEachSupportedStateTransition() {
    loadFixture();
    WaitCapability wait = session.capability(WaitCapability.class)
      .withTimeout(Duration.ofSeconds(3));

    // Native waitUntil("visible"/"hidden") — confirmed real server-side support.
    wait.untilVisible(cssElement("#toggle-visibility"));
    wait.untilInvisible(cssElement("#toggle-hide"));
    // No native "attached" round-trip needed: resolution succeeding already proves presence.
    wait.untilPresent(cssElement("#dynamic-present"));
    // No native state for these — Awaitility-polled isEnabled()/isVisible()/isChecked().
    wait.untilEnabled(cssElement("#toggle-enable"));
    wait.untilDisabled(cssElement("#always-disabled"));
    wait.untilClickable(cssElement("#always-enabled"));
    wait.untilSelected(cssElement("#toggle-checkbox"));
  }

  @Test
  void untilVisibleThrowsVibiumOperationExceptionOnGenuineTimeout() {
    loadFixture();
    WaitCapability wait = session.capability(WaitCapability.class)
      .withTimeout(Duration.ofMillis(500));

    VibiumOperationException thrown = assertThrows(
      VibiumOperationException.class,
      () -> wait.untilVisible(cssElement("#always-hidden"))
    );
    assertThat("message reports the failed operation", thrown.getMessage(), containsString("untilVisible"));
    assertThat("message reports the timeout actually used", thrown.getMessage(), containsString("500"));
    assertThat("original vibium error is preserved as the cause", thrown.getCause(), not(equalTo(null)));
  }

  @Test
  void untilEnabledThrowsVibiumOperationExceptionOnGenuineTimeout() {
    loadFixture();
    WaitCapability wait = session.capability(WaitCapability.class)
      .withTimeout(Duration.ofMillis(500));

    assertThrows(VibiumOperationException.class, () -> wait.untilEnabled(cssElement("#always-disabled")));
  }

  @Test
  void seeThatTextPassesAndFailsCorrectly() {
    loadFixture();
    AssertionCapability assertion = session.capability(AssertionCapability.class);

    assertion.seeThatText(cssElement("#marker"), "Vibium Contract Fixture");

    AssertionError failure = assertThrows(
      AssertionError.class,
      () -> assertion.seeThatText(cssElement("#marker"), "wrong text")
    );
    assertThat("failure reports expected value", failure.getMessage(), containsString("wrong text"));
    assertThat("failure reports actual value", failure.getMessage(), containsString("Vibium Contract Fixture"));
  }

  @Test
  void hasClassAndSeeThatVisiblePassAndFail() {
    loadFixture();
    AssertionCapability assertion = session.capability(AssertionCapability.class);

    assertion.hasClass(cssElement("#submit-button"), "primary");
    assertThrows(AssertionError.class, () -> assertion.hasClass(cssElement("#submit-button"), "no-such-class"));

    assertion.seeThatVisible(cssElement("#marker"));
    assertThrows(AssertionError.class, () -> assertion.seeThatVisible(cssElement("#always-hidden")));
  }

  @Test
  void isVisibleReturnsFalseRatherThanThrowingForAMissingElement() {
    loadFixture();
    AssertionCapability assertion = session.capability(AssertionCapability.class);

    assertThat(
      "a locator with zero matches is reported as not-visible, not as an operational failure",
      assertion.isVisible(cssElement("#does-not-exist-anywhere")),
      equalTo(false)
    );
  }

  @Test
  void discoveryOnlyXpathElementRejectsCapabilityUseRatherThanSilentlySucceeding() {
    loadFixture();
    AssertionCapability assertion = session.capability(AssertionCapability.class);
    Element xpathMarker = Element.of(Locator.xpath("//h1[@id='marker']"))
      .build();

    assertThrows(UnsupportedVibiumCapabilityException.class, () -> assertion.seeThatText(xpathMarker, "whatever"));
    assertThrows(UnsupportedVibiumCapabilityException.class, () -> assertion.isVisible(xpathMarker));

    WaitCapability wait = session.capability(WaitCapability.class)
      .withTimeout(Duration.ofSeconds(1));
    assertThrows(UnsupportedVibiumCapabilityException.class, () -> wait.untilVisible(xpathMarker));
  }

  // ── Phase 3 Part B: InteractionCapability / ObservationCapability ─────────────────────────

  @Test
  void clickChangesRealDomState() {
    loadFixture();
    InteractionCapability interaction = session.capability(InteractionCapability.class);
    ObservationCapability<?> observation = session.capability(ObservationCapability.class);

    assertThat(observation.getText(cssElement("#click-toggle-target")), equalTo("not-clicked"));
    interaction.click(cssElement("#click-toggle-btn"));
    assertThat(observation.getText(cssElement("#click-toggle-target")), equalTo("clicked"));
  }

  @Test
  void enterClearAndValueRoundTripThroughARealInput() {
    loadFixture();
    InteractionCapability interaction = session.capability(InteractionCapability.class);
    ObservationCapability<?> observation = session.capability(ObservationCapability.class);
    Element input = cssElement("#text-input");

    assertThat(observation.getValue(input), equalTo("initial"));

    interaction.enter("replaced-value")
      .into(input);
    assertThat("enter().into() replaces rather than appends (clear() then fill(), per plan §12)",
      observation.getValue(input), equalTo("replaced-value"));

    interaction.clear(input);
    assertThat(observation.getValue(input), equalTo(""));
  }

  @Test
  void selectOptionByValueIndexAndVisibleTextChangeTheRealSelectedOption() {
    loadFixture();
    InteractionCapability interaction = session.capability(InteractionCapability.class);
    ObservationCapability<?> observation = session.capability(ObservationCapability.class);
    Element select = cssElement("#fruit-select");

    interaction.selectOption(select)
      .byValue("banana-value");
    assertThat(observation.getValue(select), equalTo("banana-value"));

    interaction.selectOption(select)
      .byIndex(2);
    assertThat(observation.getValue(select), equalTo("cherry-value"));

    interaction.selectOption(select)
      .byVisibleText("Apple");
    assertThat(observation.getValue(select), equalTo("apple-value"));
  }

  @Test
  void dragDoesNotThrowAgainstRealResolvedElements() {
    // No assertion on an actual drop-transfer outcome: native HTML5 drag-and-drop depends on
    // synthetic DragEvent dispatch, which a raw mouse press/move/release sequence (all that
    // Element#dragTo/Page#mouse() genuinely drive, per the Phase 0 spike and this class's own
    // javadoc) does not reliably trigger in a headless browser. Asserting a real drop outcome here
    // would be a flaky test against browser/DnD internals this module does not control. This still
    // exercises both real adapted code paths (native dragTo, and the manual mouse offset path)
    // end-to-end against real resolved elements and confirms neither throws.
    loadFixture();
    InteractionCapability interaction = session.capability(InteractionCapability.class);

    interaction.drag(cssElement("#drag-source"), cssElement("#drag-target"));
    interaction.drag(cssElement("#drag-source"), 50, 0);
  }

  @Test
  void submitCallsOwningFormRequestSubmitRatherThanARawEnterKeypress() {
    loadFixture();
    InteractionCapability interaction = session.capability(InteractionCapability.class);
    ObservationCapability<?> observation = session.capability(ObservationCapability.class);

    assertThat(observation.getText(cssElement("#form-submitted")), equalTo("not-submitted"));
    interaction.submit(cssElement("#submit-input"));
    assertThat(observation.getText(cssElement("#form-submitted")), equalTo("submitted"));
  }

  @Test
  void blurRemovesFocusFromTheRealActiveElement() {
    loadFixture();
    InteractionCapability interaction = session.capability(InteractionCapability.class);

    interaction.focus(cssElement("#blur-input"));
    Boolean focusedBefore = interaction.executeScript(
      "return document.activeElement != null && document.activeElement.id === 'blur-input';"
    );
    assertThat(focusedBefore, equalTo(true));

    interaction.blur(cssElement("#blur-input"));
    Boolean focusedAfter = interaction.executeScript(
      "return document.activeElement != null && document.activeElement.id === 'blur-input';"
    );
    assertThat(focusedAfter, equalTo(false));
  }

  @Test
  void getCssValueReadsARealComputedStyle() {
    loadFixture();
    ObservationCapability<?> observation = session.capability(ObservationCapability.class);

    assertThat(observation.getCssValue(cssElement("#css-color-box"), "color"), equalTo("rgb(255, 0, 0)"));
  }

  @Test
  void executeScriptReturnsARealComputedValue() {
    loadFixture();
    InteractionCapability interaction = session.capability(InteractionCapability.class);

    Number withoutArgs = interaction.executeScript("return 21 * 2;");
    assertThat(withoutArgs.intValue(), equalTo(42));

    Number withArgs = interaction.executeScript("return arguments[0] + arguments[1];", 10, 32);
    assertThat(withArgs.intValue(), equalTo(42));
  }

  @Test
  void executeScriptAsyncThrowsUnsupportedVibiumCapabilityException() {
    InteractionCapability interaction = session.capability(InteractionCapability.class);

    assertThrows(UnsupportedVibiumCapabilityException.class, () -> interaction.executeScriptAsync("return 1;"));
  }

  @Test
  void allFourScreenshotVariantsProduceNonEmptyValidPngs() {
    loadFixture();
    ObservationCapability<?> observation = session.capability(ObservationCapability.class);

    assertValidPng(observation.capturePage()
      .visibleOnViewPort());
    assertValidPng(observation.capturePage()
      .fullPage());
    assertValidPng(observation.captureElement(cssElement("#marker")));
    assertValidPng(observation.captureRegion(0, 0, 100, 100));
  }

  private static void assertValidPng(byte[] data) {
    assertThat("screenshot bytes are present", data, not(equalTo(null)));
    assertThat("screenshot has at least a PNG header", data.length > 8, equalTo(true));
    assertThat(
      "screenshot starts with the real PNG magic header",
      (data[0] & 0xFF) == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G',
      equalTo(true)
    );
  }

  @Test
  void cookieSetThenReadRoundTripsThroughTheRealBrowserContext() throws Exception {
    // Cookies do not work against about:blank/setContent() pages (Phase 0 finding), so this test
    // spins up a tiny real local HTTP fixture server rather than reusing loadFixture().
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", exchange -> {
      byte[] body = "<html><body>cookie fixture</body></html>".getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders()
        .add("Content-Type", "text/html; charset=utf-8");
      exchange.sendResponseHeaders(200, body.length);
      try (OutputStream out = exchange.getResponseBody()) {
        out.write(body);
      }
    });
    server.start();
    try {
      session.pageForApi()
        .go("http://127.0.0.1:" + server.getAddress()
          .getPort() + "/");

      InteractionCapability interaction = session.capability(InteractionCapability.class);
      ObservationCapability<?> observation = session.capability(ObservationCapability.class);

      interaction.executeScript("document.cookie = 'testara_cookie=testara_value; path=/';");

      CapturedCookie found = observation.getCookieNamed("testara_cookie");
      assertThat("cookie set via document.cookie is readable by name", found, not(equalTo(null)));
      assertThat(found.getValue(), equalTo("testara_value"));

      List<CapturedCookie> all = observation.getCookies();
      assertThat(
        "getCookies() includes the cookie just set",
        all.stream()
          .anyMatch(cookie -> "testara_cookie".equals(cookie.getName())),
        equalTo(true)
      );
    } finally {
      server.stop(0);
    }
  }

  @Test
  void findOneAndFindElementRejectADiscoveryOnlyElementRatherThanReturningABrokenHandle() {
    loadFixture();
    ObservationCapability<?> observation = session.capability(ObservationCapability.class);
    InteractionCapability interaction = session.capability(InteractionCapability.class);
    Element xpathMarker = Element.of(Locator.xpath("//h1[@id='marker']"))
      .build();

    assertThrows(UnsupportedVibiumCapabilityException.class, () -> observation.findOne(xpathMarker));
    assertThrows(UnsupportedVibiumCapabilityException.class, () -> interaction.findElement(xpathMarker));

    Object found = observation.findOne(cssElement("#marker"));
    assertThat("a CSS-derived (interaction-safe) locator returns the real native handle",
      found, not(equalTo(null)));
    assertThat(found.getClass()
      .getName(), equalTo("com.vibium.Element"));
  }

  // ── Phase 3 Part C: stale-handle regression (plan §18.3) ──────────────────────────────────

  @Test
  void staleElementHandleThrowsAfterDomReplacementThenReResolvingTransparentlyTargetsTheNewNode() {
    // Phase 0's spike already found this raw-API behavior; this is the first Testara-capability-
    // layer regression test for it (plan §18.3 "stale handles are not reused after DOM
    // replacement"). com.vibium.Element holds a stored selector/index rather than a live node
    // reference (confirmed via javap — no live-handle field exists), so the real sequence is two
    // steps, not one: (1) with the matching element removed and NOTHING re-added yet, a call on the
    // already-resolved old handle throws a native ElementNotFoundException; (2) once a new element
    // matching the SAME selector is re-added, that SAME old handle transparently resolves to the
    // new node instead of continuing to throw — it does not remember the original node's identity.
    // (A single "remove-and-replace-in-one-click" button, tried first, never observably throws: by
    // the time any assertion runs, a matching element already exists again, so the old handle's
    // selector re-lookup simply succeeds against the new node.)
    loadFixture();
    ObservationCapability<?> observation = session.capability(ObservationCapability.class);
    InteractionCapability interaction = session.capability(InteractionCapability.class);
    Element replaceableLocator = cssElement("#replaceable-node");

    Object oldHandle = observation.findOne(replaceableLocator);
    assertThat(((com.vibium.Element) oldHandle).text(), equalTo("Original Node"));

    interaction.click(cssElement("#remove-node-btn"));

    assertThrows(
      com.vibium.errors.ElementNotFoundException.class,
      () -> ((com.vibium.Element) oldHandle).text(),
      "an old handle must not resolve to nothing/stale data once its match is genuinely gone"
    );

    interaction.click(cssElement("#readd-node-btn"));

    assertThat(
      "the SAME old handle transparently targets the freshly-added matching node, rather than "
        + "staying stuck on the original (now-gone) node identity",
      ((com.vibium.Element) oldHandle).text(),
      equalTo("Replaced Node")
    );
    assertThat(
      "re-resolving the same locator fresh through the capability layer also sees the new node",
      observation.getText(replaceableLocator),
      equalTo("Replaced Node")
    );
  }

  @Test
  void discoveryOnlyXpathElementRejectsNewInteractionAndObservationMethods() {
    loadFixture();
    InteractionCapability interaction = session.capability(InteractionCapability.class);
    ObservationCapability<?> observation = session.capability(ObservationCapability.class);
    Element xpathMarker = Element.of(Locator.xpath("//h1[@id='marker']"))
      .build();

    assertThrows(UnsupportedVibiumCapabilityException.class, () -> interaction.click(xpathMarker));
    assertThrows(UnsupportedVibiumCapabilityException.class, () -> observation.getText(xpathMarker));
    assertThrows(
      UnsupportedVibiumCapabilityException.class,
      () -> interaction.selectOption(xpathMarker)
        .byValue("whatever")
    );
  }
}
