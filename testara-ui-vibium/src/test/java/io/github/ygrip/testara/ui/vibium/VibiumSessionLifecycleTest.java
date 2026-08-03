package io.github.ygrip.testara.ui.vibium;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.vibium.Browser;
import com.vibium.Page;
import com.vibium.Vibium;
import com.vibium.types.StartOptions;

import io.github.ygrip.testara.core.BaseTests;
import io.github.ygrip.testara.core.TestWith;
import io.github.ygrip.testara.ui.model.DeviceType;
import io.github.ygrip.testara.ui.vibium.driver.VibiumSession;

import lombok.extern.log4j.Log4j2;

@Log4j2
@Tag("ui")
@Tag("vibium")
@TestWith(properties = {"classpath:application.properties", "classpath:configuration.properties"})
class VibiumSessionLifecycleTest extends BaseTests {

  @Test
  void closeIsIdempotentWhenCalledTwice() throws Exception {
    VibiumSession session = new VibiumSession();
    Browser browser = Vibium.start(new StartOptions().headless(true));
    session.using(browser).on(DeviceType.DEFAULT);

    assertThat("active before close", session.isActive(), is(true));

    session.close();
    assertThat("inactive after first close", session.isActive(), is(false));

    // Must not throw on a second close.
    session.close();
    assertThat("still inactive after second close", session.isActive(), is(false));
  }

  @Test
  void isActiveReturnsFalseAfterTheUnderlyingBrowserProcessDiesUnexpectedly() throws Exception {
    VibiumSession session = new VibiumSession();
    Browser browser = Vibium.start(new StartOptions().headless(true));
    session.using(browser).on(DeviceType.DEFAULT);

    assertThat("active before forced stop", session.isActive(), is(true));

    // Realistic failure path: the browser process dies/is stopped out from under the session
    // (e.g. crash, external kill) without going through VibiumSession.close(). isActive() must
    // detect this via its live SDK call rather than throwing.
    browser.stop();
    assertThat("isActive detects the dead browser process", session.isActive(), is(false));

    // close() afterwards must still be safe even though the session is already broken.
    session.close();
    assertThat("still inactive after close following the forced failure", session.isActive(), is(false));

    // And still idempotent from here.
    session.close();
    assertThat("still inactive after a second close following the forced failure", session.isActive(), is(false));
  }

  @Test
  void openNewPageAndSwitchToPageChangeTheActivePageWhileOriginalTabRemainsReachable() throws Exception {
    VibiumSession session = new VibiumSession();
    Browser browser = Vibium.start(new StartOptions().headless(true));
    try {
      session.using(browser).on(DeviceType.DEFAULT);

      Page originalPage = session.pageForApi();
      originalPage.setContent("<html><body><h1 id=\"marker\">Original Tab</h1></body></html>");

      Page secondPage = session.openNewPage();
      assertThat("active page switched to the new tab", session.pageForApi(), sameInstance(secondPage));
      secondPage.setContent("<html><body><h1 id=\"marker\">Second Tab</h1></body></html>");

      // Browser.pages() returns a fresh Page wrapper per call (no equals()/hashCode() override
      // on Page), so tab membership is asserted by Page#id() rather than object identity.
      List<String> openPageIds = browser.pages()
        .stream()
        .map(Page::id)
        .collect(Collectors.toList());
      assertThat("browser still tracks the original tab", openPageIds.contains(originalPage.id()), is(true));
      assertThat("browser tracks the new tab", openPageIds.contains(secondPage.id()), is(true));

      session.switchToPage(originalPage);
      assertThat("active page switched back to the original tab", session.pageForApi(), sameInstance(originalPage));
      assertThat(
        "original tab content is unaffected by the tab switch",
        session.pageForApi().find("#marker").text(),
        is("Original Tab")
      );
    } finally {
      session.close();
    }
  }
}
