package io.github.ygrip.testara.ui.vibium.capability;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.vibium.errors.VibiumException;
import com.vibium.types.BoundingBox;

import io.github.ygrip.testara.ui.capability.InteractionCapability;
import io.github.ygrip.testara.ui.page.Element;
import io.github.ygrip.testara.ui.vibium.driver.VibiumSession;
import io.github.ygrip.testara.ui.vibium.error.UnsupportedVibiumCapabilityException;
import io.github.ygrip.testara.ui.vibium.error.VibiumOperationException;
import io.github.ygrip.testara.ui.vibium.locator.VibiumElement;

import lombok.extern.log4j.Log4j2;

/**
 * Vibium's {@link InteractionCapability}.
 *
 * <p>Every native {@code com.vibium.Element} call is preceded by {@link
 * VibiumElement#requireInteractionSafe}, exactly like {@link VibiumAssertionCapability}/{@link
 * VibiumWaitCapability}: a discovery-only element (semantic/xpath/link-text resolved) will throw a
 * {@link UnsupportedVibiumCapabilityException} rather than a confusing native {@code
 * ElementNotFoundException} on the very first follow-up call.
 *
 * <p>Real-API findings (verified with {@code javap} against {@code vibium-26.5.31.jar}) that shape
 * several methods below:
 * <ul>
 *   <li>{@code Page#evaluate(String)} has a single, synchronous, String-only overload — no second
 *       "arguments" parameter like Playwright's {@code page.evaluate(script, arg)}. {@link
 *       #executeScript} therefore serializes {@code args} directly into the generated script text
 *       (see {@link #buildEvaluableScript}) instead of passing them natively.
 *   <li>{@code com.vibium.Element} has no {@code evaluate}/scoped-script method at all, so any
 *       "run a script against this specific element" need (blur/submit/select-by-index/text) is
 *       implemented by focusing the element first and then having the page-level script operate on
 *       {@code document.activeElement} — this needs no element handle or selector at all, unlike
 *       {@link VibiumObservationCapability#getCssValue}, which does need one and documents a real
 *       limitation as a result.
 *   <li>{@code Element#scrollIntoView()} takes no alignment argument (unlike Playwright's JS
 *       {@code el.scrollIntoView(alignToTop)}) — {@code alignToTop} cannot be honored; this is a
 *       known/accepted limitation per this module's implementation plan §12, not worked around.
 *   <li>{@code Page#mouse()} genuinely exists ({@code com.vibium.Mouse} with
 *       {@code move/down/up/click/wheel}), confirmed via {@code javap}, so {@link #hold} and the
 *       offset overload of {@link #drag(Element, int, int)} use it directly rather than falling
 *       back to a synthetic-event workaround.
 * </ul>
 */
@Log4j2
public final class VibiumInteractionCapability extends VibiumElementResolver implements InteractionCapability {

