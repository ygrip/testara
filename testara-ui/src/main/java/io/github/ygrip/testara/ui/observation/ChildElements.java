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
public final class ChildElements implements Observation<List<?>> {
  private final Element element;

  private ChildElements(Element element) {
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
  @SuppressWarnings("unchecked")
  public Observation<List<?>> root(Element root) {
    return new ChildElements(root.withChild(element).child());
  }

  @Override
  public List<?> perform(InteractionContext context) {
    return context.observation()
      .findAllChild(element);
  }


  public static class ChildElementValue {
    private final Element child;

    public ChildElementValue(Element child) {
      this.child = child;
    }

    @SuppressWarnings("unchecked")
    public ChildElements fromParent(String locator) {
      return new ChildElements(Element.of(locator)
        .build()
        .withChild(child));
    }

    @SuppressWarnings("unchecked")
    public ChildElements fromParent(Locator locator) {
      return new ChildElements(Element.of(locator)
        .build()
        .withChild(child));
    }

    @SuppressWarnings("unchecked")
    public ChildElements fromParent(Element.ElementContext locator) {
      return new ChildElements(locator.build()
        .withChild(child));
    }
  }
}
