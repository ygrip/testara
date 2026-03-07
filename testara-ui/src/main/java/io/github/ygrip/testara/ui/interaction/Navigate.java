package io.github.ygrip.testara.ui.interaction;

import java.util.Optional;

import io.github.ygrip.testara.ui.executor.Actor;
import io.github.ygrip.testara.ui.page.Element;
import io.github.ygrip.testara.ui.page.NamedPage;
import io.github.ygrip.testara.ui.page.PageContext;

/**
 * Screenplay-style interaction: navigate to a URL.
 *
 * @see Actor#attemptsTo(Interaction...)
 */
public final class Navigate implements Interaction {
  private final Kind kind;
  private final String url;


  private Navigate(Kind kind, String url) {
    this.url = url;
    this.kind = kind;
  }

  public static Navigate to(String url) {
    return new NavigationContext(url).build();
  }

  public static Navigate to(NamedPage.NamedPageContext page) {
    return new NavigationContext(Optional.ofNullable(page)
      .map(NamedPage.NamedPageContext::build)
      .map(NamedPage::getPage)
      .map(PageContext::pageUrl)
      .orElse(null)).build();
  }

  public static Navigate refresh() {
    return new Navigate(Kind.REFRESH, null);
  }

  public static Navigate reload() {
    return new Navigate(Kind.RELOAD, null);
  }

  @Override
  public void perform(InteractionContext context) {
    switch (kind) {
      case GO -> context.navigation()
        .to(url);
      case REFRESH -> context.navigation()
        .refresh();
      case RELOAD -> context.navigation()
        .reload();
    }
  }

  @Override
  public Interaction root(Element element) {
    return this;
  }

  private enum Kind {
    REFRESH, RELOAD, GO
  }


  public static class NavigationContext {
    private final String url;

    NavigationContext(String url) {
      this.url = url;
    }

    NavigationContext() {
      this.url = null;
    }

    protected Navigate build() {
      return new Navigate(Kind.GO, this.url);
    }
  }
}
