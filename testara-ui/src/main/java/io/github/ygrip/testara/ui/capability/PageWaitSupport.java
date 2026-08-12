package io.github.ygrip.testara.ui.capability;

import java.time.Duration;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

import io.github.ygrip.testara.ui.error.WaitTimeoutException;
import io.github.ygrip.testara.ui.page.NamedPage;
import io.github.ygrip.testara.ui.page.PageContext;

/** Shared page-wait semantics for all UI engines. */
public final class PageWaitSupport {

  private PageWaitSupport() {
  }

  /**
   * Resolve the requested page, verify that it becomes current within the timeout, and activate it.
   *
   * @throws WaitTimeoutException when the page cannot be resolved or does not become current
   */
  public static void requireLoaded(NamedPage namedPage, Duration timeout) {
    if (namedPage == null) {
      throw new IllegalArgumentException("namedPage must not be null");
    }
    if (timeout == null || timeout.isNegative()) {
      throw new IllegalArgumentException("timeout must not be null or negative");
    }

    PageContext<?> page = namedPage.getPage();
    String pageName = pageName(namedPage, page);
    if (page == null) {
      throw new WaitTimeoutException(
        "Unable to resolve page '%s' while waiting up to %s".formatted(pageName, timeout)
      );
    }

    if (!page.isCurrentPage(timeout)) {
      throw new WaitTimeoutException(
        "Timed out after %s waiting for page '%s' to become current".formatted(timeout, pageName)
      );
    }

    namedPage.getFinder().setCurrentPage(page);
  }

  private static String pageName(NamedPage namedPage, PageContext<?> page) {
    return Optional.ofNullable(namedPage.getName())
      .filter(StringUtils::isNotBlank)
      .orElseGet(() -> Optional.ofNullable(page)
        .map(Object::getClass)
        .map(Class::getSimpleName)
        .filter(StringUtils::isNotBlank)
        .orElse("requested page"));
  }
}
