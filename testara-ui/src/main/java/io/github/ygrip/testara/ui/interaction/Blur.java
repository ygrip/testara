package io.github.ygrip.testara.ui.interaction;

import io.github.ygrip.testara.ui.executor.Actor;
import io.github.ygrip.testara.ui.model.Locator;
import io.github.ygrip.testara.ui.page.Element;

/**
 * Screenplay-style interaction: blur on an element.
 *
 * @see Actor#attemptsTo(Interaction...)
 */
public final class Blur implements Interaction {
  private final Element element;

  private Blur(Locator locator) {
    this.element = Element.of(locator)
      .build();
  }

  private Blur(Element locator) {
    this.element = locator;
  }

  public static Blur from(String locator) {
    return new Blur(Locator.parse(locator));
  }

  public static Blur from(Locator locator) {
    return new Blur(locator);
  }

  public static Blur from(Element.ElementContext locator) {
    return new Blur(locator.build());
  }

  @Override
  public void perform(InteractionContext context) {
    context.interaction()
      .blur(element);
  }

  @Override
  public Interaction root(Element root) {
    return new Blur(root.withChild(element).child());
  }
}
