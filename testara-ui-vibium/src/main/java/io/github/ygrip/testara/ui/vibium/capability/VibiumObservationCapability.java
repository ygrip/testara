package io.github.ygrip.testara.ui.vibium.capability;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.vibium.errors.VibiumException;
import com.vibium.types.ScreenshotOptions;

import io.github.ygrip.testara.ui.capability.ObservationCapability;
import io.github.ygrip.testara.ui.model.CapturedCookie;
import io.github.ygrip.testara.ui.page.Element;
import io.github.ygrip.testara.ui.vibium.driver.VibiumSession;
import io.github.ygrip.testara.ui.vibium.error.UnsupportedVibiumCapabilityException;
import io.github.ygrip.testara.ui.vibium.error.VibiumOperationException;
import io.github.ygrip.testara.ui.vibium.locator.VibiumElement;

import lombok.extern.log4j.Log4j2;

/**
 * Vibium's {@link ObservationCapability}, bound to {@code E = com.vibium.Element} — the raw native
 * SDK handle, mirroring how {@code PlaywrightObservationCapability} binds {@code E} to Playwright's
 * own native {@code Locator} type rather than a Testara wrapper.
 *
 * <p><b>Why {@link #findOne}/{@link #findOneChild}/{@link #findAll}/{@link #findAllChild} still
 * gate on {@link VibiumElement#requireInteractionSafe} before returning that raw handle:</b> the
 * native {@code com.vibium.Element} object returned here is exactly what Vibium itself produced via
 * a real, live {@code find}/{@code findAll} call — it is never fabricated or "repaired" by this
 * adapter. But for an element resolved through a discovery-only strategy (semantic role/text/xpath),
 * that live-produced handle carries an empty stored selector (a confirmed client bug — see {@link
 * VibiumElement}), so literally every follow-up native call on it — including a read-only one like
 * {@code text()}/{@code getAttribute()}/{@code isVisible()} — throws. Handing that handle back as
 * an "escape hatch for advanced use" would be silently propagating a handle that is guaranteed to
 * break on first use, with a confusing native {@code ElementNotFoundException} instead of a clear
 * reason. Refusing it up front (same gate every other capability method in this module already
 * applies before its own native round-trip) is the honest choice: a discovery-only locator is
 * simply not usable through this escape hatch, so it fails loudly and immediately instead of later.
 */
