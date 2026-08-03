package io.github.ygrip.testara.ui.vibium.locator;

import com.vibium.types.SelectorOptions;

/**
 * Discovery-only semantic locator builder wrapping {@link SelectorOptions}.
 *
 * <p>Every instance produced by this class resolves through {@code Page.find(SelectorOptions)} /
 * {@code Element.find(SelectorOptions)}, which Vibium 26.5.31's Java client returns with no
 * reusable selector (a real client bug — see {@link VibiumElement} for details). There is
 * therefore no interaction-safe variant of this type: results are usable for a single
 * info()/text()/attr() read right after resolution, and must be flagged discovery-only for any
 * follow-up capability (Phase 3, not built yet).
 *
 * <pre>{@code
 * VibiumLocator.role("button").named("Sign in");
 * VibiumLocator.text("Welcome");
 * VibiumLocator.label("Email address");
 * VibiumLocator.placeholder("Search");
 * VibiumLocator.testId("submit-login");
 * VibiumLocator.alt("Company logo");
 * VibiumLocator.title("Close");
 * VibiumLocator.xpath("//main//button");
 * }</pre>
 */
public final class VibiumLocator {

  private final SelectorOptions options;
  private final String describedAs;

  private VibiumLocator(SelectorOptions options, String describedAs) {
    this.options = options;
    this.describedAs = describedAs;
  }

  public static VibiumLocator role(String role) {
    return new VibiumLocator(new SelectorOptions().role(role), "role:" + role);
  }

  /** Narrow a {@link #role(String)} locator by accessible name. */
  public VibiumLocator named(String accessibleName) {
    return new VibiumLocator(this.options.text(accessibleName), this.describedAs + " named:" + accessibleName);
  }

  public static VibiumLocator text(String text) {
    return new VibiumLocator(new SelectorOptions().text(text), "text:" + text);
  }

  public static VibiumLocator label(String label) {
    return new VibiumLocator(new SelectorOptions().label(label), "label:" + label);
  }

  public static VibiumLocator placeholder(String placeholder) {
    return new VibiumLocator(new SelectorOptions().placeholder(placeholder), "placeholder:" + placeholder);
  }

  public static VibiumLocator testId(String testId) {
    return new VibiumLocator(new SelectorOptions().testid(testId), "testid:" + testId);
  }

  public static VibiumLocator alt(String alt) {
    return new VibiumLocator(new SelectorOptions().alt(alt), "alt:" + alt);
  }

  public static VibiumLocator title(String title) {
    return new VibiumLocator(new SelectorOptions().title(title), "title:" + title);
  }

  public static VibiumLocator xpath(String xpath) {
    return new VibiumLocator(new SelectorOptions().xpath(xpath), "xpath:" + xpath);
  }

  public SelectorOptions options() {
    return options;
  }

  public String describedAs() {
    return describedAs;
  }

  /** Convert to the finder-level {@link VibiumSelector} representation. Always discovery-only. */
  public VibiumSelector toSelector() {
    return VibiumSelector.discoveryOnly(options, describedAs);
  }
}
