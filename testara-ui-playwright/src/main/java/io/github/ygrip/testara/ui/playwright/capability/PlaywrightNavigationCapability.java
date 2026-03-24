package io.github.ygrip.testara.ui.playwright.capability;

import java.util.List;

import org.apache.commons.lang3.StringUtils;

import io.github.ygrip.testara.ui.capability.NavigationCapability;
import io.github.ygrip.testara.ui.playwright.driver.PlaywrightSession;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;

public final class PlaywrightNavigationCapability implements NavigationCapability {
  private final PlaywrightSession session;

  public PlaywrightNavigationCapability(PlaywrightSession session) {
    this.session = session;
  }

  @Override
  public NavigationCapability to(String url) {
    session.runOnApiThread(() -> {
      Page p = session.pageForApi();
      p.navigate(url);
      p.waitForLoadState(LoadState.NETWORKIDLE);
      return null;
    });
    return this;
  }

  @Override
  public NavigationCapability back() {
    session.runOnApiThread(() -> {
      Page p = session.pageForApi();
      p.goBack();
      p.waitForLoadState(LoadState.NETWORKIDLE);
      return null;
    });
    return this;
  }

  @Override
  public NavigationCapability forward() {
    session.runOnApiThread(() -> {
      Page p = session.pageForApi();
      p.goForward();
      p.waitForLoadState(LoadState.NETWORKIDLE);
      return null;
    });
    return this;
  }

  @Override
  public NavigationCapability refresh() {
    session.runOnApiThread(() -> {
      session.pageForApi().reload(new Page.ReloadOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
      return null;
    });
    return this;
  }

  @Override
  public NavigationCapability reload() {
    session.runOnApiThread(() -> {
      session.contextForApi().clearCookies();
      session.pageForApi().reload(new Page.ReloadOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
      return null;
    });
    return this;
  }

  @Override
  public String getCurrentUrl() {
    return session.runOnApiThread(() -> session.pageForApi().url());
  }

  @Override
  public String getTitle() {
    return session.runOnApiThread(() -> session.pageForApi().title());
  }

  @Override
  public NavigationCapability openNewTab() {
    session.runOnApiThread(() -> {
      BrowserContext ctx = session.contextForApi();
      Page newPage = ctx.newPage();
      session.setActivePage(newPage);
      return null;
    });
    return this;
  }

  @Override
  public NavigationCapability openNewTab(String url) {
    session.runOnApiThread(() -> {
      BrowserContext ctx = session.contextForApi();
      Page newPage = ctx.newPage();
      if (StringUtils.isNotBlank(url)) {
        newPage.navigate(url);
      }
      session.setActivePage(newPage);
      return null;
    });
    return this;
  }

  @Override
  public NavigationCapability closeTab() {
    session.runOnApiThread(() -> {
      BrowserContext ctx = session.contextForApi();
      Page current = session.pageForApi();
      current.close();
      List<Page> remaining = ctx.pages();
      if (!remaining.isEmpty()) {
        session.setActivePage(remaining.getLast());
      }
      return null;
    });
    return this;
  }

  @Override
  public NavigationCapability switchToTab(int index) {
    session.runOnApiThread(() -> {
      List<Page> pages = session.contextForApi().pages();
      if (index >= 0 && index < pages.size()) {
        Page target = pages.get(index);
        target.bringToFront();
        session.setActivePage(target);
      }
      return null;
    });
    return this;
  }

  @Override
  public int getTabCount() {
    return session.runOnApiThread(() -> session.contextForApi().pages().size());
  }
}
