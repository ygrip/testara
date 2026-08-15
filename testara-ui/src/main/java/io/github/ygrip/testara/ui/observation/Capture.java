package io.github.ygrip.testara.ui.observation;

import io.github.ygrip.testara.ui.executor.Actor;
import io.github.ygrip.testara.ui.interaction.InteractionContext;
import io.github.ygrip.testara.ui.model.CapturedScreenshot;
import io.github.ygrip.testara.ui.model.Locator;
import io.github.ygrip.testara.ui.model.ScreenshotQuality;
import io.github.ygrip.testara.ui.page.Element;

/**
 * Screenplay-style observation: capture a screenshot and return the PNG bytes.
 * <pre>
 *   byte[] png = actor.observe(Capture.page().visibleOnViewPort());
 *   byte[] png = actor.observe(Capture.page().fullPage());
 *   byte[] png = actor.observe(Capture.element("css:#my-element"));
 *   byte[] png = actor.observe(Capture.dimension(100, 200, 400, 300));
 * </pre>
 *
 * @see Actor#observe(Observation)
 */
public final class Capture implements Observation<byte[]> {
  private final Target target;
  private final Element element;
  private final Dimension dimension;

  private Capture(Target target, Element element, Dimension dimension) {
    this.target = target;
    this.element = element;
    this.dimension = dimension;
  }

  public static PageCapture page() {
    return new PageCapture();
  }

  public static Capture element(String locator) {
    return new Capture(Target.ELEMENT, Element.of(locator).build(), null);
  }

  public static Capture element(Locator locator) {
    return new Capture(Target.ELEMENT, Element.of(locator).build(), null);
  }

  public static Capture element(Element.ElementContext locator) {
    return new Capture(Target.ELEMENT, locator.build(), null);
  }

  public static Capture dimension(Dimension dimension) {
    return new Capture(Target.REGION, null, dimension);
  }

  public static Capture dimension(int x, int y, int width, int height) {
    return dimension(new Dimension(x, y, width, height));
  }

  @Override
  public byte[] perform(InteractionContext context) {
    return switch (target) {
      case VIEWPORT -> context.observation().capturePage().visibleOnViewPort();
      case FULL_PAGE -> context.observation().capturePage().fullPage();
      case ELEMENT -> context.observation().captureElement(element);
      case REGION -> context.observation().captureRegion(
        dimension.x(), dimension.y(), dimension.width(), dimension.height());
    };
  }

  @Override
  @SuppressWarnings("unchecked")
  public Observation<byte[]> root(Element root) {
    if (target == Target.ELEMENT) {
      return new Capture(Target.ELEMENT, root.withChild(element).child(), null);
    }
    return this;
  }

  private enum Target {
    VIEWPORT, FULL_PAGE, ELEMENT, REGION
  }

  /** Defines a rectangular region on the page (position + size). */
  public record Dimension(int x, int y, int width, int height) {}

  /** Fluent step returned by {@link Capture#page()} to choose the capture mode. */
  public static final class PageCapture {
    PageCapture() {}

    public Capture visibleOnViewPort() {
      return new Capture(Target.VIEWPORT, null, null);
    }

    public Observation<CapturedScreenshot> fastVisibleOnViewPort(ScreenshotQuality quality) {
      return context -> context.observation().capturePage().fastVisibleOnViewPort(quality);
    }

    public Observation<CapturedScreenshot> visibleOnViewPort(ScreenshotQuality quality) {
      return context -> context.observation().capturePage().visibleOnViewPort(quality);
    }

    public Capture fullPage() {
      return new Capture(Target.FULL_PAGE, null, null);
    }
  }
}
