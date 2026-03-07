package io.github.ygrip.testara.ui.capability;

/**
 * Fluent navigation (go to URL, back, forward, refresh, tab management). Screenplay-style.
 */
public interface NavigationCapability {

  /** Navigate to URL. Returns this for chaining. */
  NavigationCapability to(String url);

  /** Browser back. */
  NavigationCapability back();

  /** Browser forward. */
  NavigationCapability forward();

  /** Refresh current page. */
  NavigationCapability refresh();

  /** Refresh current page. */
  NavigationCapability reload();

  /** Get current URL. */
  String getCurrentUrl();

  /** Get page title. */
  String getTitle();

  /** Open a new blank tab and switch to it. */
  NavigationCapability openNewTab();

  /** Open a new tab, navigate to the given URL, and switch to it. */
  NavigationCapability openNewTab(String url);

  /** Close the current tab and switch to the previous one. */
  NavigationCapability closeTab();

  /** Switch to the tab at the given zero-based index. */
  NavigationCapability switchToTab(int index);

  /** Return the number of currently open tabs. */
  int getTabCount();
}
