package io.github.ygrip.testara.ui.driver;

import io.github.ygrip.testara.ui.config.AbstractDriverProperties;
import io.github.ygrip.testara.ui.model.DeviceType;
import io.github.ygrip.testara.ui.page.PageContext;
import io.github.ygrip.testara.ui.page.PageFinder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Regression coverage for session/page ownership: the current page belongs to the browser
 * {@link DriverSession}, not the shared {@code PageFinder}. Two sessions in the same test must
 * keep independent current-page state.
 */
class CurrentPageHolderTest {

  /** Minimal session; the three ownership methods delegate to a {@link CurrentPageHolder}. */
  static final class FakeSession implements DriverSession<Object> {
    private final CurrentPageHolder pageState = new CurrentPageHolder(this);

    @Override public <T> T capability(Class<T> type) { throw new UnsupportedOperationException(); }
    @Override public Class<? extends AbstractDriverProperties> configType() { return null; }
    @Override public <F extends PageFinder<?, ?, ?>> F finder() { return null; }
    @Override public void close() { }
    @Override public boolean isActive() { return false; }
    @Override public Object instance() { return null; }
    @Override public DeviceType platform() { return DeviceType.DEFAULT; }
    @Override public DriverSession<Object> using(Object driver) { return this; }
    @Override public DriverSession<Object> on(DeviceType platform) { return this; }
    @Override public PageContext<?> currentPage() { return pageState.current(); }
    @Override public void activatePage(PageContext<?> page) { pageState.activate(page); }
    @Override public void clearCurrentPage() { pageState.clear(); }
    @Override public String sessionName() { return "fake-session"; }
  }

  /** No {@code @Page} annotation, so the parent constructor never touches the framework config. */
  static final class FakePage extends PageContext<FakeSession> {
    FakePage() { super((FakeSession) null); }

    @Override public String currentUrl() { return ""; }
    @Override public String pageTitle() { return ""; }
    @Override public void open(String url) { }
    @Override public void refresh() { }
    @Override public void reload() { }
    @Override public void forward() { }
    @Override public void back() { }
  }

  @Test
  void eachSessionOwnsItsCurrentPageIndependently() {
    FakeSession sessionA = new FakeSession();
    FakeSession sessionB = new FakeSession();
    assertNull(sessionA.currentPage());
    assertNull(sessionB.currentPage());

    FakePage pageA = new FakePage();
    sessionA.activatePage(pageA);

    assertSame(pageA, sessionA.currentPage(), "session A must hold the page it activated");
    assertNull(sessionB.currentPage(), "session B must NOT share session A's current-page state");

    sessionA.clearCurrentPage();
    assertNull(sessionA.currentPage(), "clearCurrentPage must reset the session's current page");
  }

  @Test
  void activatingNullIsIgnored() {
    FakeSession session = new FakeSession();
    session.activatePage(null);
    assertNull(session.currentPage());
  }
}
