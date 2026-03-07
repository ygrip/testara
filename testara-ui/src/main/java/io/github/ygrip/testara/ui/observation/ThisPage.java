package io.github.ygrip.testara.ui.observation;

import io.github.ygrip.testara.ui.executor.Actor;
import io.github.ygrip.testara.ui.interaction.InteractionContext;
import io.github.ygrip.testara.ui.page.Element;

/**
 * Screenplay-style observation: get title or url of current page.
 *
 * @see Actor#observe(Observation)
 */
public final class ThisPage implements Observation<String> {
  private final Kind kind;

  private ThisPage(Kind kind) {
    this.kind = kind;
  }

  public static ThisPage url() {
    return new ThisPage(Kind.URL);
  }

  public static ThisPage title() {
    return new ThisPage(Kind.TITLE);
  }

  @Override
  public Observation<String> root(Element root) {
    return this;
  }

  @Override
  public String perform(InteractionContext context) {
    return switch (kind) {
      case URL -> context.observation()
        .getCurrentUrl();
      case TITLE -> context.observation()
        .getPageTitle();
    };
  }

  private enum Kind {
    TITLE, URL
  }
}
