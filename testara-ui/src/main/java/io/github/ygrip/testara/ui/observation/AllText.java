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
public final class AllText implements Observation<List<String>> {
  private final Element element;

  private AllText(Element element) {
    this.element = element;
  }

  public static AllText of(String locator) {
    return new AllText(Element.of(locator)
      .build());
  }

  public static AllText of(Locator locator) {
    return new AllText(Element.of(locator)
      .build());
  }

  public static AllText of(Element.ElementContext locator) {
    return new AllText(locator.build());
  }

  @Override
  public Observation<List<String>> root(Element root) {
    return new AllText(root.withChild(element).child());
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<String> perform(InteractionContext context) {
    return (List<String>) context.observation()
      .getTexts(element);
  }
}
