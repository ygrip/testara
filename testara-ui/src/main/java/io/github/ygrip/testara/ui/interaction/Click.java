package io.github.ygrip.testara.ui.interaction;

import io.github.ygrip.testara.ui.executor.Actor;
import io.github.ygrip.testara.ui.page.Element;
import io.github.ygrip.testara.ui.model.Locator;

/**
 * Screenplay-style interaction: click an element.
 *
 * @see Actor#attemptsTo(Interaction...)
 */
public final class Click implements Interaction {
  private final Element element;

  private Click(Locator locator) {
    this.element = Element.of(locator)
      .build();
  }

  private Click(Element locator) {
    this.element = locator;
  }

  public static Click on(String locator) {
    return new Click(Locator.parse(locator));
  }

  public static Click on(Locator locator) {
    return new Click(locator);
  }

  public static Click on(Element.ElementContext locator) {
    return new Click(locator.build());
  }

  @Override
  public void perform(InteractionContext context) {
    context.interaction()
      .click(element);
  }

  @Override
  public Interaction root(Element root) {
    return new Click(root.withChild(element).child());
  }
}
