package io.github.ygrip.testara.ui.vibium;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

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
import io.github.ygrip.testara.ui.model.CapturedCookie;
import io.github.ygrip.testara.ui.model.DeviceType;
import io.github.ygrip.testara.ui.vibium.driver.VibiumSession;

import lombok.extern.log4j.Log4j2;

/**
 * Two {@link VibiumSession}s created independently in the same test must never bleed active-page,
 * finder, or lifecycle state into each other. {@link VibiumSession#finder()} deliberately
 * constructs and caches its own {@code VibiumPageFinder} per session (see that method's javadoc)
 * rather than resolving a shared {@code RegistryScope.TEST}-cached instance, so this also
 * regression-tests that the two sessions don't end up sharing the same finder.
 */
@Log4j2
@Tag("ui")
@Tag("vibium")
@TestWith(properties = {"classpath:application.properties", "classpath:configuration.properties"})
class VibiumSessionIsolationTest extends BaseTests {

  @Test
  void twoIndependentSessionsDoNotBleedActivePageOrFinderState() throws Exception {
    Browser browserA = Vibium.start(new StartOptions().headless(true));
    Browser browserB = Vibium.start(new StartOptions().headless(true));

    VibiumSession sessionA = new VibiumSession();
    VibiumSession sessionB = new VibiumSession();
    try {
      sessionA.using(browserA).on(DeviceType.DEFAULT);
      sessionB.using(browserB).on(DeviceType.DEFAULT);

      sessionA.pageForApi().setContent("<html><body><h1 id=\"marker\">Session A</h1></body></html>");
      sessionB.pageForApi().setContent("<html><body><h1 id=\"marker\">Session B</h1></body></html>");

      assertThat("session A shows its own content", sessionA.pageForApi().find("#marker").text(), equalTo("Session A"));
      assertThat("session B shows its own content", sessionB.pageForApi().find("#marker").text(), equalTo("Session B"));

      assertThat("sessions do not share the underlying browser", sessionA.instance(), not(sameInstance(sessionB.instance())));
      assertThat("sessions do not share the active page", sessionA.pageForApi(), not(sameInstance(sessionB.pageForApi())));
      assertThat(
        "each session owns its own finder instance, not a shared TEST-scope-cached one",
        sessionA.finder(),
        not(sameInstance(sessionB.finder()))
      );
      assertThat("session A's finder returns the same cached instance on repeated calls",
        sessionA.finder(), sameInstance(sessionA.finder()));

      assertThat("session A active", sessionA.isActive(), is(true));
      assertThat("session B active", sessionB.isActive(), is(true));
    } finally {
      sessionA.close();
      sessionB.close();
    }

    assertThat("session A inactive after close", sessionA.isActive(), is(false));
    assertThat("session B inactive after close", sessionB.isActive(), is(false));

    // Closing an already-closed session (idempotent) must never affect the other session's state.
    sessionA.close();
    assertThat("session B still closed after re-closing session A", sessionB.isActive(), is(false));
  }

  @Test
  void twoIndependentSessionsMaintainIndependentCookies() throws Exception {
    // Cookies do not work against about:blank/setContent() pages (Phase 0 finding, already relied
    // on by VibiumCapabilityContractTest's single-session cookie test), so both sessions navigate
    // to a tiny real local HTTP fixture server instead. Goes through the real Part B
    // ObservationCapability#getCookieNamed/InteractionCapability#executeScript path rather than the
    // raw BrowserContext#cookies(...) call directly.
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", exchange -> {
      byte[] body = "<html><body>cookie isolation fixture</body></html>".getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders()
        .add("Content-Type", "text/html; charset=utf-8");
      exchange.sendResponseHeaders(200, body.length);
      try (OutputStream out = exchange.getResponseBody()) {
        out.write(body);
      }
    });
    server.start();

    Browser browserA = Vibium.start(new StartOptions().headless(true));
    Browser browserB = Vibium.start(new StartOptions().headless(true));
    VibiumSession sessionA = new VibiumSession();
    VibiumSession sessionB = new VibiumSession();
    try {
      sessionA.using(browserA).on(DeviceType.DEFAULT);
      sessionB.using(browserB).on(DeviceType.DEFAULT);

      String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
      sessionA.pageForApi().go(url);
      sessionB.pageForApi().go(url);

      InteractionCapability interactionA = sessionA.capability(InteractionCapability.class);
      InteractionCapability interactionB = sessionB.capability(InteractionCapability.class);
      interactionA.executeScript("document.cookie = 'session_a_cookie=alpha; path=/';");
      interactionB.executeScript("document.cookie = 'session_b_cookie=beta; path=/';");

      ObservationCapability<?> observationA = sessionA.capability(ObservationCapability.class);
      ObservationCapability<?> observationB = sessionB.capability(ObservationCapability.class);

      CapturedCookie ownedByA = observationA.getCookieNamed("session_a_cookie");
      CapturedCookie ownedByB = observationB.getCookieNamed("session_b_cookie");
      assertThat("session A sees its own cookie", ownedByA, not(nullValue()));
      assertThat("session A's cookie has its own value", ownedByA.getValue(), equalTo("alpha"));
      assertThat("session B sees its own cookie", ownedByB, not(nullValue()));
      assertThat("session B's cookie has its own value", ownedByB.getValue(), equalTo("beta"));

      assertThat("session A does not see session B's cookie",
        observationA.getCookieNamed("session_b_cookie"), is(nullValue()));
      assertThat("session B does not see session A's cookie",
        observationB.getCookieNamed("session_a_cookie"), is(nullValue()));
    } finally {
      sessionA.close();
      sessionB.close();
      server.stop(0);
    }
  }
}
