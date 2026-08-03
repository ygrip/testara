package io.github.ygrip.testara.ui.vibium.locator;

import java.util.ArrayList;
import java.util.List;

import com.vibium.Page;
import com.vibium.errors.ElementNotFoundException;
import com.vibium.types.SelectorOptions;

/**
 * Engine-specific locator produced by {@link VibiumLocatorConverter}. Either a plain CSS selector
 * string (interaction-safe, resolves through the {@code Page.find(String)}/{@code
 * Element.find(String)} overloads) or a semantic {@link SelectorOptions} query (discovery-only,
 * see {@link VibiumElement} for why).
 *
 * <p>Zero matches are surfaced as {@code null} (single element) or an empty list (multiple
 * elements), mirroring the low-level resolution contract used by the Playwright finder — the
 * native {@link ElementNotFoundException} that Vibium throws on a failed {@code find(...)} is
 * caught here rather than left to propagate, so callers get the same "not found" signal
 * regardless of which Vibium find overload was used underneath.
 */
public final class VibiumSelector {

  private final String css;
  private final SelectorOptions options;
  private final String describedAs;

  private VibiumSelector(String css, SelectorOptions options, String describedAs) {
    this.css = css;
    this.options = options;
    this.describedAs = describedAs;
  }

  /** A plain CSS selector string. Interaction-safe. */
  public static VibiumSelector css(String cssSelector) {
    if (cssSelector == null) {
      throw new IllegalArgumentException("cssSelector cannot be null");
    }
    return new VibiumSelector(cssSelector, null, "css:" + cssSelector);
  }

  /** A semantic/xpath query. Discovery-only — see {@link VibiumElement#requireInteractionSafe(String)}. */
  public static VibiumSelector discoveryOnly(SelectorOptions options, String describedAs) {
    if (options == null) {
      throw new IllegalArgumentException("options cannot be null");
    }
    return new VibiumSelector(null, options, describedAs);
  }

  public boolean isInteractionSafe() {
    return css != null;
  }

  public String describedAs() {
    return describedAs;
  }

  public String cssValue() {
    return css;
  }

  public SelectorOptions optionsValue() {
    return options;
  }

  /** Resolve the first match against a page. Returns {@code null} on zero matches. */
  public VibiumElement find(Page page) {
    try {
      com.vibium.Element resolved;
      if (css != null) {
        resolved = page.find(css);
      } else {
        resolved = page.find(options);
      }
      return wrap(resolved);
    } catch (ElementNotFoundException e) {
      return null;
    }
  }

  /** Resolve the first match scoped under an already-resolved element. Returns {@code null} on zero matches. */
  public VibiumElement find(com.vibium.Element scope) {
    try {
      com.vibium.Element resolved;
      if (css != null) {
        resolved = scope.find(css);
      } else {
        resolved = scope.find(options);
      }
      return wrap(resolved);
    } catch (ElementNotFoundException e) {
      return null;
    }
  }

  /** Resolve every match against a page. Returns an empty list on zero matches. */
  public List<VibiumElement> findAll(Page page) {
    try {
      List<com.vibium.Element> resolved;
      if (css != null) {
        resolved = page.findAll(css);
      } else {
        resolved = page.findAll(options);
      }
      return wrapAll(resolved);
    } catch (ElementNotFoundException e) {
      return new ArrayList<>();
    }
  }

  /** Resolve every match scoped under an already-resolved element. Returns an empty list on zero matches. */
  public List<VibiumElement> findAll(com.vibium.Element scope) {
    try {
      List<com.vibium.Element> resolved;
      if (css != null) {
        resolved = scope.findAll(css);
      } else {
        resolved = scope.findAll(options);
      }
      return wrapAll(resolved);
    } catch (ElementNotFoundException e) {
      return new ArrayList<>();
    }
  }

  private VibiumElement wrap(com.vibium.Element resolved) {
    if (resolved == null) {
      return null;
    }
    return new VibiumElement(resolved, isInteractionSafe(), describedAs);
  }

  private List<VibiumElement> wrapAll(List<com.vibium.Element> resolved) {
    List<VibiumElement> result = new ArrayList<>();
    if (resolved == null) {
      return result;
    }
    for (com.vibium.Element element : resolved) {
      result.add(new VibiumElement(element, isInteractionSafe(), describedAs));
    }
    return result;
  }

  @Override
  public String toString() {
    return describedAs;
  }
}
