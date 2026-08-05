package io.github.ygrip.testara.ui.interaction;

import io.github.ygrip.testara.ui.executor.Actor;
import io.github.ygrip.testara.ui.model.Locator;
import io.github.ygrip.testara.ui.page.Element;

/**
 * Screenplay-style interaction: focus on an element.
 *
 * @see Actor#attemptsTo(Interaction...)
 */
public final class Focus implements Interaction {
  private final Element element;

  private Focus(Locator locator) {
    this.element = Element.of(locator)
      .build();
  }

  private Focus(Element locator) {
    this.element = locator;
  }

  public static Focus on(String locator) {
    return new Focus(Locator.parse(locator));
  }

  public static Focus on(Locator locator) {
    return new Focus(locator);
  }

  public static Focus on(Element.ElementContext locator) {
    return new Focus(locator.build());
  }

  @Override
  public void perform(InteractionContext context) {
    context.interaction()
      .focus(element);
  }

  @Override
  @SuppressWarnings("unchecked")
  public Interaction root(Element root) {
    return new Focus(root.withChild(element).child());
  }
}
