package io.github.ygrip.testara.ui.interaction;

import io.github.ygrip.testara.ui.executor.Actor;
import io.github.ygrip.testara.ui.page.Element;
import io.github.ygrip.testara.ui.model.Locator;

/**
 * Screenplay-style interaction: scroll to element.
 *
 * @see Actor#attemptsTo(Interaction...)
 */
public final class Scroll implements Interaction {
  private final Element locator;
  private final boolean alignToTop;

  private Scroll(Element locator, boolean alignToTop) {
    this.locator = locator;
    this.alignToTop = alignToTop;
  }

  public static ScrollTo to(String locator) {
    return new ScrollTo(Element.of(locator)
      .build());
  }

  public static ScrollTo to(Locator locator) {
    return new ScrollTo(Element.of(locator)
      .build());
  }

  public static ScrollTo to(Element.ElementContext locator) {
    return new ScrollTo(locator.build());
  }

  @Override
  public void perform(InteractionContext context) {
    context.interaction()
      .scrollTo(locator, alignToTop);
  }

  @Override
  @SuppressWarnings("unchecked")
  public Interaction root(Element root) {
    return new Scroll(root.withChild(locator).child(), alignToTop);
  }

  /**
   * Fluent step to specify target locator.
   */
  public static final class ScrollTo {
    private final Element target;

    ScrollTo(Element target) {
      this.target = target;
    }

    public Scroll andAlignToTop() {
      return new Scroll(target, true);
    }

    public Scroll andAlignToBottom() {
      return new Scroll(target, false);
    }
  }
}