@Log4j2
public final class VibiumObservationCapability extends VibiumElementResolver
    implements ObservationCapability<com.vibium.Element> {

  public VibiumObservationCapability(VibiumSession session) {
    super(session);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private Element bindToSessionFinder(Element locator) {
    if (locator == null) {
      return null;
    }
    Element cursor = locator;
    while (cursor != null) {
      cursor.using(session.finder());
      cursor = cursor.child();
    }
    return locator;
  }

  /** See {@code VibiumInteractionCapability#resolveAllElements} — duplicated for the same reason. */
  @SuppressWarnings({"rawtypes", "unchecked"})
  private List<VibiumElement> resolveAllElements(Element locator) {
    if (locator == null) {
      return List.of();
    }
    try {
      Element current = bindToSessionFinder(locator);
      while (current.child() != null) {
        current = current.child();
      }
      List<VibiumElement> result = current.all();
      return result != null ? result : List.of();
    } catch (Exception e) {
      log.debug("Unable to resolve elements on {}: {}", describeLocator(locator), e.getMessage());
      return List.of();
    }
  }

  /**
   * Resolve {@code locator.child()} (mirrors {@code PlaywrightElementResolver#resolveChildOnApiThreadOnly}).
   * Returns {@code null} if {@code locator} has no child link at all.
   */
  @SuppressWarnings({"rawtypes"})
  private VibiumElement resolveChildElement(Element locator) {
    if (locator == null || locator.child() == null) {
      return null;
    }
    try {
      Element current = bindToSessionFinder(locator).child();
      while (current.child() != null) {
        current = current.child();
      }
      return (VibiumElement) current.one();
    } catch (Exception e) {
      log.debug("Unable to resolve child element on {}: {}", describeLocator(locator), e.getMessage());
      return null;
    }
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private List<VibiumElement> resolveChildElements(Element locator) {
    if (locator == null || locator.child() == null) {
      return List.of();
    }
    try {
      Element current = bindToSessionFinder(locator).child();
      while (current.child() != null) {
        current = current.child();
      }
      List<VibiumElement> result = current.all();
      return result != null ? result : List.of();
    } catch (Exception e) {
      log.debug("Unable to resolve child elements on {}: {}", describeLocator(locator), e.getMessage());
      return List.of();
    }
  }

  private VibiumOperationException wrap(String operation, String locatorDescription, VibiumException cause) {
    return VibiumOperationException.of(operation, locatorDescription, safePageUrl(), 0L, cause);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> T fromScript(String script, Object... args) {
    String wrapped = VibiumInteractionCapability.buildEvaluableScript(script, args);
    try {
      return (T) session.pageForApi()
        .evaluate(wrapped);
    } catch (VibiumException e) {
      throw wrap("fromScript", "n/a", e);
    }
  }

  @Override
  public String getText(Element locator) {
    VibiumElement el = requireElement(locator, "getText");
    el.requireInteractionSafe("getText");
    try {
      return el.raw()
        .text();
    } catch (VibiumException e) {
      throw wrap("getText", describeLocator(locator), e);
    }
  }

  @Override
  public List<String> getTexts(Element locator) {
    List<VibiumElement> resolved = resolveAllElements(locator);
    if (resolved.isEmpty()) {
      return List.of();
    }
    List<String> result = new ArrayList<>(resolved.size());
    for (VibiumElement el : resolved) {
      el.requireInteractionSafe("getTexts");
      try {
        result.add(el.raw()
          .text());
      } catch (VibiumException e) {
        throw wrap("getTexts", describeLocator(locator), e);
      }
    }
    return result;
  }

  @Override
  public String getValue(Element locator) {
    VibiumElement el = requireElement(locator, "getValue");
    el.requireInteractionSafe("getValue");
    try {
      return el.raw()
        .value();
    } catch (VibiumException e) {
      throw wrap("getValue", describeLocator(locator), e);
    }
  }

  @Override
  public List<String> getValues(Element locator) {
    List<VibiumElement> resolved = resolveAllElements(locator);
    if (resolved.isEmpty()) {
      return List.of();
    }
    List<String> result = new ArrayList<>(resolved.size());
    for (VibiumElement el : resolved) {
      el.requireInteractionSafe("getValues");
      try {
        result.add(el.raw()
          .value());
      } catch (VibiumException e) {
        throw wrap("getValues", describeLocator(locator), e);
      }
    }
    return result;
  }

  @Override
  public String getCurrentUrl() {
    try {
      return session.pageForApi()
        .url();
    } catch (VibiumException e) {
      throw wrap("getCurrentUrl", "n/a", e);
    }
  }

  @Override
  public String getPageTitle() {
    try {
      return session.pageForApi()
        .title();
    } catch (VibiumException e) {
      throw wrap("getPageTitle", "n/a", e);
    }
  }

  @Override
  public CapturedCookie getCookieNamed(String name) {
    return getCookies().stream()
      .filter(cookie -> Objects.equals(cookie.getName(), name))
      .findFirst()
      .orElse(null);
  }

  @Override
  public List<CapturedCookie> getCookies() {
    try {
      // Scoped to the current page's URL rather than an unqualified BrowserContext#cookies() call:
      // the Phase 0 spike only empirically verified the single-URL overload
      // (ctx.cookies("https://example.com")); the zero-arg overload's behavior was never confirmed
      // against this pinned client, so this avoids relying on an unverified code path. This does
      // narrow "all browser cookies" to "cookies visible to the current page's origin" (unlike
      // Playwright's unscoped context.cookies()), which is documented here as a deliberate,
      // verified-safe deviation.
      List<com.vibium.types.Cookie> cookies = session.pageForApi()
        .context()
        .cookies(session.pageForApi()
          .url());
      return cookies.stream()
        .map(this::toCapturedCookie)
        .collect(Collectors.toList());
    } catch (VibiumException e) {
      throw wrap("getCookies", "n/a", e);
    }
  }

  private CapturedCookie toCapturedCookie(com.vibium.types.Cookie cookie) {
    if (cookie == null) {
      return null;
    }
    Long expirySeconds = cookie.expiry();
    return CapturedCookie.builder()
      .name(cookie.name())
      .value(cookie.value())
      .domain(cookie.domain())
      .path(cookie.path())
      .secured(cookie.secure())
      .httpOnly(cookie.httpOnly())
      .sameSite(cookie.sameSite())
      .maxAge(expirySeconds)
      .expiryDate(expirySeconds != null ? new Date(expirySeconds * 1000) : null)
      .build();
  }

  @Override
  public String getAttribute(Element locator, String attributeName) {
    VibiumElement el = requireElement(locator, "getAttribute");
    el.requireInteractionSafe("getAttribute");
    try {
      return el.raw()
        .getAttribute(attributeName);
    } catch (VibiumException e) {
      throw wrap("getAttribute", describeLocator(locator), e);
    }
  }

  @Override
  public String getCssValue(Element locator, String attributeName) {
    VibiumElement el = requireElement(locator, "getCssValue");
    el.requireInteractionSafe("getCssValue");
    String selector = pageLevelCssSelectorOf(el, "getCssValue");
    try {
      String script = "(function(){var el=document.querySelector(" + VibiumInteractionCapability.jsonQuote(selector)
        + ");return el?window.getComputedStyle(el).getPropertyValue("
        + VibiumInteractionCapability.jsonQuote(attributeName) + "):null;})()";
      Object result = session.pageForApi()
        .evaluate(script);
      return result != null ? result.toString() : null;
    } catch (VibiumException e) {
      throw wrap("getCssValue", describeLocator(locator), e);
    }
  }

  /**
   * Vibium's {@code com.vibium.Element} has no {@code evaluate}/scoped-script method (confirmed via
   * {@code javap} against {@code vibium-26.5.31.jar}), and {@code Page#evaluate(String)} takes no
   * argument parameter to receive a specific element handle either — so there is no way to run
   * {@code getComputedStyle(el).getPropertyValue(prop)} truly SCOPED to an arbitrary
   * already-resolved element. This re-derives the plain CSS selector text that was used to resolve
   * the element ({@link VibiumElement#resolvedVia()} is exactly {@code "css:" + the original
   * selector} for every interaction-safe — i.e. ID/CSS/CLASS/TAG/NAME-derived — locator; see {@code
   * VibiumSelector#css}) and re-queries it with {@code document.querySelector} at the page/document
   * level.
   *
   * <p>This is correct for the common case (a locator resolved directly against the page). It is
   * NOT reliable for a locator resolved under a parent scope, because Vibium's own {@code
   * Element#find}/{@code findAll} scope the query to that parent's subtree server-side, while a
   * reconstructed {@code document.querySelector} call here can only search the whole document — if
   * the same selector text also matches an unrelated element elsewhere on the page, this would
   * silently compute the wrong element's style. The anonymous nth-child case ({@code
   * Element.child(index)}) always resolves through the literal Vibium selector {@code ":scope > *"}
   * (see {@code VibiumPageFinder#getChildNode}), which can never be meaningfully re-run at the
   * document level, so that specific, detectable case is rejected outright rather than silently
   * mis-targeting — a direct application of this module's plan §12 "fail for unaddressable cases".
   */
  private String pageLevelCssSelectorOf(VibiumElement el, String operationName) {
    String describedAs = el.resolvedVia();
    if (describedAs == null || !describedAs.startsWith("css:")) {
      throw new UnsupportedVibiumCapabilityException(
        operationName,
        "element was not resolved via a plain CSS-derived selector; Vibium has no element-scoped "
          + "script-evaluation API to compute a CSS property another way"
      );
    }
    String css = describedAs.substring("css:".length());
    if (css.contains(":scope")) {
      throw new UnsupportedVibiumCapabilityException(
        operationName,
        "element was resolved via a parent-scoped ':scope' query (e.g. Element.child(index)); "
          + "Vibium has no element-scoped script-evaluation API, and re-running a ':scope' selector "
          + "at document level cannot reproduce the original parent-scoped match"
      );
    }
    return css;
  }

  @Override
  public com.vibium.Element findOne(Element locator) {
    VibiumElement el = resolveElement(locator);
    if (el == null) {
      return null;
    }
    el.requireInteractionSafe("findOne");
    return el.raw();
  }

  @Override
  public com.vibium.Element findOneChild(Element locator) {
    VibiumElement el = resolveChildElement(locator);
    if (el == null) {
      return null;
    }
    el.requireInteractionSafe("findOneChild");
    return el.raw();
  }

  @Override
  public List<com.vibium.Element> findAll(Element locator) {
    List<VibiumElement> resolved = resolveAllElements(locator);
    if (resolved.isEmpty()) {
      return List.of();
    }
    List<com.vibium.Element> result = new ArrayList<>(resolved.size());
    for (VibiumElement el : resolved) {
      el.requireInteractionSafe("findAll");
      result.add(el.raw());
    }
    return result;
  }

  @Override
  public List<com.vibium.Element> findAllChild(Element locator) {
    List<VibiumElement> resolved = resolveChildElements(locator);
    if (resolved.isEmpty()) {
      return List.of();
    }
    List<com.vibium.Element> result = new ArrayList<>(resolved.size());
    for (VibiumElement el : resolved) {
      el.requireInteractionSafe("findAllChild");
      result.add(el.raw());
    }
    return result;
  }

  @Override
  public ScreenshotCapture capturePage() {
    return new ScreenshotCapture() {
      @Override
      public byte[] visibleOnViewPort() {
        try {
          return session.pageForApi()
            .screenshot();
        } catch (VibiumException e) {
          throw wrap("capturePage.visibleOnViewPort", "n/a", e);
        }
      }

      @Override
      public byte[] fullPage() {
        try {
          return session.pageForApi()
            .screenshot(new ScreenshotOptions().fullPage(true));
        } catch (VibiumException e) {
          throw wrap("capturePage.fullPage", "n/a", e);
        }
      }
    };
  }

  @Override
  public byte[] captureElement(Element locator) {
    // Unlike Playwright's silent "return an empty array if not found", this module's established
    // convention (VibiumElementResolver/VibiumAssertionCapability) is to throw a real resolution
    // error for an operation that genuinely needs a target — a screenshot of a missing element is
    // exactly that, not a legitimate "state not met" query.
    VibiumElement el = requireElement(locator, "captureElement");
    el.requireInteractionSafe("captureElement");
    try {
      return el.raw()
        .screenshot();
    } catch (VibiumException e) {
      throw wrap("captureElement", describeLocator(locator), e);
    }
  }

  @Override
  public byte[] captureRegion(int x, int y, int width, int height) {
    try {
      return session.pageForApi()
        .screenshot(new ScreenshotOptions().clip(x, y, width, height));
    } catch (VibiumException e) {
      throw wrap("captureRegion", "n/a", e);
    }
  }
}
