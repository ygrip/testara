package io.github.ygrip.testara.ui.page;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;

import io.github.ygrip.testara.ui.config.AbstractDriverProperties;
import io.github.ygrip.testara.ui.driver.DriverSession;
import io.github.ygrip.testara.ui.model.DeviceType;
import io.github.ygrip.testara.ui.model.Locator;
import io.github.ygrip.testara.ui.populator.ElementCatalog;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Regression coverage for {@link NamedPage#getPage()}: when a {@link NamedPage} is built via
 * {@code NamedPage.of(Class)}, resolution must go through the finder's class-based lookup
 * ({@link PageFinder#getPage(Class)}) rather than the name-based lookup, since the name is
 * always null in that construction path.
 */
class NamedPageResolutionTest {

  /** No {@code @Page} annotation, so the parent constructor never touches the framework config. */
  static final class TargetPage extends PageContext<DriverSession<?>> {
    TargetPage() { super((DriverSession<?>) null); }

    @Override public String currentUrl() { return ""; }
    @Override public String pageTitle() { return ""; }
    @Override public void open(String url) { }
    @Override public void refresh() { }
    @Override public void reload() { }
    @Override public void forward() { }
    @Override public void back() { }
  }

  /** Minimal finder that registers a single page class without any classpath scanning. */
  static final class FakeFinder extends PageFinder<PageContext<?>, Object, Object> {
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

    @Override
    Map<String, Class<? extends PageContext<?>>> pagesOn(DeviceType deviceType) {
      Map<String, Class<? extends PageContext<?>>> pages = new HashMap<>();
      pages.put("target", TargetPage.class);
      return pages;
    }

    @Override
    <T> T getPageInstance(Class<? extends T> type) {
      try {
        return type.getDeclaredConstructor()
          .newInstance();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }

  @Test
  void resolvesByClassWhenConstructedFromPageType() {
    NamedPage namedPage = NamedPage.of(TargetPage.class)
      .by(new FakeFinder())
      .build();

    PageContext<?> page = namedPage.getPage();

    assertNotNull(page, "class-based lookup must resolve a page instance");
    assertSame(TargetPage.class, page.getClass());
  }
}
