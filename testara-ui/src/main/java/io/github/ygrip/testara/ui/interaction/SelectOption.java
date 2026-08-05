package io.github.ygrip.testara.ui.interaction;

import io.github.ygrip.testara.ui.executor.Actor;
import io.github.ygrip.testara.ui.model.Locator;
import io.github.ygrip.testara.ui.page.Element;

/**
 * Screenplay-style interaction: select an option from a dropdown.
 * <pre>
 *   SelectOption.from(Element.of("country")).byValue("US")
 *   SelectOption.from("my dropdown").byIndex(2)
 *   SelectOption.from(Element.of("size")).byVisibleText("Large")
 * </pre>
 *
 * @see Actor#attemptsTo(Interaction...)
 */
public final class SelectOption implements Interaction {
  private final Element element;
  private final Strategy strategy;
  private final String stringArg;
  private final int indexArg;

  private SelectOption(Element element, Strategy strategy, String stringArg, int indexArg) {
    this.element = element;
    this.strategy = strategy;
    this.stringArg = stringArg;
    this.indexArg = indexArg;
  }

  public static From from(String locator) {
    return new From(Element.of(locator).build());
  }

  public static From from(Locator locator) {
    return new From(Element.of(locator).build());
  }

  public static From from(Element.ElementContext locator) {
    return new From(locator.build());
  }

  @Override
  public void perform(InteractionContext context) {
    var select = context.interaction().selectOption(element);
    switch (strategy) {
      case VALUE -> select.byValue(stringArg);
      case INDEX -> select.byIndex(indexArg);
      case VISIBLE_TEXT -> select.byVisibleText(stringArg);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public Interaction root(Element root) {
    return new SelectOption(root.withChild(element).child(), strategy, stringArg, indexArg);
  }

  private enum Strategy {
    VALUE, INDEX, VISIBLE_TEXT
  }

  /**
   * Fluent step to specify which option to select.
   */
  public static final class From {
    private final Element target;

    From(Element target) {
      this.target = target;
    }

    public SelectOption byValue(String value) {
      return new SelectOption(target, Strategy.VALUE, value, -1);
    }

    public SelectOption byIndex(int index) {
      return new SelectOption(target, Strategy.INDEX, null, index);
    }

    public SelectOption byVisibleText(String visibleText) {
      return new SelectOption(target, Strategy.VISIBLE_TEXT, visibleText, -1);
    }
  }
}
