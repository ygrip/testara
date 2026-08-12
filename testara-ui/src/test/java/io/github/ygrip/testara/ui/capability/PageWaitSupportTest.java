package io.github.ygrip.testara.ui.capability;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;

import io.github.ygrip.testara.ui.config.AbstractDriverProperties;
import io.github.ygrip.testara.ui.driver.DriverSession;
import io.github.ygrip.testara.ui.error.WaitTimeoutException;
import io.github.ygrip.testara.ui.model.Locator;
import io.github.ygrip.testara.ui.page.NamedPage;
import io.github.ygrip.testara.ui.page.PageContext;
import io.github.ygrip.testara.ui.page.PageFinder;
import io.github.ygrip.testara.ui.populator.ElementCatalog;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PageWaitSupportTest {

  static final class FakePage extends PageContext<DriverSession<?>> {
    private final boolean current;

    FakePage(boolean current) {
      super((DriverSession<?>) null);
      this.current = current;
    }

    @Override public boolean isCurrentPage(Duration timeout) { return current; }
    @Override public String currentUrl() { return ""; }
    @Override public String pageTitle() { return ""; }
    @Override public void open(String url) { }
    @Override public void refresh() { }
    @Override public void reload() { }
    @Override public void forward() { }
    @Override public void back() { }
  }

  static final class FakeFinder extends PageFinder<PageContext<?>, Object, Object> {
    private final PageContext<?> page;
    private boolean activated;

    FakeFinder(PageContext<?> page) {
      this.page = page;
    }

    boolean wasActivated() {
      return activated;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getPage(Class<T> pageObject) {
      return (T) page;
    }

    @Override
    public <C extends PageContext<?>> void setCurrentPage(C page) {
      activated = true;
    }

    @Override public Class<? extends AbstractDriverProperties> configType() { return null; }
    @Override public Logger log() { return LogManager.getLogger(FakeFinder.class); }
    @Override public Object getLocator(PageContext<?> page, String element) { return null; }
    @Override public Object getLocator(PageContext<?> page, String element, Map<String, ?> parameters) { return null; }
    @Override public Object getLocator(Locator locator) { return null; }
    @Override public Supplier<Object> getElementFromPage(PageContext<?> page, String element) { return null; }
    @Override public Supplier<Object> getElementFromPage(PageContext<?> page, Locator locator) { return null; }
    @Override public Supplier<List<Object>> getElementsFromPage(PageContext<?> page, String element) { return null; }
    @Override public Supplier<List<Object>> getElementsFromPage(PageContext<?> page, Locator locator) { return null; }
    @Override public Supplier<Object> getElement(Locator locator) { return null; }
    @Override public Supplier<List<Object>> getElements(Locator locator) { return null; }
    @Override public List<Object> getElementsWithRoot(Object parent, Object locator) { return null; }
    @Override public Object getElementWithRoot(Object parent, Object locator) { return null; }
    @Override public Object getPrecedingSiblingElement(Object parent, Object locator) { return null; }
    @Override public List<Object> getPrecedingSiblingElements(Object parent, Object locator) { return null; }
    @Override public Object getFollowingSiblingElement(Object parent, Object locator) { return null; }
    @Override public List<Object> getFollowingSiblingElements(Object parent, Object locator) { return null; }
    @Override public List<Object> getSiblings(Object parent, Object locator) { return null; }
    @Override public Object getChildNode(Object parent, Object locator, int childIndex) { return null; }

    @Override
    protected BiFunction<Field, Object, ElementCatalog> resolveElementStrategy(ElementCatalog catalog) {
      return (field, value) -> catalog;
    }
  }

  @Test
  void activatesPageWhenConditionIsMet() {
    FakeFinder finder = new FakeFinder(new FakePage(true));
    NamedPage page = NamedPage.of(FakePage.class).by(finder).build();

    PageWaitSupport.requireLoaded(page, Duration.ofMillis(10));

    assertTrue(finder.wasActivated());
  }

  @Test
  void throwsWhenPageConditionIsNotMet() {
    FakeFinder finder = new FakeFinder(new FakePage(false));
    NamedPage page = NamedPage.of(FakePage.class).by(finder).build();

    assertThrows(
      WaitTimeoutException.class,
      () -> PageWaitSupport.requireLoaded(page, Duration.ofMillis(10))
    );
  }

  @Test
  void throwsWhenPageCannotBeResolved() {
    FakeFinder finder = new FakeFinder(null);
    NamedPage page = NamedPage.of(FakePage.class).by(finder).build();

    assertThrows(
      WaitTimeoutException.class,
      () -> PageWaitSupport.requireLoaded(page, Duration.ofMillis(10))
    );
  }
}
