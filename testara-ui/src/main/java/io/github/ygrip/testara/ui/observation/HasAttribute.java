package io.github.ygrip.testara.ui.observation;

import org.apache.commons.lang3.ObjectUtils;

import io.github.ygrip.testara.ui.executor.Actor;
import io.github.ygrip.testara.ui.interaction.InteractionContext;
import io.github.ygrip.testara.ui.model.Locator;
import io.github.ygrip.testara.ui.page.Element;

/**
 * Screenplay-style observation: check the attribute of an element.
 *
 * @see Actor#observe(Observation)
 */
public final class HasAttribute implements Observation<Boolean> {
  private final Element element;
  private final String attributeName;

  private HasAttribute(Element element, String attributeName) {
    this.element = element;
    this.attributeName = attributeName;
  }

  public static HasAttributeValue of(String attributeName) {
    return new HasAttributeValue(attributeName);
  }

  @Override
  public Observation<Boolean> root(Element root) {
    return new HasAttribute(root.withChild(element).child(), attributeName);
  }

  @Override
  public Boolean perform(InteractionContext context) {
    return ObjectUtils.isNotEmpty(context.observation()
      .getAttribute(element, attributeName));
  }

  public static class HasAttributeValue {
    private final String attributeName;

    public HasAttributeValue(String attributeName) {
      this.attributeName = attributeName;
    }

    public HasAttribute on(String locator) {
      return new HasAttribute(
        Element.of(locator)
          .build(), attributeName
      );
    }

    public HasAttribute on(Locator locator) {
      return new HasAttribute(
        Element.of(locator)
          .build(), attributeName
      );
    }

    public HasAttribute on(Element.ElementContext locator) {
      return new HasAttribute(locator.build(), attributeName);
    }
  }
}
