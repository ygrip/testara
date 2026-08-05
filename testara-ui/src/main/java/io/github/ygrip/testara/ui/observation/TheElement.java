package io.github.ygrip.testara.ui.observation;

import io.github.ygrip.testara.ui.executor.Actor;
import io.github.ygrip.testara.ui.interaction.InteractionContext;
import io.github.ygrip.testara.ui.model.Locator;
import io.github.ygrip.testara.ui.page.Element;

/**
 * Screenplay-style observation: get text of element.
 *
 * @see Actor#observe(Observation)
 */
public final class TheElement implements Observation<Object> {
  private final Element element;

  private TheElement(Element element) {
    this.element = element;
  }

  public static TheElement of(String locator) {
    return new TheElement(Element.of(locator)
      .build());
  }

  public static TheElement of(Locator locator) {
    return new TheElement(Element.of(locator)
      .build());
  }

  public static TheElement of(Element.ElementContext locator) {
    return new TheElement(locator.build());
  }

  @Override
  @SuppressWarnings("unchecked")
  public Observation<Object> root(Element root) {
    return new TheElement(root.withChild(element).child());
  }

  @Override
  public Object perform(InteractionContext context) {
    return context.observation()
      .findOne(element);
  }
}
