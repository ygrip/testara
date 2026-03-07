package io.github.ygrip.testara.ui.interaction;

import io.github.ygrip.testara.ui.executor.Actor;
import io.github.ygrip.testara.ui.page.Element;
import io.github.ygrip.testara.ui.model.Locator;

/**
 * Screenplay-style interaction: submit an element.
 *
 * @see Actor#attemptsTo(Interaction...)
 */
public final class Submit implements Interaction {
  private final Element element;

  private Submit(Locator locator) {
    this.element = Element.of(locator)
      .build();
  }

  private Submit(Element locator) {
    this.element = locator;
  }

  public static Submit into(String locator) {
    return new Submit(Locator.parse(locator));
  }

  public static Submit into(Locator locator) {
    return new Submit(locator);
  }

  public static Submit into(Element.ElementContext locator) {
    return new Submit(locator.build());
  }

  @Override
  public void perform(InteractionContext context) {
    context.interaction()
      .submit(element);
  }

  @Override
  public Interaction root(Element root) {
    return new Submit(root.withChild(element).child());
  }
}
