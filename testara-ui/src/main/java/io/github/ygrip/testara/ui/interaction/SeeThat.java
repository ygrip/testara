package io.github.ygrip.testara.ui.interaction;

import io.github.ygrip.testara.ui.executor.Actor;
import io.github.ygrip.testara.ui.model.Locator;
import io.github.ygrip.testara.ui.page.Element;
import io.github.ygrip.testara.ui.page.NamedPage;
import io.github.ygrip.testara.ui.page.PageContext;

/**
 * Screenplay-style interaction: assert a condition (e.g. element visible, has text).
 *
 * @see Actor#attemptsTo(Interaction...)
 */
public final class SeeThat implements Interaction {
  private final Kind kind;
  private final Element locator;
  private final String expectedText;
  private NamedPage pageContext;

  private SeeThat(Kind kind, Element locator, String expectedText) {
    this.kind = kind;
    this.locator = locator;
    this.expectedText = expectedText;
  }

  public static SeeThat visible(String locator) {
    return new SeeThat(
      Kind.VISIBLE,
      Element.of(locator)
        .build(),
      null
    );
  }

  public static SeeThat visible(Element.ElementContext locator) {
    return new SeeThat(Kind.VISIBLE, locator.build(), null);
  }

  public static SeeThat visible(Locator locator) {
    return new SeeThat(
      Kind.VISIBLE,
      Element.of(locator)
        .build(),
      null
    );
  }

  public static HasValue value(String expectedText) {
    return new HasValue(expectedText);
  }

  public static ContainsValue containsValue(String expectedText) {
    return new ContainsValue(expectedText);
  }

  public static HasText text(String expectedText) {
    return new HasText(expectedText);
  }

  public static ContainsText containsText(String expectedText) {
    return new ContainsText(expectedText);
  }

  public static HasAttribute attribute(String attributeName) {
    return new HasAttribute(attributeName);
  }

  public static HasClass hasClass(String className) {
    return new HasClass(className);
  }

  public static SeeThat present(String locator) {
    return new SeeThat(
      Kind.PRESENT,
      Element.of(locator)
        .build(),
      null
    );
  }

  public static SeeThat present(Element.ElementContext locator) {
    return new SeeThat(Kind.PRESENT, locator.build(), null);
  }

  public static SeeThat present(Locator locator) {
    return new SeeThat(
      Kind.PRESENT,
      Element.of(locator)
        .build(),
      null
    );
  }

  public static SeeThat hidden(String locator) {
    return new SeeThat(
      Kind.HIDDEN,
      Element.of(locator)
        .build(),
      null
    );
  }

  public static SeeThat hidden(Element.ElementContext locator) {
    return new SeeThat(Kind.HIDDEN, locator.build(), null);
  }

  public static SeeThat hidden(Locator locator) {
    return new SeeThat(
      Kind.HIDDEN,
      Element.of(locator)
        .build(),
      null
    );
  }

  public static SeeThat clickable(String locator) {
    return new SeeThat(
      Kind.CLICKABLE,
      Element.of(locator)
        .build(),
      null
    );
  }

  public static SeeThat clickable(Element.ElementContext locator) {
    return new SeeThat(Kind.CLICKABLE, locator.build(), null);
  }

  public static SeeThat clickable(Locator locator) {
    return new SeeThat(
      Kind.CLICKABLE,
      Element.of(locator)
        .build(),
      null
    );
  }

  public static SeeThat enabled(String locator) {
    return new SeeThat(
      Kind.ENABLED,
      Element.of(locator)
        .build(),
      null
    );
  }

  public static SeeThat enabled(Element.ElementContext locator) {
    return new SeeThat(Kind.ENABLED, locator.build(), null);
  }

  public static SeeThat enabled(Locator locator) {
    return new SeeThat(
      Kind.ENABLED,
      Element.of(locator)
        .build(),
      null
    );
  }

  public static SeeThat disabled(String locator) {
    return new SeeThat(
      Kind.DISABLED,
      Element.of(locator)
        .build(),
      null
    );
  }

  public static SeeThat disabled(Element.ElementContext locator) {
    return new SeeThat(Kind.DISABLED, locator.build(), null);
  }

  public static SeeThat disabled(Locator locator) {
    return new SeeThat(
      Kind.DISABLED,
      Element.of(locator)
        .build(),
      null
    );
  }

  public static SeeThat selected(String locator) {
    return new SeeThat(
      Kind.SELECTED,
      Element.of(locator)
        .build(),
      null
    );
  }

  public static SeeThat selected(Element.ElementContext locator) {
    return new SeeThat(Kind.SELECTED, locator.build(), null);
  }

  public static SeeThat selected(Locator locator) {
    return new SeeThat(
      Kind.SELECTED,
      Element.of(locator)
        .build(),
      null
    );
  }

  public static SeeThat page(NamedPage.NamedPageContext page) {
    return new SeeThat(Kind.PAGE, null, null).currentPage(page.build());
  }

  public static SeeThat page(String pageName) {
    return new SeeThat(Kind.PAGE, null, null).currentPage(NamedPage.of(pageName)
      .build());
  }

  public static SeeThat page(Class<? extends PageContext<?>> pageType) {
    return new SeeThat(Kind.PAGE, null, null).currentPage(NamedPage.of(pageType)
      .build());
  }

  SeeThat currentPage(NamedPage pageContext) {
    this.pageContext = pageContext;
    return this;
  }