  public VibiumInteractionCapability(VibiumSession session) {
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

  /**
   * Resolve every match for {@code locator} (mirrors {@link VibiumElementResolver#resolveElement}
   * but for {@code Element#all()}). Duplicated locally rather than added to the shared {@code
   * VibiumElementResolver} base (out of scope for this change; {@code bindToSessionFinder} there is
   * private) — the same duplication already exists between Playwright's Interaction/Observation
   * capability classes for their own resolver helpers.
   */
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

  private VibiumOperationException wrap(String operation, String locatorDescription, VibiumException cause) {
    return VibiumOperationException.of(operation, locatorDescription, safePageUrl(), 0L, cause);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> T executeScript(String script, Object... args) {
    String wrapped = buildEvaluableScript(script, args);
    try {
      return (T) session.pageForApi()
        .evaluate(wrapped);
    } catch (VibiumException e) {
      throw wrap("executeScript", "n/a", e);
    }
  }

  @Override
  public <T> T executeScriptAsync(String script, Object... args) {
    throw new UnsupportedVibiumCapabilityException(
      "executeScriptAsync",
      "com.vibium.Page#evaluate(String) (confirmed via javap against vibium-26.5.31.jar) is a "
        + "single synchronous String-only overload with no promise/await-aware variant; there is no "
        + "confirmed native way to run and await an async script without risking the return value "
        + "being read before the underlying promise settles, so this is deliberately unsupported "
        + "rather than approximated"
    );
  }

  @Override
  public InteractionCapability scrollTo(Element locator, boolean alignToTop) {
    VibiumElement el = requireElement(locator, "scrollTo");
    el.requireInteractionSafe("scrollTo");
    try {
      // See class javadoc: Element#scrollIntoView() takes no alignment parameter in this client;
      // alignToTop cannot be honored (plan §12 "document alignment limitation").
      el.raw()
        .scrollIntoView();
    } catch (VibiumException e) {
      throw wrap("scrollTo", describeLocator(locator), e);
    }
    return this;
  }

  @Override
  public InteractionCapability click(Element locator) {
    VibiumElement el = requireElement(locator, "click");
    el.requireInteractionSafe("click");
    try {
      el.raw()
        .click();
    } catch (VibiumException e) {
      throw wrap("click", describeLocator(locator), e);
    }
    return this;
  }

  @Override
  public InteractionCapability focus(Element locator) {
    VibiumElement el = requireElement(locator, "focus");
    el.requireInteractionSafe("focus");
    try {
      el.raw()
        .focus();
    } catch (VibiumException e) {
      throw wrap("focus", describeLocator(locator), e);
    }
    return this;
  }

  @Override
  public InteractionCapability blur(Element locator) {
    VibiumElement el = requireElement(locator, "blur");
    el.requireInteractionSafe("blur");
    try {
      el.raw()
        .focus();
      session.pageForApi()
        .evaluate("document.activeElement && document.activeElement.blur();");
    } catch (VibiumException e) {
      throw wrap("blur", describeLocator(locator), e);
    }
    return this;
  }

  @Override
  public InteractionCapability forceClick(Element locator) {
    VibiumElement el = requireElement(locator, "forceClick");
    el.requireInteractionSafe("forceClick");
    try {
      el.raw()
        .dispatchEvent("click", Map.of("bubbles", true, "cancelable", true));
    } catch (VibiumException e) {
      throw wrap("forceClick", describeLocator(locator), e);
    }
    return this;
  }

  @Override
  public InteractionCapability doubleClick(Element locator) {
    VibiumElement el = requireElement(locator, "doubleClick");
    el.requireInteractionSafe("doubleClick");
    try {
      el.raw()
        .dblclick();
    } catch (VibiumException e) {
      throw wrap("doubleClick", describeLocator(locator), e);
    }
    return this;
  }

  @Override
  public InteractionCapability hover(Element locator) {
    VibiumElement el = requireElement(locator, "hover");
    el.requireInteractionSafe("hover");
    try {
      el.raw()
        .hover();
    } catch (VibiumException e) {
      throw wrap("hover", describeLocator(locator), e);
    }
    return this;
  }

  @Override
  public InteractionCapability hold(Element locator, Duration duration) {
    VibiumElement el = requireElement(locator, "hold");
    el.requireInteractionSafe("hold");
    try {
      BoundingBox box = el.raw()
        .boundingBox();
      if (box != null) {
        double x = box.x() + box.width() / 2;
        double y = box.y() + box.height() / 2;
        com.vibium.Page page = session.pageForApi();
        page.mouse()
          .move(x, y);
        page.mouse()
          .down();
        page.sleep(duration.toMillis());
        page.mouse()
          .up();
      }
    } catch (VibiumException e) {
      throw wrap("hold", describeLocator(locator), e);
    }
    return this;
  }

  @Override
  public InteractionCapability drag(Element source, Element target) {
    VibiumElement sourceEl = requireElement(source, "drag");
    sourceEl.requireInteractionSafe("drag");
    VibiumElement targetEl = requireElement(target, "drag");
    targetEl.requireInteractionSafe("drag");
    try {
      sourceEl.raw()
        .dragTo(targetEl.raw());
    } catch (VibiumException e) {
      throw wrap("drag", describeLocator(source), e);
    }
    return this;
  }

  @Override
  public InteractionCapability drag(Element source, int xOffset, int yOffset) {
    VibiumElement el = requireElement(source, "drag");
    el.requireInteractionSafe("drag");
    try {
      BoundingBox box = el.raw()
        .boundingBox();
      if (box != null) {
        double sx = box.x() + box.width() / 2;
        double sy = box.y() + box.height() / 2;
        com.vibium.Page page = session.pageForApi();
        page.mouse()
          .move(sx, sy);
        page.mouse()
          .down();
        page.mouse()
          .move(sx + xOffset, sy + yOffset);
        page.mouse()
          .up();
      }
    } catch (VibiumException e) {
      throw wrap("drag", describeLocator(source), e);
    }
    return this;
  }

  @Override
  public TextEntry enter(String text) {
    return locator -> {
      VibiumElement el = requireElement(locator, "enter");
      el.requireInteractionSafe("enter");
      try {
        // Replicates Playwright's enter().into() replace semantics (clear() then fill()) even
        // though the underlying native calls differ, per this module's implementation plan §12.
        el.raw()
          .clear();
        el.raw()
          .fill(text);
      } catch (VibiumException e) {
        throw wrap("enter", describeLocator(locator), e);
      }
      return VibiumInteractionCapability.this;
    };
  }

  @Override
  public InteractionCapability clear(Element locator) {
    VibiumElement el = requireElement(locator, "clear");
    el.requireInteractionSafe("clear");
    try {
      el.raw()
        .clear();
    } catch (VibiumException e) {
      throw wrap("clear", describeLocator(locator), e);
    }
    return this;
  }

  @Override
  public InteractionCapability submit(Element locator) {
    VibiumElement el = requireElement(locator, "submit");
    el.requireInteractionSafe("submit");
    try {
      el.raw()
        .focus();
      session.pageForApi()
        .evaluate(
          "(function(){var el=document.activeElement;var form=el&&(el.form||(el.closest?el.closest('form'):null));"
            + "if(form){if(form.requestSubmit){form.requestSubmit();}else{form.submit();}}})();"
        );
    } catch (VibiumException e) {
      throw wrap("submit", describeLocator(locator), e);
    }
    return this;
  }

  @Override
  public Object findElement(Element locator) {
    VibiumElement el = resolveElement(locator);
    if (el == null) {
      return null;
    }
    // Gate before handing back the raw native handle: only an interaction-safe (CSS-derived)
    // element supports ANY follow-up native call — see VibiumElement's javadoc. Returning a raw
    // handle from a discovery-only resolution here would hand the caller a "found" element that
    // explodes on first use instead of the clear, typed rejection every other capability method in
    // this module already gives.
    el.requireInteractionSafe("findElement");
    return el.raw();
  }

  @Override
  public List<?> findElements(Element locator) {
    List<VibiumElement> resolved = resolveAllElements(locator);
    if (resolved.isEmpty()) {
      return List.of();
    }
    List<Object> result = new ArrayList<>(resolved.size());
    for (VibiumElement el : resolved) {
      el.requireInteractionSafe("findElements");
      result.add(el.raw());
    }
    return result;
  }

  @Override
  public SelectOption selectOption(Element locator) {
    return new SelectOption() {
      @Override
      public InteractionCapability byValue(String value) {
        VibiumElement el = requireElement(locator, "selectOption.byValue");
        el.requireInteractionSafe("selectOption.byValue");
        try {
          el.raw()
            .selectOption(value);
        } catch (VibiumException e) {
          throw wrap("selectOption.byValue", describeLocator(locator), e);
        }
        return VibiumInteractionCapability.this;
      }

      @Override
      public InteractionCapability byIndex(int index) {
        VibiumElement el = requireElement(locator, "selectOption.byIndex");
        el.requireInteractionSafe("selectOption.byIndex");
        try {
          el.raw()
            .focus();
          String script = "(function(){var el=document.activeElement;if(!el||!el.options)return;"
            + "if(" + index + "<0||" + index + ">=el.options.length)return;el.selectedIndex=" + index + ";"
            + "el.dispatchEvent(new Event('input',{bubbles:true}));"
            + "el.dispatchEvent(new Event('change',{bubbles:true}));})();";
          session.pageForApi()
            .evaluate(script);
        } catch (VibiumException e) {
          throw wrap("selectOption.byIndex", describeLocator(locator), e);
        }
        return VibiumInteractionCapability.this;
      }

      @Override
      public InteractionCapability byVisibleText(String visibleText) {
        VibiumElement el = requireElement(locator, "selectOption.byVisibleText");
        el.requireInteractionSafe("selectOption.byVisibleText");
        try {
          el.raw()
            .focus();
          String script = "(function(){var el=document.activeElement;if(!el||!el.options)return;"
            + "for(var i=0;i<el.options.length;i++){if(el.options[i].text===" + jsonQuote(visibleText) + "){"
            + "el.selectedIndex=i;break;}}"
            + "el.dispatchEvent(new Event('input',{bubbles:true}));"
            + "el.dispatchEvent(new Event('change',{bubbles:true}));})();";
          session.pageForApi()
            .evaluate(script);
        } catch (VibiumException e) {
          throw wrap("selectOption.byVisibleText", describeLocator(locator), e);
        }
        return VibiumInteractionCapability.this;
      }
    };
  }

  /**
   * Builds a self-invoking script that exposes {@code args} as {@code seleniumArgs} (and rewrites
   * any Selenium-style {@code arguments[...]} reference in the caller's script body to match) —
   * see class javadoc for why the args cannot be passed as a real {@code Page#evaluate} parameter.
   */
  static String buildEvaluableScript(String script, Object[] args) {
    String body = Optional.ofNullable(script)
      .orElse("")
      .replaceAll("\\barguments\\s*\\[", "seleniumArgs[");
    StringBuilder argsLiteral = new StringBuilder("[");
    for (int i = 0; i < args.length; i++) {
      if (i > 0) {
        argsLiteral.append(",");
      }
      argsLiteral.append(toJsonLiteral(args[i]));
    }
    argsLiteral.append("]");
    return "(function(){var seleniumArgs=" + argsLiteral + ";" + body + "\n})()";
  }

  /**
   * Minimal, dependency-free JSON-literal encoding for script arguments. {@code com.google.gson}
   * (used internally by Vibium's own client classes) is only a {@code runtime}-scope transitive
   * dependency of the {@code vibium} artifact, not a compile-scope one for this module, so it is
   * not used here; only {@link String}/{@link Number}/{@link Boolean}/{@code null} round-trip
   * exactly — anything else falls back to its {@code toString()} wrapped as a JS string literal,
   * which is a known limitation for complex object arguments.
   */
  static String toJsonLiteral(Object value) {
    if (value == null) {
      return "null";
    }
    if (value instanceof Boolean || value instanceof Number) {
      return value.toString();
    }
    return jsonQuote(value.toString());
  }

  static String jsonQuote(String value) {
    StringBuilder sb = new StringBuilder("\"");
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      switch (c) {
        case '"' -> sb.append("\\\"");
        case '\\' -> sb.append("\\\\");
        case '\n' -> sb.append("\\n");
        case '\r' -> sb.append("\\r");
        case '\t' -> sb.append("\\t");
        default -> {
          if (c < 0x20) {
            sb.append(String.format("\\u%04x", (int) c));
          } else {
            sb.append(c);
          }
        }
      }
    }
    sb.append("\"");
    return sb.toString();
  }
}
