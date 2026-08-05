package io.github.ygrip.testara.ui.observation;

import io.github.ygrip.testara.ui.executor.Actor;
import io.github.ygrip.testara.ui.interaction.Interaction;
import io.github.ygrip.testara.ui.interaction.InteractionContext;
import io.github.ygrip.testara.ui.model.Locator;
import io.github.ygrip.testara.ui.page.Element;

/**
 * Screenplay-style observation: get text of element.
 *
 * @see Actor#observe(Observation)
 */
public final class TheText implements Observation<String> {
  private final Element element;

  private TheText(Element element) {
    this.element = element;
  }

  public static TheText of(String locator) {
    return new TheText(Element.of(locator)
      .build());
  }

  public static TheText of(Locator locator) {
    return new TheText(Element.of(locator)
      .build());
  }

  public static TheText of(Element.ElementContext locator) {
    return new TheText(locator.build());
  }

  @Override
  @SuppressWarnings("unchecked")
  public Observation<String> root(Element root) {
    return new TheText(root.withChild(element).child());
  }

  @Override
  public String perform(InteractionContext context) {
    return context.observation()
      .getText(element);
  }
}
