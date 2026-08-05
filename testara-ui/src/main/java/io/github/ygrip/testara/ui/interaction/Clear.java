package io.github.ygrip.testara.ui.interaction;

import io.github.ygrip.testara.ui.executor.Actor;
import io.github.ygrip.testara.ui.page.Element;
import io.github.ygrip.testara.ui.model.Locator;

/**
 * Screenplay-style interaction: clear an input element.
 *
 * @see Actor#attemptsTo(Interaction...)
 */
public final class Clear implements Interaction {
  private final Element element;

  private Clear(Element element) {
    this.element = element;
  }

  public static Clear field(String locator) {
    return new Clear(Element.of(locator)
      .build());
  }

  public static Clear field(Locator locator) {
    return new Clear(Element.of(locator)
      .build());
  }

  public static Clear field(Element.ElementContext locator) {
    return new Clear(locator.build());
  }

  @Override
  public void perform(InteractionContext context) {
    context.interaction()
      .clear(element);
  }

  @Override
  @SuppressWarnings("unchecked")
  public Interaction root(Element root) {
    return new Clear(root.withChild(element).child());
  }
}
