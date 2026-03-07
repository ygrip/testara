package io.github.ygrip.testara.ui.appium.capability;

import java.util.ArrayList;
import java.util.List;

import org.openqa.selenium.WindowType;

import io.github.ygrip.testara.ui.capability.NavigationCapability;

import io.appium.java_client.AppiumDriver;

/**
 * Appium implementation of {@link NavigationCapability}. Fluent, chainable.
 */
public final class AppiumNavigationCapability implements NavigationCapability {
  private final AppiumDriver driver;

  public AppiumNavigationCapability(AppiumDriver driver) {
    this.driver = driver;
  }

  @Override
  public NavigationCapability to(String url) {
    driver.get(url);
    return this;
  }

  @Override
  public NavigationCapability back() {
    driver.navigate()
      .back();
    return this;
  }

  @Override
  public NavigationCapability forward() {
    driver.navigate()
      .forward();
    return this;
  }

  @Override
  public NavigationCapability refresh() {
    driver.navigate()
      .refresh();
    return this;
  }

  @Override
  public NavigationCapability reload() {
    driver.manage()
      .deleteAllCookies();
    refresh();
    return this;
  }

  @Override
  public String getCurrentUrl() {
    return driver.getCurrentUrl();
  }

  @Override
  public String getTitle() {
    return driver.getTitle();
  }

  @Override
  public NavigationCapability openNewTab() {
    driver.switchTo()
      .newWindow(WindowType.TAB);
    return this;
  }

  @Override
  public NavigationCapability openNewTab(String url) {
    driver.switchTo()
      .newWindow(WindowType.TAB);
    driver.get(url);
    return this;
  }

  @Override
  public NavigationCapability closeTab() {
    String current = driver.getWindowHandle();
    List<String> handles = new ArrayList<>(driver.getWindowHandles());
    driver.close();
    handles.remove(current);
    if (!handles.isEmpty()) {
      driver.switchTo()
        .window(handles.getLast());
    }
    return this;
  }

  @Override
  public NavigationCapability switchToTab(int index) {
    List<String> handles = new ArrayList<>(driver.getWindowHandles());
    if (index >= 0 && index < handles.size()) {
      driver.switchTo()
        .window(handles.get(index));
    }
    return this;
  }

  @Override
  public int getTabCount() {
    return driver.getWindowHandles()
      .size();
  }
}
