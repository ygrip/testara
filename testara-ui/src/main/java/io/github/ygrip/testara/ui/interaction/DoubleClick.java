package io.github.ygrip.testara.ui.interaction;

import io.github.ygrip.testara.ui.executor.Actor;
import io.github.ygrip.testara.ui.page.Element;
import io.github.ygrip.testara.ui.model.Locator;

/**
 * Screenplay-style interaction: double click an element.
 *
 * @see Actor#attemptsTo(Interaction...)
 */
public final class DoubleClick implements Interaction {
  private final Element element;

  private DoubleClick(Element element) {
    this.element = element;
  }

  public static DoubleClick on(String locator) {
    return new DoubleClick(Element.of(locator).build());
  }

  public static DoubleClick on(Locator locator) {
    return new DoubleClick(Element.of(locator).build());
  }
  public static DoubleClick on(Element.ElementContext locator) {
    return new DoubleClick(locator.build());
  }

  @Override
  public void perform(InteractionContext context) {
    context.interaction().doubleClick(element);
  }

  @Override
  @SuppressWarnings("unchecked")
  public Interaction root(Element root) {
    return new DoubleClick(root.withChild(element).child());
  }
}
