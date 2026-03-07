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
public final class ChildElement implements Observation<Object> {
  private final Element element;

  private ChildElement(Element element) {
    this.element = element;
  }

  public static ChildElementValue locatedBy(String locator) {
    return new ChildElementValue(Element.of(Locator.parse(locator)).build());
  }

  public static ChildElementValue locatedBy(Locator locator) {
    return new ChildElementValue(Element.of(locator).build());
  }

  public static ChildElementValue locatedBy(Element.ElementContext locator) {
    return new ChildElementValue(locator.build());
  }

  @Override
  public Observation<Object> root(Element root) {
    return new ChildElement(root.withChild(this.element));
  }

  @Override
  public Object perform(InteractionContext context) {
    return context.observation()
      .findOneChild(element);
  }


  public static class ChildElementValue {
    private final Element child;

    public ChildElementValue(Element child) {
      this.child = child;
    }

    public ChildElement fromParent(String locator) {
      return new ChildElement(Element.of(locator)
        .build()
        .withChild(child));
    }

    public ChildElement fromParent(Locator locator) {
      return new ChildElement(Element.of(locator)
        .build()
        .withChild(child));
    }

    public ChildElement fromParent(Element.ElementContext locator) {
      return new ChildElement(locator.build()
        .withChild(child));
    }
  }
}
