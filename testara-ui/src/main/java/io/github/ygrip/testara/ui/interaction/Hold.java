package io.github.ygrip.testara.ui.interaction;

import java.time.Duration;

import io.github.ygrip.testara.ui.executor.Actor;
import io.github.ygrip.testara.ui.page.Element;
import io.github.ygrip.testara.ui.model.Locator;

/**
 * Screenplay-style interaction: hold / press an element.
 *
 * @see Actor#attemptsTo(Interaction...)
 */
public final class Hold implements Interaction {
  private final Element locator;
  private final Duration duration;

  private Hold(Element locator, Duration duration) {
    this.duration = duration;
    this.locator = locator;
  }

  public static HoldDuration the(String locator) {
    return new HoldDuration(Element.of(locator).build());
  }
  public static HoldDuration the(Element.ElementContext locator) {
    return new HoldDuration(locator.build());
  }

  public static HoldDuration the(Locator locator) {
    return new HoldDuration(Element.of(locator).build());
  }

  @Override
  public void perform(InteractionContext context) {
    context.interaction()
      .hold(locator, duration);
  }

  @Override
  public Interaction root(Element root) {
    return new Hold(root.withChild(locator).child(), duration);
  }

  /**
   * Fluent step to specify target locator.
   */
  public static final class HoldDuration {
    private final Element locator;

    HoldDuration(Element locator) {
      this.locator = locator;
    }

    public Hold atMost(Duration duration) {
      return new Hold(locator, duration);
    }
  }
}
