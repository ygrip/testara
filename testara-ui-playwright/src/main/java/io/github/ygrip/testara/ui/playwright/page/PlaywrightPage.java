package io.github.ygrip.testara.ui.playwright.page;

import java.util.List;

import io.github.ygrip.testara.ui.driver.DriverSession;
import io.github.ygrip.testara.ui.driver.DriverSessionManager;
import io.github.ygrip.testara.ui.page.PageContext;
import io.github.ygrip.testara.ui.playwright.driver.PlaywrightSession;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;

import lombok.extern.log4j.Log4j2;

@Log4j2
public abstract class PlaywrightPage extends PageContext<PlaywrightSession> {

  /**
   * Returns the live {@link Page} after marshalling to the Playwright API thread. Do not retain or
   * use this reference from another thread; prefer {@link PageContext} methods and capabilities.
   */
  public Page page() {
    return pw().runOnApiThread(() -> pw().pageForApi());
  }

  public BrowserContext context() {
    return pw().runOnApiThread(() -> pw().contextForApi());
  }

  private PlaywrightSession pw() {
    PlaywrightSession s = driver();
    if (s != null) {
      return s;
    }
    for (DriverSession<?> d : DriverSessionManager.inThisTestThread().getCurrentDrivers()) {
      if (d instanceof PlaywrightSession pws && d.isActive()) {
        return pws;
      }
    }
    throw new IllegalStateException(
      "No Playwright session on this test thread. Open a browser (e.g. via the Playwright engine) "
        + "or call DriverSessionManager.inThisTestThread().setCurrentActiveDriver(session) after registering the driver."
    );
  }

  @Override
  public String currentUrl() {
    return pw().runOnApiThread(() -> pw().pageForApi().url());
  }

  @Override
  public String pageTitle() {
    return pw().runOnApiThread(() -> pw().pageForApi().title());
  }

  @Override
  public void open(String url) {
    pw().runOnApiThread(() -> {
      pw().pageForApi().navigate(url);
      return null;
    });
  }

  @Override
  public void refresh() {
    pw().runOnApiThread(() -> {
      pw().pageForApi().reload();
      return null;
    });
  }

  @Override
  public void reload() {
    pw().runOnApiThread(() -> {
      pw().pageForApi().reload();
      return null;
    });
  }

  @Override
  public void forward() {
    pw().runOnApiThread(() -> {
      pw().pageForApi().goForward();
      return null;
    });
  }

  @Override
  public void back() {
    pw().runOnApiThread(() -> {
      pw().pageForApi().goBack();
      return null;
    });
  }

  public com.microsoft.playwright.Locator findOne(String locator) {
    try {
      PlaywrightSession s = pw();
      return s.runOnApiThread(() -> s.pageForApi().locator(locator).first());
    } catch (Throwable ignored) {
      return null;
    }
  }

  public com.microsoft.playwright.Locator findOne(String locator, Page.LocatorOptions locatorOptions) {
    try {
      PlaywrightSession s = pw();
      return s.runOnApiThread(() -> s.pageForApi().locator(locator, locatorOptions).first());
    } catch (Throwable ignored) {
      return null;
    }
  }

  public List<com.microsoft.playwright.Locator> findAll(String locator) {
    PlaywrightSession s = pw();
    return s.runOnApiThread(() -> {
      com.microsoft.playwright.Locator base = s.pageForApi().locator(locator);
      base.first().waitFor();
      return base.all();
    });
  }

  public List<com.microsoft.playwright.Locator> findAll(String locator, Page.LocatorOptions locatorOptions) {
    PlaywrightSession s = pw();
    return s.runOnApiThread(() -> {
      com.microsoft.playwright.Locator base = s.pageForApi().locator(locator, locatorOptions);
      base.first().waitFor();
      return base.all();
    });
  }
}
