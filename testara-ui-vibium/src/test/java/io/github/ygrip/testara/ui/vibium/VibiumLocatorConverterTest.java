package io.github.ygrip.testara.ui.vibium;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.ygrip.testara.ui.model.Locator;
import io.github.ygrip.testara.ui.model.Selector;
import io.github.ygrip.testara.ui.vibium.error.UnsupportedVibiumCapabilityException;
import io.github.ygrip.testara.ui.vibium.locator.VibiumLocatorConverter;
import io.github.ygrip.testara.ui.vibium.locator.VibiumSelector;

/**
 * Pure unit tests for {@link VibiumLocatorConverter} — no browser required. One case per
 * {@link Selector} strategy, plus concrete escaping cases that would produce a broken/injectable
 * selector without escaping.
 */
@Tag("vibium")
class VibiumLocatorConverterTest {

  @Test
  void idProducesInteractionSafeHashSelector() {
    VibiumSelector selector = VibiumLocatorConverter.toSelector(Locator.id("username"));

    assertThat("id selector is interaction-safe", selector.isInteractionSafe(), is(true));
    assertThat("id selector value", selector.cssValue(), equalTo("#username"));
  }

  @Test
  void cssPassesThroughUnchanged() {
    String rawCss = "div.card > span:first-child";
    VibiumSelector selector = VibiumLocatorConverter.toSelector(Locator.css(rawCss));

    assertThat("css selector is interaction-safe", selector.isInteractionSafe(), is(true));
    assertThat("css selector passes through as-is", selector.cssValue(), equalTo(rawCss));
  }

  @Test
  void classProducesInteractionSafeDotSelector() {
    VibiumSelector selector = VibiumLocatorConverter.toSelector(Locator.className("btn-primary"));

    assertThat("class selector is interaction-safe", selector.isInteractionSafe(), is(true));
    assertThat("class selector value", selector.cssValue(), equalTo(".btn-primary"));
  }

  @Test
  void tagProducesInteractionSafeBareSelector() {
    VibiumSelector selector = VibiumLocatorConverter.toSelector(Locator.tagName("button"));

    assertThat("tag selector is interaction-safe", selector.isInteractionSafe(), is(true));
    assertThat("tag selector value", selector.cssValue(), equalTo("button"));
  }

  @Test
  void invalidTagNameIsRejected() {
    assertThrows(IllegalArgumentException.class, () -> VibiumLocatorConverter.toSelector(Locator.tagName("button; DROP")));
  }

  @Test
  void nameProducesInteractionSafeAttributeSelector() {
    VibiumSelector selector = VibiumLocatorConverter.toSelector(Locator.name("submit"));

    assertThat("name selector is interaction-safe", selector.isInteractionSafe(), is(true));
    assertThat("name selector value", selector.cssValue(), equalTo("[name=\"submit\"]"));
  }

  @Test
  void xpathIsDiscoveryOnly() {
    VibiumSelector selector = VibiumLocatorConverter.toSelector(Locator.xpath("//main//button"));

    assertThat("xpath selector is discovery-only", selector.isInteractionSafe(), is(false));
    Map<String, Object> params = selector.optionsValue().toParams();
    assertThat("xpath param carried through", params.get("xpath"), equalTo("//main//button"));
  }

  @Test
  void linkTextIsDiscoveryOnly() {
    VibiumSelector selector = VibiumLocatorConverter.toSelector(Locator.linkText("Read the docs"));

    assertThat("linkText selector is discovery-only", selector.isInteractionSafe(), is(false));
    Map<String, Object> params = selector.optionsValue().toParams();
    assertThat("linkText resolves via role=link", params.get("role"), equalTo("link"));
    assertThat("linkText resolves via text", params.get("text"), equalTo("Read the docs"));
  }

