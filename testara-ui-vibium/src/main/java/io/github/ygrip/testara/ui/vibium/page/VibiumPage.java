package io.github.ygrip.testara.ui.vibium.page;

import com.vibium.Page;

import io.github.ygrip.testara.ui.driver.DriverSession;
import io.github.ygrip.testara.ui.driver.DriverSessionManager;
import io.github.ygrip.testara.ui.page.PageContext;
import io.github.ygrip.testara.ui.vibium.driver.VibiumSession;

import lombok.extern.log4j.Log4j2;

/**
 * Vibium's {@link PageContext}. Unlike {@code PlaywrightPage}, no API-thread marshalling is
 * needed: Vibium's Java client is not tied to a dedicated worker thread the way Playwright's is
 * (Phase 1 decision), so every method here calls straight through to the session's active
 * {@link com.vibium.Page}.
 *
 * <p>Every method resolves the live session defensively — first via {@link #driver()}, falling
 * back to scanning {@link DriverSessionManager#getCurrentDrivers()} for an active
 * {@link VibiumSession} — mirroring {@code PlaywrightPage}'s resolution pattern.
 */
@Log4j2
public abstract class VibiumPage extends PageContext<VibiumSession> {

  private VibiumSession vibium() {
    VibiumSession session = driver();
    if (session != null) {
      return session;
    }
    for (DriverSession<?> candidate : DriverSessionManager.inThisTestThread().getCurrentDrivers()) {
      if (candidate instanceof VibiumSession vibiumSession && candidate.isActive()) {
        return vibiumSession;
      }
    }
    throw new IllegalStateException(
      "No Vibium session on this test thread. Open a browser (e.g. via the Vibium engine) "
        + "or call DriverSessionManager.inThisTestThread().setCurrentActiveDriver(session) after registering the driver."
    );
  }

  /**
   * Returns the live active {@link Page}. Do not retain this reference across a tab switch;
   * prefer {@link PageContext} methods and (Phase 3) capabilities.
   */
  public Page page() {
    return vibium().pageForApi();
  }

  @Override
  public String currentUrl() {
    return vibium().pageForApi().url();
  }

  @Override
  public String pageTitle() {
    return vibium().pageForApi().title();
  }

  @Override
  public void open(String url) {
    vibium().pageForApi().go(url);
  }

  @Override
  public void refresh() {
    vibium().pageForApi().reload();
  }

  @Override
  public void reload() {
    vibium().pageForApi().reload();
  }

  @Override
  public void forward() {
    vibium().pageForApi().forward();
  }

  @Override
  public void back() {
    vibium().pageForApi().back();
  }
}
