package io.github.ygrip.testara.ui.interaction;

import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.ui.config.WebPageDataProperties;
import io.github.ygrip.testara.ui.driver.DriverSession;
import io.github.ygrip.testara.ui.executor.Actor;
import io.github.ygrip.testara.ui.model.DeviceType;
import io.github.ygrip.testara.ui.model.WebPageData;
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
    final var pageContext = Optional.ofNullable(page)
      .map(NamedPage.NamedPageContext::build)
      .map(NamedPage::getPage)
      .orElse(null);
    return new NavigationContext(Optional.ofNullable(pageContext)
      .map(PageContext::pageUrl)
      .filter(StringUtils::isNotBlank)
      .orElseGet(() -> Optional.ofNullable(page)
        .map(NamedPage.NamedPageContext::getName)
        .orElse(null))).build();
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
      case GO -> {
        final var converter = TestFramework.context()
          .converter();
        final var resolvedUrl = Optional.ofNullable(getUrl(context, url))
          .map(converter::convert)
          .map(String::valueOf)
          .filter(StringUtils::isNotBlank)
          .orElse(url);
        context.navigation()
          .to(resolvedUrl);
      }
      case REFRESH -> context.navigation()
        .refresh();
      case RELOAD -> context.navigation()
        .reload();
    }
  }

  private String getUrl(InteractionContext context, String url) {
    WebPageDataProperties properties = TestFramework.configuration()
      .get(WebPageDataProperties.class);
    DriverSession<?> session = context.session();
    final var platform = session.platform();
    final Map<String, WebPageData> pagesWithinPlatform = Optional.ofNullable(properties)
      .map(WebPageDataProperties::getPage)
      .map(pages -> pages.get(platform))
      .orElse(Map.of());
    final Map<String, WebPageData> fallback = Optional.ofNullable(properties)
      .map(WebPageDataProperties::getPage)
      .map(pages -> pages.get(DeviceType.DEFAULT))
      .orElse(Map.of());
    return Optional.ofNullable(pagesWithinPlatform.get(url))
      .map(WebPageData::getUrl)
      .filter(StringUtils::isNotBlank)
      .orElseGet(() -> fallback.getOrDefault(url, new WebPageData())
        .getUrl());
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
