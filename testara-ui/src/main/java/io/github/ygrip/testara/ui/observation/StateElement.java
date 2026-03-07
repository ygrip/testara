package io.github.ygrip.testara.ui.observation;

import io.github.ygrip.testara.ui.executor.Actor;
import io.github.ygrip.testara.ui.interaction.InteractionContext;
import io.github.ygrip.testara.ui.model.Locator;
import io.github.ygrip.testara.ui.page.Element;

/**
 * Screenplay-style observation: check various state of element.
 *
 * @see Actor#observe(Observation)
 */
public final class StateElement implements Observation<Boolean> {
  private final Element element;
  private final Kind kind;

  private StateElement(Kind kind, Element element) {
    this.kind = kind;
    this.element = element;
  }

  public static StateElementContext of(String locator) {
    return new StateElementContext(Element.of(locator)
      .build());
  }

  public static StateElementContext of(Locator locator) {
    return new StateElementContext(Element.of(locator)
      .build());
  }

  public static StateElementContext of(Element.ElementContext locator) {
    return new StateElementContext(locator.build());
  }

  @Override
  public Observation<Boolean> root(Element root) {
    return new StateElement(kind, root.withChild(element).child());
  }

  @Override
  public Boolean perform(InteractionContext context) {
    try {
      return switch (kind) {
        case HIDDEN -> context.assertion()
          .isHidden(element);
        case VISIBLE -> context.assertion()
          .isVisible(element);
        case ENABLED -> context.assertion()
          .isEnabled(element);
        case DISABLED -> !context.assertion()
          .isEnabled(element);
        case PRESENT -> context.assertion()
          .isPresent(element);
        case CLICKABLE -> {
          context.waits()
            .untilClickable(element);
          yield true;
        }
        case SELECTED -> {
          context.waits()
            .untilSelected(element);
          yield true;
        }
      };
    } catch (Exception ignored) {
      return false;
    }
  }

  private enum Kind {
    VISIBLE, ENABLED, CLICKABLE, DISABLED, PRESENT, HIDDEN, SELECTED
  }


  public static class StateElementContext {
    private final Element element;

    public StateElementContext(Element element) {
      this.element = element;
    }

    public StateElement isVisible() {
      return new StateElement(Kind.VISIBLE, element);
    }

    public StateElement isHidden() {
      return new StateElement(Kind.HIDDEN, element);
    }

    public StateElement isSelected() {
      return new StateElement(Kind.SELECTED, element);
    }

    public StateElement isClickable() {
      return new StateElement(Kind.CLICKABLE, element);
    }

    public StateElement isPresent() {
      return new StateElement(Kind.PRESENT, element);
    }

    public StateElement isEnabled() {
      return new StateElement(Kind.ENABLED, element);
    }

    public StateElement isDisabled() {
      return new StateElement(Kind.DISABLED, element);
    }
  }
}
