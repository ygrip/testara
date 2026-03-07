package io.github.ygrip.testara.ui.playwright.capability;

import java.util.List;

import org.apache.commons.lang3.StringUtils;

import io.github.ygrip.testara.ui.capability.NavigationCapability;
import io.github.ygrip.testara.ui.playwright.driver.PlaywrightSession;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitUntilState;

public final class PlaywrightNavigationCapability implements NavigationCapability {
  private final PlaywrightSession session;

  public PlaywrightNavigationCapability(PlaywrightSession session) {
    this.session = session;
  }

  private Page page() {
    return session.page();
  }

  @Override
  public NavigationCapability to(String url) {
    page().navigate(url);
    return this;
  }

  @Override
  public NavigationCapability back() {
    page().goBack();
    return this;
  }

  @Override
  public NavigationCapability forward() {
    page().goForward();
    return this;
  }

  @Override
  public NavigationCapability refresh() {
    page().reload(new Page.ReloadOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
    return this;
  }

  @Override
  public NavigationCapability reload() {
    session.context()
      .clearCookies();
    refresh();
    return this;
  }

  @Override
  public String getCurrentUrl() {
    return page().url();
  }

  @Override
  public String getTitle() {
    return page().title();
  }

  @Override
  public NavigationCapability openNewTab() {
    BrowserContext ctx = session.context();
    Page newPage = ctx.newPage();
    session.setActivePage(newPage);
    return this;
  }

  @Override
  public NavigationCapability openNewTab(String url) {
    BrowserContext ctx = session.context();
    Page newPage = ctx.newPage();
    if (StringUtils.isNotBlank(url)) {
      newPage.navigate(url);
    }
    session.setActivePage(newPage);
    return this;
  }

  @Override
  public NavigationCapability closeTab() {
    BrowserContext ctx = session.context();
    Page current = page();
    List<Page> pages = ctx.pages();
    current.close();
    List<Page> remaining = ctx.pages();
    if (!remaining.isEmpty()) {
      session.setActivePage(remaining.getLast());
    }
    return this;
  }

  @Override
  public NavigationCapability switchToTab(int index) {
    List<Page> pages = session.context()
      .pages();
    if (index >= 0 && index < pages.size()) {
      Page target = pages.get(index);
      target.bringToFront();
      session.setActivePage(target);
    }
    return this;
  }

  @Override
  public int getTabCount() {
    return session.context()
      .pages()
      .size();
  }
}