  @Override
  public void perform(InteractionContext context) {
    switch (kind) {
      case VISIBLE -> context.assertion()
        .seeThatVisible(locator);
      case HIDDEN -> context.assertion()
        .seeThatHidden(locator);
      case ENABLED -> {
        boolean enabled = context.assertion()
          .isEnabled(locator);
        if(!enabled){
          throw new AssertionError("Element : "+ locator.getLocator()+" is not enabled");
        }
      }
      case DISABLED -> {
        boolean enabled = context.assertion()
          .isEnabled(locator);
        if(enabled){
          throw new AssertionError("Element : "+ locator.getLocator()+" is not disabled");
        }
      }
      case CLICKABLE -> {
        context.waits().untilClickable(locator);
      }
      case SELECTED -> {
        context.waits().untilSelected(locator);
      }
      case TEXT -> context.assertion()
        .seeThatText(locator, expectedText);
      case VALUE -> context.assertion()
        .seeThatValue(locator, expectedText);
      case CONTAINS_TEXT -> context.assertion()
        .seeThatContainsText(locator, expectedText);
      case HAS_CLASS -> context.assertion()
        .hasClass(locator, expectedText);
      case PRESENT -> context.assertion()
        .seeThatPresent(locator);
      case ATTRIBUTE -> context.assertion()
        .seeThatAttribute(locator, expectedText);
      case PAGE -> context.assertion()
        .isOn(pageContext);
    }
  }

  @Override
  public Interaction root(Element root) {
    return new SeeThat(kind, root.withChild(locator).child(), expectedText);
  }

  private enum Kind {VISIBLE, HIDDEN, SELECTED, ENABLED, DISABLED, CLICKABLE, TEXT, PRESENT, ATTRIBUTE, CONTAINS_TEXT, PAGE, VALUE, CONTAINS_VALUE, HAS_CLASS}


  public static class HasText {
    private final String text;

    public HasText(String text) {
      this.text = text;
    }

    public SeeThat on(String locator) {
      return new SeeThat(
        Kind.TEXT,
        Element.of(locator)
          .build(),
        text
      );
    }

    public SeeThat on(Locator locator) {
      return new SeeThat(
        Kind.TEXT,
        Element.of(locator)
          .build(),
        text
      );
    }

    public SeeThat on(Element.ElementContext locator) {
      return new SeeThat(Kind.TEXT, locator.build(), text);
    }
  }


  public static class ContainsText {
    private final String text;

    public ContainsText(String text) {
      this.text = text;
    }

    public SeeThat on(String locator) {
      return new SeeThat(
        Kind.CONTAINS_TEXT,
        Element.of(locator)
          .build(),
        text
      );
    }

    public SeeThat on(Locator locator) {
      return new SeeThat(
        Kind.CONTAINS_TEXT,
        Element.of(locator)
          .build(),
        text
      );
    }

    public SeeThat on(Element.ElementContext locator) {
      return new SeeThat(Kind.CONTAINS_TEXT, locator.build(), text);
    }
  }


  public static class HasAttribute {
    private final String attributeName;

    public HasAttribute(String attributeName) {
      this.attributeName = attributeName;
    }

    public SeeThat on(String locator) {
      return new SeeThat(
        Kind.ATTRIBUTE,
        Element.of(locator)
          .build(),
        attributeName
      );
    }

    public SeeThat on(Locator locator) {
      return new SeeThat(
        Kind.ATTRIBUTE,
        Element.of(locator)
          .build(),
        attributeName
      );
    }

    public SeeThat on(Element.ElementContext locator) {
      return new SeeThat(Kind.ATTRIBUTE, locator.build(), attributeName);
    }
  }


  public static class HasValue {
    private final String value;

    public HasValue(String value) {
      this.value = value;
    }

    public SeeThat on(String locator) {
      return new SeeThat(
        Kind.VALUE,
        Element.of(locator)
          .build(),
        value
      );
    }

    public SeeThat on(Locator locator) {
      return new SeeThat(
        Kind.VALUE,
        Element.of(locator)
          .build(),
        value
      );
    }

    public SeeThat on(Element.ElementContext locator) {
      return new SeeThat(Kind.VALUE, locator.build(), value);
    }
  }


  public static class ContainsValue {
    private final String value;

    public ContainsValue(String value) {
      this.value = value;
    }

    public SeeThat on(String locator) {
      return new SeeThat(
        Kind.CONTAINS_VALUE,
        Element.of(locator)
          .build(),
        value
      );
    }

    public SeeThat on(Locator locator) {
      return new SeeThat(
        Kind.CONTAINS_VALUE,
        Element.of(locator)
          .build(),
        value
      );
    }

    public SeeThat on(Element.ElementContext locator) {
      return new SeeThat(Kind.CONTAINS_VALUE, locator.build(), value);
    }
  }

  public static class HasClass {
    private final String value;

    public HasClass(String value) {
      this.value = value;
    }

    public SeeThat on(String locator) {
      return new SeeThat(
        Kind.HAS_CLASS,
        Element.of(locator)
          .build(),
        value
      );
    }

    public SeeThat on(Locator locator) {
      return new SeeThat(
        Kind.HAS_CLASS,
        Element.of(locator)
          .build(),
        value
      );
    }

    public SeeThat on(Element.ElementContext locator) {
      return new SeeThat(Kind.HAS_CLASS, locator.build(), value);
    }
  }
}
