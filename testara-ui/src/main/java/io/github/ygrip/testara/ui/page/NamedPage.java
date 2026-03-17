package io.github.ygrip.testara.ui.page;

import java.util.Optional;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import io.github.ygrip.testara.ui.driver.DriverSessionManager;

import lombok.Getter;

public final class NamedPage {
  @Getter
  private final String name;
  @Getter
  private final PageFinder<?, ?, ?> finder;
  private final Class<? extends PageContext<?>> pageType;

  NamedPage(PageFinder<?, ?, ?> finder, Class<? extends PageContext<?>> pageType, String name) {
    this.finder = Optional.ofNullable(finder)
      .orElseGet(() -> DriverSessionManager.inThisTestThread()
        .getCurrentDriver()
        .finder());
    this.pageType = pageType;
    this.name = name;
  }

  public static NamedPageContext of(String name) {
    return new NamedPageContext(name);
  }

  public static NamedPageContext of(Class<? extends PageContext<?>> pageType) {
    return new NamedPageContext(pageType);
  }

  public PageContext<?> getPage() {
    if (ObjectUtils.isNotEmpty(pageType)) {
      try {
        return finder.getPage(name);
      } catch (Exception err) {
        return null;
      }
    }
    return (PageContext<?>) Optional.ofNullable(name)
      .filter(StringUtils::isNotBlank)
      .map(page -> {
        try {
          return finder.getPage(name);
        } catch (Exception err) {
          return null;
        }
      })
      .orElse(null);
  }

  public static class NamedPageContext {
    @Getter
    private final String name;
    private PageFinder<?, ?, ?> finder;
    private Class<? extends PageContext<?>> pageType;

    public NamedPageContext(String name) {
      this.name = name;
    }

    public NamedPageContext(Class<? extends PageContext<?>> pageType) {
      this.pageType = pageType;
      this.name = null;
    }

    public NamedPageContext by(PageFinder<?, ?, ?> finder) {
      this.finder = finder;
      return this;
    }

    public NamedPage build() {
      return new NamedPage(finder, pageType, name);
    }
  }
}
