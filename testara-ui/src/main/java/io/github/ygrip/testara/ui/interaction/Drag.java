package io.github.ygrip.testara.ui.interaction;

import java.util.Optional;

import org.apache.commons.lang3.ObjectUtils;

import io.github.ygrip.testara.ui.executor.Actor;
import io.github.ygrip.testara.ui.model.Locator;
import io.github.ygrip.testara.ui.page.Element;

/**
 * Screenplay-style interaction: drag element.
 *
 * @see Actor#attemptsTo(Interaction...)
 */
public final class Drag implements Interaction {
  private final Integer xOffset;
  private final Integer yOffset;
  private final Element source;
  private final Element target;

  private Drag(Element source, Element target) {
    this.source = source;
    this.target = target;
    this.xOffset = null;
    this.yOffset = null;
  }

  private Drag(Element source, Integer xOffset, Integer yOffset) {
    this.source = source;
    this.target = null;
    this.xOffset = xOffset;
    this.yOffset = yOffset;
  }

  public static DragElement element(Locator source) {
    return new DragElement(Element.of(source)
      .build());
  }

  public static DragElement element(Element.ElementContext source) {
    return new DragElement(source.build());
  }

  public static DragElement element(String source) {
    return new DragElement(Element.of(source)
      .build());
  }

  @Override
  public void perform(InteractionContext context) {
    if (ObjectUtils.isNotEmpty(target)) {
      context.interaction()
        .drag(source, target);
    } else if (ObjectUtils.isNotEmpty(xOffset) && ObjectUtils.isNotEmpty(yOffset)) {
      context.interaction()
        .drag(source, xOffset, yOffset);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public Interaction root(Element root) {
    return Optional.ofNullable(target)
      .map(result -> new Drag(root.withChild(source).child(), root.withChild(target).child()))
      .orElseGet(() -> new Drag(root.withChild(source).child(), xOffset, yOffset));
  }

  /**
   * Fluent step to specify target locator.
   */
  public static final class DragElement {
    private final Element source;

    DragElement(Element source) {
      this.source = source;
    }

    public Drag into(String locator) {
      return new Drag(
        source,
        Element.of(locator)
          .build()
      );
    }

    public Drag into(Locator locator) {
      return new Drag(
        source,
        Element.of(locator)
          .build()
      );
    }

    public Drag into(Element.ElementContext locator) {
      return new Drag(source, locator.build());
    }

    public Drag into(int xOffset, int yOffset) {
      return new Drag(source, xOffset, yOffset);
    }
  }
}
