package io.github.ygrip.testara.ui.observation;

import io.github.ygrip.testara.ui.executor.Actor;
import io.github.ygrip.testara.ui.interaction.Interaction;
import io.github.ygrip.testara.ui.interaction.InteractionContext;
import io.github.ygrip.testara.ui.model.Locator;
import io.github.ygrip.testara.ui.page.Element;

/**
 * Screenplay-style observation: get attribute of an element.
 *
 * @see Actor#observe(Observation)
 */
public final class TheAttribute implements Observation<String> {
  private final Element element;
  private final String attributeName;

  private TheAttribute(Element element, String attributeName) {
    this.element = element;
    this.attributeName = attributeName;
  }

  public static TheAttributeValue of(String attributeName) {
    return new TheAttributeValue(attributeName);
  }

  @Override
  @SuppressWarnings("unchecked")
  public Observation<String> root(Element root) {
    return new TheAttribute(root.withChild(element).child(), attributeName);
  }

  @Override
  public String perform(InteractionContext context) {
    return context.observation()
      .getAttribute(element, attributeName);
  }

  public static class TheAttributeValue {
    private final String attributeName;

    public TheAttributeValue(String attributeName) {
      this.attributeName = attributeName;
    }

    public TheAttribute on(String locator) {
      return new TheAttribute(
        Element.of(locator)
          .build(), attributeName
      );
    }

    public TheAttribute on(Locator locator) {
      return new TheAttribute(
        Element.of(locator)
          .build(), attributeName
      );
    }

    public TheAttribute on(Element.ElementContext locator) {
      return new TheAttribute(locator.build(), attributeName);
    }
  }
}
