package io.github.ygrip.testara.ui.observation;

import java.util.List;

import io.github.ygrip.testara.ui.executor.Actor;
import io.github.ygrip.testara.ui.interaction.InteractionContext;
import io.github.ygrip.testara.ui.model.Locator;
import io.github.ygrip.testara.ui.page.Element;

/**
 * Screenplay-style observation: get text of element.
 *
 * @see Actor#observe(Observation)
 */
public final class TheElements implements Observation<List<?>> {
  private final Element element;

  private TheElements(Element element) {
    this.element = element;
  }

  public static TheElements of(String locator) {
    return new TheElements(Element.of(locator)
      .build());
  }

  public static TheElements of(Locator locator) {
    return new TheElements(Element.of(locator)
      .build());
  }

  public static TheElements of(Element.ElementContext locator) {
    return new TheElements(locator.build());
  }

  @Override
  @SuppressWarnings("unchecked")
  public Observation<List<?>> root(Element root) {
    return new TheElements(root.withChild(element).child());
  }

  @Override
  public List<?> perform(InteractionContext context) {
    return context.observation()
      .findAll(element);
  }
}