  @Test
  void partialLinkBehavesIdenticallyToLinkText() {
    // Vibium's SelectorOptions.text(...) is substring/semantic matching only in this version —
    // there is no separate exact-match mode, so LINKTEXT and PARTIALLINK produce the same query.
    VibiumSelector linkText = VibiumLocatorConverter.toSelector(Locator.linkText("docs"));
    VibiumSelector partialLink = VibiumLocatorConverter.toSelector(Locator.partialLink("docs"));

    assertThat("partialLink is discovery-only", partialLink.isInteractionSafe(), is(false));
    assertThat(
      "partialLink and linkText produce the same query params",
      partialLink.optionsValue().toParams(),
      equalTo(linkText.optionsValue().toParams())
    );
  }

  @Test
  void accessibilityStrategyIsRejected() {
    assertThrows(
      UnsupportedVibiumCapabilityException.class,
      () -> VibiumLocatorConverter.toSelector(Locator.of(Selector.ACCESSIBILITY, "loginButton"))
    );
  }

  @Test
  void androidUiAutomatorStrategyIsRejected() {
    assertThrows(
      UnsupportedVibiumCapabilityException.class,
      () -> VibiumLocatorConverter.toSelector(Locator.of(Selector.ANDROID_UI_AUTOMATOR, "new UiSelector()"))
    );
  }

  @Test
  void iosClassChainStrategyIsRejected() {
    assertThrows(
      UnsupportedVibiumCapabilityException.class,
      () -> VibiumLocatorConverter.toSelector(Locator.of(Selector.IOS_CLASS_CHAIN, "**/XCUIElementTypeButton"))
    );
  }

  @Test
  void idEscapesEmbeddedSpaceThatWouldOtherwiseBecomeADescendantCombinator() {
    // Without escaping, "#foo bar" is valid CSS but means something completely different: an
    // element with id="foo" containing a descendant <bar> element (space = descendant
    // combinator). The escaped form must keep this a single identifier selector.
    VibiumSelector selector = VibiumLocatorConverter.toSelector(Locator.id("foo bar"));

    assertThat("id selector is interaction-safe", selector.isInteractionSafe(), is(true));
    assertThat("naive concatenation would have been wrong", selector.cssValue(), not(equalTo("#foo bar")));
    assertThat("space is backslash-escaped so it stays one identifier", selector.cssValue(), equalTo("#foo\\ bar"));
  }

  @Test
  void classEscapesLeadingDigitThatWouldOtherwiseBeInvalidCss() {
    // A bare CSS identifier cannot start with a digit; ".123abc" is invalid selector syntax and
    // would throw at the native querySelector layer without escaping.
    VibiumSelector selector = VibiumLocatorConverter.toSelector(Locator.className("123abc"));

    assertThat("class selector is interaction-safe", selector.isInteractionSafe(), is(true));
    assertThat("naive concatenation would have been invalid CSS", selector.cssValue(), not(equalTo(".123abc")));
    assertThat("leading digit is escaped as a codepoint", selector.cssValue(), equalTo(".\\31 23abc"));
  }

  @Test
  void nameEscapesEmbeddedQuoteThatWouldOtherwiseBreakOutOfTheAttributeValue() {
    // Without escaping, a value containing a double quote would terminate the attribute value
    // early: [name="she said "hi""] is corrupt/invalid CSS.
    VibiumSelector selector = VibiumLocatorConverter.toSelector(Locator.name("she said \"hi\""));

    assertThat("name selector is interaction-safe", selector.isInteractionSafe(), is(true));
    assertThat(
      "naive concatenation would have broken out of the quoted attribute value",
      selector.cssValue(),
      not(equalTo("[name=\"she said \"hi\"\"]"))
    );
    assertThat("quotes are backslash-escaped", selector.cssValue(), equalTo("[name=\"she said \\\"hi\\\"\"]"));
  }

  @Test
  void nameEscapesEmbeddedBackslash() {
    VibiumSelector selector = VibiumLocatorConverter.toSelector(Locator.name("back\\slash"));

    assertThat("backslash is escaped", selector.cssValue(), equalTo("[name=\"back\\\\slash\"]"));
  }
}
