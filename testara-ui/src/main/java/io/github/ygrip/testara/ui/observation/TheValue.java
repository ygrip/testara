package io.github.ygrip.testara.ui.observation;

import io.github.ygrip.testara.ui.executor.Actor;
import io.github.ygrip.testara.ui.interaction.InteractionContext;
import io.github.ygrip.testara.ui.model.Locator;
import io.github.ygrip.testara.ui.page.Element;

/**
 * Screenplay-style observation: get value of element.
 *
 * @see Actor#observe(Observation)
 */
public final class TheValue implements Observation<String> {
  private final Element element;

  private TheValue(Element element) {
    this.element = element;
  }

  public static TheValue of(String locator) {
    return new TheValue(Element.of(locator)
      .build());
  }

  public static TheValue of(Locator locator) {
    return new TheValue(Element.of(locator)
      .build());
  }

  public static TheValue of(Element.ElementContext locator) {
    return new TheValue(locator.build());
  }

  @Override
  @SuppressWarnings("unchecked")
  public Observation<String> root(Element root) {
    return new TheValue(root.withChild(element).child());
  }

  @Override
  public String perform(InteractionContext context) {
    return context.observation()
      .getValue(element);
  }
}
