package io.github.ygrip.testara.ui.steps;

import java.util.Optional;

import io.github.ygrip.testara.ui.driver.DriverSessionManager;
import io.github.ygrip.testara.ui.page.Element;
import io.github.ygrip.testara.ui.page.PageContext;
import io.github.ygrip.testara.ui.page.PageFinder;
import io.github.ygrip.testara.ui.page.ParameterizedElementMatch;

public final class ElementPhraseResolver {

  private ElementPhraseResolver() {
  }

  public static Optional<Element.ElementContext> resolve(String phrase) {
    final var session = DriverSessionManager.inThisTestThread().getCurrentDriver();
    if (session == null) {
      return Optional.empty();
    }
    final var finder = session.finder();
    final var page = finder.getCurrentPage();
    if (page == null) {
      return Optional.empty();
    }
    return resolve(phrase, page);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public static Optional<Element.ElementContext> resolve(String phrase, PageContext<?> page) {
    if (phrase == null || phrase.isBlank() || page == null) {
      return Optional.empty();
    }
    final var session = DriverSessionManager.inThisTestThread().getCurrentDriver();
    if (session == null) {
      return Optional.empty();
    }
    // Raw type used to bypass generic wildcard capture — safe because P extends PageContext<?>
    PageFinder finder = session.finder();
    @SuppressWarnings("unchecked")
    Optional<ParameterizedElementMatch> result = (Optional<ParameterizedElementMatch>) finder.resolveParameterizedElement(page, phrase);
    return result.map(match -> Element.named(match.elementName())
      .with(match.parameters())
      .on(page));
  }
}
