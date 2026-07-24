package io.github.ygrip.testara.ui.driver;

import io.github.ygrip.testara.ui.page.PageContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Per-session "current page" state. Ownership of the current page belongs to the browser
 * {@link DriverSession}, not to the (test-scoped, potentially shared) {@code PageFinder}.
 * Each session holds one of these so two sessions in the same test never share current-page state.
 *
 * <p>All transitions are logged to make navigation state easy to trace when debugging.
 */
public final class CurrentPageHolder {

  private static final Logger LOG = LogManager.getLogger(CurrentPageHolder.class);

  private final DriverSession<?> session;
  private volatile PageContext<?> currentPage;

  public CurrentPageHolder(DriverSession<?> session) {
    this.session = session;
  }

  public PageContext<?> current() {
    return currentPage;
  }

  /** Mark {@code page} as the current page of this session. Null is rejected and logged. */
  public void activate(PageContext<?> page) {
    if (page == null) {
      LOG.warn("#activatePage called with null page on session '{}' — ignoring", sessionName());
      return;
    }
    String previousName = "none";
    if (this.currentPage != null) {
      previousName = this.currentPage.getClass().getSimpleName();
    }
    this.currentPage = page;
    LOG.debug("#Activated page '{}' on session '{}' (previous: {})",
        page.getClass().getSimpleName(), sessionName(), previousName);
  }

  /** Forget the current page (e.g. on session close). Safe to call repeatedly. */
  public void clear() {
    if (this.currentPage != null) {
      LOG.debug("#Cleared current page '{}' on session '{}'",
          this.currentPage.getClass().getSimpleName(), sessionName());
    }
    this.currentPage = null;
  }

  private String sessionName() {
    try {
      String name = session.sessionName();
      if (name != null) {
        return name;
      }
    } catch (Exception err) {
      LOG.trace("#Unable to resolve session name for current-page log", err);
    }
    return "unknown";
  }
}
