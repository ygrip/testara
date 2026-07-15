package io.github.ygrip.testara.ui.observation;

import io.github.ygrip.testara.ui.executor.Actor;
import io.github.ygrip.testara.ui.interaction.InteractionContext;
import io.github.ygrip.testara.ui.model.CapturedCookie;
import io.github.ygrip.testara.ui.model.Locator;
import io.github.ygrip.testara.ui.page.Element;

/**
 * Screenplay-style observation: get text of element.
 *
 * @see Actor#observe(Observation)
 */
public final class BrowserCookie implements Observation<CapturedCookie> {
  private final String name;

  private BrowserCookie(String name) {
    this.name = name;
  }

  public static BrowserCookie named(String name) {
    return new BrowserCookie(name);
  }

  @Override
  public Observation<CapturedCookie> root(Element root) {
    return this;
  }

  @Override
  public CapturedCookie perform(InteractionContext context) {
    return context.observation()
      .getCookieNamed(name);
  }
}
