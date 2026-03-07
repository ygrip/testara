package io.github.ygrip.testara.ui.interaction;

import io.github.ygrip.testara.ui.executor.Actor;
import io.github.ygrip.testara.ui.page.Element;
import io.github.ygrip.testara.ui.model.Locator;

/**
 * Screenplay-style interaction: click an element.
 *
 * @see Actor#attemptsTo(Interaction...)
 */
public final class ForceClick implements Interaction {
  private final Element element;

  private ForceClick(Locator locator) {
    this.element = Element.of(locator)
      .build();
  }

  private ForceClick(Element locator) {
    this.element = locator;
  }

  public static ForceClick on(String locator) {
    return new ForceClick(Locator.parse(locator));
  }

  public static ForceClick on(Locator locator) {
    return new ForceClick(locator);
  }

  public static ForceClick on(Element.ElementContext locator) {
    return new ForceClick(locator.build());
  }

  @Override
  public void perform(InteractionContext context) {
    context.interaction()
      .forceClick(element);
  }

  @Override
  public Interaction root(Element root) {
    return new ForceClick(root.withChild(element).child());
  }
}
