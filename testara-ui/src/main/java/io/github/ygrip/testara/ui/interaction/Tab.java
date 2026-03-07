package io.github.ygrip.testara.ui.interaction;

import io.github.ygrip.testara.ui.executor.Actor;
import io.github.ygrip.testara.ui.page.Element;

/**
 * Screenplay-style interaction: manage browser tabs.
 * <pre>
 *   Tab.openNew()
 *   Tab.openNew("https://example.com")
 *   Tab.close()
 *   Tab.switchTo(0)
 * </pre>
 *
 * @see Actor#attemptsTo(Interaction...)
 */
public final class Tab implements Interaction {
  private final Action action;
  private final String url;
  private final int index;

  private Tab(Action action, String url, int index) {
    this.action = action;
    this.url = url;
    this.index = index;
  }

  public static Tab openNew() {
    return new Tab(Action.OPEN_BLANK, null, -1);
  }

  public static Tab openNew(String url) {
    return new Tab(Action.OPEN_URL, url, -1);
  }

  public static Tab close() {
    return new Tab(Action.CLOSE, null, -1);
  }

  public static Tab switchTo(int index) {
    return new Tab(Action.SWITCH, null, index);
  }

  @Override
  public void perform(InteractionContext context) {
    switch (action) {
      case OPEN_BLANK -> context.navigation().openNewTab();
      case OPEN_URL -> context.navigation().openNewTab(url);
      case CLOSE -> context.navigation().closeTab();
      case SWITCH -> context.navigation().switchToTab(index);
    }
  }

  @Override
  public Interaction root(Element root) {
    return this;
  }

  private enum Action {
    OPEN_BLANK, OPEN_URL, CLOSE, SWITCH
  }
}
