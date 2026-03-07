package io.github.ygrip.testara.ui.observation;

import io.github.ygrip.testara.ui.executor.Actor;
import io.github.ygrip.testara.ui.interaction.Interaction;
import io.github.ygrip.testara.ui.interaction.InteractionContext;
import io.github.ygrip.testara.ui.model.Locator;
import io.github.ygrip.testara.ui.page.Element;

/**
 * Screenplay-style observation: get css value of element.
 *
 * @see Actor#observe(Observation)
 */
public final class TheCss implements Observation<String> {
  private final Element element;
  private final String propertyName;

  private TheCss(Element element, String propertyName) {
    this.element = element;
    this.propertyName = propertyName;
  }

  public static TheCssValue of(String propertyName) {
    return new TheCssValue(propertyName);
  }

  @Override
  public Observation<String> root(Element root) {
    return new TheCss(root.withChild(element).child(), propertyName);
  }

  @Override
  public String perform(InteractionContext context) {
    return context.observation()
      .getCssValue(element, propertyName);
  }

  public static class TheCssValue {
    private final String attributeName;

    public TheCssValue(String attributeName) {
      this.attributeName = attributeName;
    }

    public TheCss on(String locator) {
      return new TheCss(
        Element.of(locator)
          .build(), attributeName
      );
    }

    public TheCss on(Locator locator) {
      return new TheCss(
        Element.of(locator)
          .build(), attributeName
      );
    }

    public TheCss on(Element.ElementContext locator) {
      return new TheCss(locator.build(), attributeName);
    }
  }
}
