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
import io.github.ygrip.testara.ui.model.Selector;
import io.github.ygrip.testara.ui.populator.ElementCatalog;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression coverage for testara-hti.4: parseElementSelector split every element string on ':'
 * and unconditionally stripped parts[0] whenever there was more than one part, even when
 * parts[0] didn't resolve to a known Selector. An unprefixed xpath/css value that itself
 * contains a colon (e.g. {@code //a[@data-id=':x']} or {@code a:hover}) got corrupted.
 */
class PageFinderLocatorParsingTest {

  /** Minimal finder; only resolveLocator's private parsing helper is under test. */
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
      return new HashMap<>();
    }

    @Override
    <T> T getPageInstance(Class<? extends T> type) {
      throw new UnsupportedOperationException();
    }
  }

  private final FakeFinder finder = new FakeFinder();

  @Test
  void unprefixedXpathContainingColonIsKeptWhole() {
    String xpath = "//a[@data-id=':x']";
    Locator locator = finder.resolveLocator(xpath);

    assertEquals(Selector.CSS, locator.getStrategy(), "unrecognized prefix must fall back to css() on the whole string");
    assertEquals(xpath, locator.getValue());
  }

  @Test
  void unprefixedCssContainingColonIsKeptWhole() {
    String css = "a:hover";
    Locator locator = finder.resolveLocator(css);

    assertEquals(Selector.CSS, locator.getStrategy());
    assertEquals(css, locator.getValue());
  }

  @Test
  void knownSelectorPrefixIsStillStripped() {
    Locator locator = finder.resolveLocator("xpath://div[@id='x']");

    assertEquals(Selector.XPATH, locator.getStrategy());
    assertEquals("//div[@id='x']", locator.getValue());
  }

  @Test
  void knownSelectorPrefixWithHyphenIsStillStripped() {
    Locator locator = finder.resolveLocator("link-text:Sign in");

    assertEquals(Selector.LINKTEXT, locator.getStrategy());
    assertEquals("Sign in", locator.getValue());
  }

  @Test
  void valueWithoutAnyColonIsUnaffected() {
    Locator locator = finder.resolveLocator(".submit-button");

    assertEquals(Selector.CSS, locator.getStrategy());
    assertEquals(".submit-button", locator.getValue());
  }
}
