package io.github.ygrip.testara.ui.interaction;

import io.github.ygrip.testara.ui.executor.Actor;
import io.github.ygrip.testara.ui.model.Locator;
import io.github.ygrip.testara.ui.page.Element;

/**
 * Screenplay-style interaction: hover an element.
 *
 * @see Actor#attemptsTo(Interaction...)
 */
public final class Hover implements Interaction {
  private final Element locator;

  private Hover(Element locator) {
    this.locator = locator;
  }

  public static Hover on(String locator) {
    return new Hover(Element.of(locator)
      .build());
  }

  public static Hover on(Locator locator) {
    return new Hover(Element.of(locator)
      .build());
  }

  public static Hover on(Element.ElementContext locator) {
    return new Hover(locator.build());
  }

  @Override
  public void perform(InteractionContext context) {
    context.interaction()
      .hover(locator);
  }

  @Override
  @SuppressWarnings("unchecked")
  public Interaction root(Element root) {
    return new Hover(root.withChild(locator).child());
  }
}
