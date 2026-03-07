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
public final class AllValue implements Observation<List<String>> {
  private final Element element;

  private AllValue(Element element) {
    this.element = element;
  }

  public static AllValue of(String locator) {
    return new AllValue(Element.of(locator)
      .build());
  }

  public static AllValue of(Locator locator) {
    return new AllValue(Element.of(locator)
      .build());
  }

  public static AllValue of(Element.ElementContext locator) {
    return new AllValue(locator.build());
  }

  @Override
  public Observation<List<String>> root(Element root) {
    return new AllValue(root.withChild(element).child());
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<String> perform(InteractionContext context) {
    return (List<String>) context.observation()
      .getValues(element);
  }
}
