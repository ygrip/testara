package io.github.ygrip.testara.ui.vibium.capability;

import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.vibium.Page;
import com.vibium.errors.VibiumException;

import io.github.ygrip.testara.ui.capability.NavigationCapability;
import io.github.ygrip.testara.ui.vibium.driver.VibiumSession;
import io.github.ygrip.testara.ui.vibium.error.VibiumOperationException;

/**
 * Vibium's {@link NavigationCapability}. No element resolution needed (mirrors {@code
 * PlaywrightNavigationCapability}), so this does not extend {@link VibiumElementResolver}. Maps
 * directly onto the real {@code com.vibium.Page}/{@code com.vibium.Browser} methods that {@link
 * io.github.ygrip.testara.ui.vibium.page.VibiumPage} and {@link VibiumSession} already use for
 * their own navigation/tab handling (delegated to rather than duplicated).
 */
public final class VibiumNavigationCapability implements NavigationCapability {
  private final VibiumSession session;

  public VibiumNavigationCapability(VibiumSession session) {
    this.session = session;
  }

  @Override
  public NavigationCapability to(String url) {
    try {
      session.pageForApi()
        .go(url);
    } catch (VibiumException e) {
      throw wrap("to", e);
    }
    return this;
  }

  @Override
  public NavigationCapability back() {
    try {
      session.pageForApi()
        .back();
    } catch (VibiumException e) {
      throw wrap("back", e);
    }
    return this;
  }

  @Override
  public NavigationCapability forward() {
    try {
      session.pageForApi()
        .forward();
    } catch (VibiumException e) {
      throw wrap("forward", e);
    }
    return this;
  }

  @Override
  public NavigationCapability refresh() {
    try {
      session.pageForApi()
        .reload();
    } catch (VibiumException e) {
      throw wrap("refresh", e);
    }
    return this;
  }

  @Override
  public NavigationCapability reload() {
    try {
      session.pageForApi()
        .reload();
    } catch (VibiumException e) {
      throw wrap("reload", e);
    }
    return this;
  }

  @Override
  public String getCurrentUrl() {
    try {
      return session.pageForApi()
        .url();
    } catch (VibiumException e) {
      throw wrap("getCurrentUrl", e);
    }
  }

  @Override
  public String getTitle() {
    try {
      return session.pageForApi()
        .title();
    } catch (VibiumException e) {
      throw wrap("getTitle", e);
    }
  }

  @Override
  public NavigationCapability openNewTab() {
    try {
      session.openNewPage();
    } catch (VibiumException e) {
      throw wrap("openNewTab", e);
    }
    return this;
  }

  @Override
  public NavigationCapability openNewTab(String url) {
    try {
      Page newPage = session.openNewPage();
      if (StringUtils.isNotBlank(url)) {
        newPage.go(url);
      }
    } catch (VibiumException e) {
      throw wrap("openNewTab", e);
    }
    return this;
  }

  @Override
  public NavigationCapability closeTab() {
    try {
      Page current = session.pageForApi();
      current.close();
      List<Page> remaining = session.instance()
        .pages();
      if (!remaining.isEmpty()) {
        session.switchToPage(remaining.get(remaining.size() - 1));
      }
    } catch (VibiumException e) {
      throw wrap("closeTab", e);
    }
    return this;
  }

  @Override
  public NavigationCapability switchToTab(int index) {
    List<Page> pages;
    try {
      pages = session.instance()
        .pages();
    } catch (VibiumException e) {
      throw wrap("switchToTab", e);
    }
    if (index < 0 || index >= pages.size()) {
      throw new VibiumOperationException(
        "Vibium operation 'switchToTab' failed on '" + safePageUrl() + "': no tab at index " + index
          + " (open tab count=" + pages.size() + ")"
      );
    }
    try {
      session.switchToPage(pages.get(index));
    } catch (VibiumException e) {
      throw wrap("switchToTab", e);
    }
    return this;
  }

  @Override
  public int getTabCount() {
    try {
      return session.instance()
        .pages()
        .size();
    } catch (VibiumException e) {
      throw wrap("getTabCount", e);
    }
  }

  private VibiumOperationException wrap(String operation, VibiumException cause) {
    return VibiumOperationException.of(operation, "n/a", safePageUrl(), 0L, cause);
  }

  private String safePageUrl() {
    try {
      return session.pageForApi()
        .url();
    } catch (Exception e) {
      return "<unavailable>";
    }
  }
}
