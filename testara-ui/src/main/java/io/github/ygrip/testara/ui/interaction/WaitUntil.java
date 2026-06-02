package io.github.ygrip.testara.ui.interaction;

import java.time.Duration;
import java.util.Optional;

import io.github.ygrip.testara.ui.executor.Actor;
import io.github.ygrip.testara.ui.model.Locator;
import io.github.ygrip.testara.ui.page.Element;
import io.github.ygrip.testara.ui.page.NamedPage;
import io.github.ygrip.testara.ui.page.PageContext;

/**
 * Screenplay-style interaction: wait until a condition (e.g. element visible, clickable).
 *
 * @see Actor#attemptsTo(Interaction...)
 */
public final class WaitUntil implements Interaction {
  private final Kind kind;
  private final Element locator;
  private final Duration timeout;
  private NamedPage pageContext;
  private String url;

  private WaitUntil(Kind kind, Element locator, Duration timeout) {
    this.kind = kind;
    this.locator = locator;
    this.timeout = Optional.ofNullable(timeout).orElse(Duration.ofSeconds(10));
  }

  public static WaitUntil visible(String locator) {
    return new WaitUntil(
      Kind.VISIBLE,
      Element.of(locator)
        .build(),
      null
    );
  }

  public static WaitUntil visible(Element.ElementContext locator) {
    return new WaitUntil(Kind.VISIBLE, locator.build(), null);
  }

  public static WaitUntil visible(Locator locator) {
    return new WaitUntil(
      Kind.VISIBLE,
      Element.of(locator)
        .build(),
      null
    );
  }

  public static WaitUntil hidden(String locator) {
    return new WaitUntil(
      Kind.INVISIBLE,
      Element.of(locator)
        .build(),
      null
    );
  }

  public static WaitUntil hidden(Element.ElementContext locator) {
    return new WaitUntil(Kind.INVISIBLE, locator.build(), null);
  }

  public static WaitUntil hidden(Locator locator) {
    return new WaitUntil(
      Kind.INVISIBLE,
      Element.of(locator)
        .build(),
      null
    );
  }

  public static WaitUntil clickable(String locator) {
    return new WaitUntil(
      Kind.CLICKABLE,
      Element.of(locator)
        .build(),
      null
    );
  }

  public static WaitUntil clickable(Locator locator) {
    return new WaitUntil(
      Kind.CLICKABLE,
      Element.of(locator)
        .build(),
      null
    );
  }

  public static WaitUntil clickable(Element.ElementContext locator) {
    return new WaitUntil(Kind.CLICKABLE, locator.build(), null);
  }

  public static WaitUntil enabled(String locator) {
    return new WaitUntil(
      Kind.ENABLED,
      Element.of(locator)
        .build(),
      null
    );
  }

  public static WaitUntil enabled(Locator locator) {
    return new WaitUntil(
      Kind.ENABLED,
      Element.of(locator)
        .build(),
      null
    );
  }

  public static WaitUntil enabled(Element.ElementContext locator) {
    return new WaitUntil(Kind.ENABLED, locator.build(), null);
  }

  public static WaitUntil disabled(String locator) {
    return new WaitUntil(
      Kind.DISABLED,
      Element.of(locator)
        .build(),
      null
    );
  }

  public static WaitUntil disabled(Locator locator) {
    return new WaitUntil(
      Kind.DISABLED,
      Element.of(locator)
        .build(),
      null
    );
  }

  public static WaitUntil disabled(Element.ElementContext locator) {
    return new WaitUntil(Kind.DISABLED, locator.build(), null);
  }

  public static WaitUntil present(String locator) {
    return new WaitUntil(
      Kind.PRESENT,
      Element.of(locator)
        .build(),
      null
    );
  }

  public static WaitUntil present(Locator locator) {
    return new WaitUntil(
      Kind.PRESENT,
      Element.of(locator)
        .build(),
      null
    );
  }

  public static WaitUntil present(Element.ElementContext locator) {
    return new WaitUntil(Kind.PRESENT, locator.build(), null);
  }

  public static WaitUntil selected(String locator) {
    return new WaitUntil(
      Kind.SELECTED,
      Element.of(locator)
        .build(),
      null
    );
  }

  public static WaitUntil selected(Locator locator) {
    return new WaitUntil(
      Kind.SELECTED,
      Element.of(locator)
        .build(),
      null
    );
  }

  public static WaitUntil selected(Element.ElementContext locator) {
    return new WaitUntil(Kind.SELECTED, locator.build(), null);
  }

  public static WaitUntil duration(Duration duration) {
    return new WaitUntil(Kind.DURATION, null, duration);
  }

  public static WaitPage page(NamedPage.NamedPageContext pageContext) {
    return new WaitPage(pageContext.build());
  }

  public static WaitPage page(String pageName) {
    return new WaitPage(NamedPage.of(pageName)
      .build());
  }

  public static WaitPage page(Class<? extends PageContext<?>> pageType) {
    return new WaitPage(NamedPage.of(pageType)
      .build());
  }

  public static WaitUntil url(String url) {
    return new WaitUntil(Kind.URL, null, null).currentUrl(url);
  }

  WaitUntil currentPage(NamedPage pageContext) {
    this.pageContext = pageContext;
    return this;
  }

  WaitUntil currentUrl(String url) {
    this.url = url;
    return this;
  }

  public WaitUntil withTimeout(Duration duration) {
    return new WaitUntil(kind, locator, duration).currentPage(pageContext);
  }

  @Override
  public void perform(InteractionContext context) {
    if (timeout != null) {
      context.waits()
        .withTimeout(timeout);
    }
    switch (kind) {
      case VISIBLE -> context.waits()
        .untilVisible(locator);
      case INVISIBLE -> context.waits()
        .untilInvisible(locator);
      case CLICKABLE -> context.waits()
        .untilClickable(locator);
      case PRESENT -> context.waits()
        .untilPresent(locator);
      case ENABLED -> context.waits()
        .untilEnabled(locator);
      case DISABLED -> context.waits()
        .untilDisabled(locator);
      case SELECTED -> context.waits()
        .untilSelected(locator);
      case DURATION -> context.waits()
        .forDuration(timeout);
      case PAGE -> context.waits()
        .untilPageLoaded(pageContext)
        .forDuration(timeout);
      case URL -> context.waits()
        .untilUrlContains(url)
        .forDuration(timeout);
    }
  }

  @Override
  public Interaction root(Element root) {
    return new WaitUntil(kind,
      root.withChild(locator)
        .child(),
      timeout
    );
  }

  private enum Kind {VISIBLE, CLICKABLE, PRESENT, ENABLED, DISABLED, SELECTED, INVISIBLE, DURATION, PAGE, URL}


  public static class WaitPage {
    private final NamedPage page;

    public WaitPage(NamedPage page) {
      this.page = page;
    }

    public WaitUntil loaded() {
      return new WaitUntil(Kind.PAGE, null, null).currentPage(this.page);
    }
  }
}
