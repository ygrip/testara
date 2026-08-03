package io.github.ygrip.testara.ui.vibium;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.vibium.Browser;
import com.vibium.Page;
import com.vibium.Vibium;
import com.vibium.types.StartOptions;
import com.vibium.types.ViewportSize;

import io.github.ygrip.testara.core.BaseTests;
import io.github.ygrip.testara.core.TestWith;
import io.github.ygrip.testara.ui.model.DeviceType;
import io.github.ygrip.testara.ui.vibium.config.VibiumViewportSize;
import io.github.ygrip.testara.ui.vibium.driver.VibiumSession;

import lombok.extern.log4j.Log4j2;

/**
 * Proves desktop vs mobile viewport isolation: two independently-created sessions, each on a
 * different {@link DeviceType}, each end up with its own correctly-applied viewport, and setting
 * up one (including opening a new tab) never affects the other.
 */
@Log4j2
@Tag("ui")
@Tag("vibium")
@TestWith(properties = {"classpath:application.properties", "classpath:configuration.properties"})
class VibiumPageCatalogTest extends BaseTests {

  @Test
  void desktopAndMobileSessionsGetIndependentlyCorrectViewports() throws Exception {
    VibiumViewportSize desktopSize = new VibiumViewportSize();
    desktopSize.setWidth(1440);
    desktopSize.setHeight(900);

    VibiumViewportSize mobileSize = new VibiumViewportSize();
    mobileSize.setWidth(390);
    mobileSize.setHeight(844);

    Map<DeviceType, VibiumViewportSize> profiles = Map.of(
      DeviceType.DESKTOP, desktopSize,
      DeviceType.MOBILE, mobileSize
    );

    Browser desktopBrowser = Vibium.start(new StartOptions().headless(true));
    Browser mobileBrowser = Vibium.start(new StartOptions().headless(true));

    VibiumSession desktopSession = new VibiumSession();
    VibiumSession mobileSession = new VibiumSession();
    try {
      desktopSession.using(desktopBrowser);
      desktopSession.withViewport(profiles);
      desktopSession.on(DeviceType.DESKTOP);

      mobileSession.using(mobileBrowser);
      mobileSession.withViewport(profiles);
      mobileSession.on(DeviceType.MOBILE);

      ViewportSize desktopViewport = desktopSession.pageForApi().viewport();
      ViewportSize mobileViewport = mobileSession.pageForApi().viewport();

      assertThat("desktop session gets its own configured viewport width", desktopViewport.width(), equalTo(1440));
      assertThat("desktop session gets its own configured viewport height", desktopViewport.height(), equalTo(900));

      assertThat("mobile session gets its own configured viewport width", mobileViewport.width(), equalTo(390));
      assertThat("mobile session gets its own configured viewport height", mobileViewport.height(), equalTo(844));

      assertThat(
        "desktop and mobile viewports are independent",
        desktopViewport.width(),
        not(equalTo(mobileViewport.width()))
      );

      // New tabs do not inherit a previously-set viewport; opening one must reapply THIS
      // session's own configured viewport, and must not bleed into the other session.
      Page desktopSecondTab = desktopSession.openNewPage();
      ViewportSize desktopSecondTabViewport = desktopSecondTab.viewport();
      assertThat("new tab on desktop session keeps desktop viewport width", desktopSecondTabViewport.width(), equalTo(1440));
      assertThat("new tab on desktop session keeps desktop viewport height", desktopSecondTabViewport.height(), equalTo(900));

      ViewportSize mobileViewportAfter = mobileSession.pageForApi().viewport();
      assertThat("mobile session viewport untouched by desktop's new tab", mobileViewportAfter.width(), equalTo(390));
      assertThat("mobile session viewport untouched by desktop's new tab", mobileViewportAfter.height(), equalTo(844));
    } finally {
      desktopSession.close();
      mobileSession.close();
    }
  }
}
