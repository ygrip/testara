package io.github.ygrip.testara.ui.observation;

import java.util.List;
import java.util.Optional;

import io.github.ygrip.testara.ui.executor.Actor;
import io.github.ygrip.testara.ui.interaction.InteractionContext;
import io.github.ygrip.testara.ui.model.Locator;
import io.github.ygrip.testara.ui.page.Element;

/**
 * Screenplay-style observation: count of elements.
 *
 * @see Actor#observe(Observation)
 */
public final class CountElements implements Observation<Integer> {
  private final Element element;

  private CountElements(Element element) {
    this.element = element;
  }

  public static CountElements of(String locator) {
    return new CountElements(Element.of(locator)
      .build());
  }

  public static CountElements of(Locator locator) {
    return new CountElements(Element.of(locator)
      .build());
  }

  public static CountElements of(Element.ElementContext locator) {
    return new CountElements(locator.build());
  }

  @Override
  public Observation<Integer> root(Element root) {
    return new CountElements(root.withChild(element).child());
  }

  @Override
  public Integer perform(InteractionContext context) {
    return Optional.ofNullable(context.observation()
      .findAll(element)).map(List::size).orElse(0);
  }
}
