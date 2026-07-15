package io.github.ygrip.testara.ui.observation;

import java.util.List;

import io.github.ygrip.testara.ui.executor.Actor;
import io.github.ygrip.testara.ui.interaction.InteractionContext;
import io.github.ygrip.testara.ui.model.CapturedCookie;
import io.github.ygrip.testara.ui.page.Element;

/**
 * Screenplay-style observation: get text of element.
 *
 * @see Actor#observe(Observation)
 */
public final class BrowserCookies implements Observation<List<CapturedCookie>> {

  @Override
  public Observation<List<CapturedCookie>> root(Element root) {
    return this;
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<CapturedCookie> perform(InteractionContext context) {
    return context.observation()
      .getCookies();
  }
}
