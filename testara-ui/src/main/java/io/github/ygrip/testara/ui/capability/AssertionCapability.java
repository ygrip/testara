package io.github.ygrip.testara.ui.capability;

import io.github.ygrip.testara.ui.page.Element;
import io.github.ygrip.testara.ui.page.NamedPage;
import io.github.ygrip.testara.ui.page.PageContext;

/**
 * Fluent assertions (see that element has text, is visible, etc.). Screenplay-style.
 */
public interface AssertionCapability {

  /** Assert element is visible. Throws if not. Returns this. */
  AssertionCapability seeThatVisible(Element locator);

  /** Assert element is visible. Throws if not. Returns this. */
  AssertionCapability seeThatHidden(Element locator);

  /** Assert element has exact text. */
  AssertionCapability seeThatAttribute(Element locator, String attributeName);

  /** Assert element has exact text. */
  AssertionCapability seeThatText(Element locator, String expectedText);

  /** Assert element has exact value. */
  AssertionCapability seeThatValue(Element locator, String value);

  /** Assert element contains value. */
  AssertionCapability containsThatValue(Element locator, String value);

  /** Assert element contains text. */
  AssertionCapability seeThatContainsText(Element locator, String substring);

  /** Assert element contains text. */
  AssertionCapability hasClass(Element locator, String className);

  /** Assert element exists in DOM. */
  AssertionCapability seeThatPresent(Element locator);

  /** Check if element is visible (no throw). */
  boolean isVisible(Element locator);

  /** Check if element is hidden (no throw). */
  boolean isHidden(Element locator);

  /** Check if element is enabled (no throw). */
  boolean isEnabled(Element locator);

  /** Check if element is present (no throw). */
  boolean isPresent(Element locator);

  /** Check if page is current page (no throw). */
  AssertionCapability isOn(NamedPage namedPage);
}
