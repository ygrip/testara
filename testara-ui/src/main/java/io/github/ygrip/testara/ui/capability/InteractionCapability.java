package io.github.ygrip.testara.ui.capability;

import java.time.Duration;
import java.util.List;

import io.github.ygrip.testara.ui.page.Element;

/**
 * Fluent interaction (click, type, clear). Screenplay-style, chainable.
 */
public interface InteractionCapability {

  /** Execute javascript on the page. Returns this for chaining. */
  <T> T executeScript(String script, Object... args);

  /** Execute javascript on the page. Returns this for chaining. */
  <T> T executeScriptAsync(String script, Object... args);

  /** Scroll to the element. Returns this for chaining. */
  InteractionCapability scrollTo(Element locator, boolean alignToTop);

  /** Click the element. Returns this for chaining. */
  InteractionCapability click(Element locator);

  /** Focus the element. Returns this for chaining. */
  InteractionCapability focus(Element locator);

  /** Blur the element. Returns this for chaining. */
  InteractionCapability blur(Element locator);

  /** Force click the element. Returns this for chaining. */
  InteractionCapability forceClick(Element locator);

  /** Click the element. Returns this for chaining. */
  InteractionCapability doubleClick(Element locator);

  /** Hover the element. Returns this for chaining. */
  InteractionCapability hover(Element locator);

  /** Drag the element. Returns this for chaining. */
  InteractionCapability hold(Element locator, Duration duration);

  /** Drag the element. Returns this for chaining. */
  InteractionCapability drag(Element source, Element target);

  /** Drag the element. Returns this for chaining. */
  InteractionCapability drag(Element source, int xOffset, int yOffset);

  /** Start fluent text entry: {@code enter("text").into(locator) }. */
  TextEntry enter(String text);

  /** Clear the element. */
  InteractionCapability clear(Element locator);

  /** Submit the form containing the element. */
  InteractionCapability submit(Element locator);

  /** Resolve element (engine-specific handle, e.g. WebElement). Returns null if not found. */
  Object findElement(Element locator);

  /** Resolve all matching elements. Returns empty list if none. */
  List<?> findElements(Element locator);

  /** Start fluent select: {@code selectOption(locator).byValue("val")}. */
  SelectOption selectOption(Element locator);

  /** Fluent step for entering text into a locator. */
  interface TextEntry {
    InteractionCapability into(Element locator);
  }

  /** Fluent step for selecting an option from a {@code <select>} element. */
  interface SelectOption {
    InteractionCapability byValue(String value);
    InteractionCapability byIndex(int index);
    InteractionCapability byVisibleText(String visibleText);
  }
}
