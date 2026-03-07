package io.github.ygrip.testara.ui.playwright.page;

import java.lang.reflect.Proxy;
import java.util.List;

import io.github.ygrip.testara.ui.page.PageContext;
import io.github.ygrip.testara.ui.playwright.driver.PlaywrightSession;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Page;

import lombok.extern.log4j.Log4j2;

@Log4j2
public abstract class PlaywrightPage extends PageContext<PlaywrightSession> {

  public Page page() {
    return driver().page();
  }

  public BrowserContext context() {
    return driver().context();
  }

  @Override
  public String currentUrl() {
    return page().url();
  }

  @Override
  public String pageTitle() {
    return page().title();
  }

  @Override
  public void open(String url) {
    page().navigate(url);
  }

  @Override
  public void refresh() {
    page().reload();
  }

  @Override
  public void reload() {
    page().reload();
  }

  @Override
  public void forward() {
    page().goForward();
  }

  @Override
  public void back() {
    page().goBack();
  }

  public ElementHandle findOne(String locator) {
    try {
      return (ElementHandle) Proxy.newProxyInstance(
        ElementHandle.class.getClassLoader(), new Class[] {ElementHandle.class}, (proxy, method, args) -> {
          ElementHandle element = page().locator(locator)
            .elementHandle();
          return method.invoke(element, args);
        }
      );
    } catch (Throwable ignored) {
      return null;
    }
  }

  public ElementHandle findOne(String locator, Page.LocatorOptions locatorOptions) {
    try {
      return (ElementHandle) Proxy.newProxyInstance(
        ElementHandle.class.getClassLoader(), new Class[] {ElementHandle.class}, (proxy, method, args) -> {
          ElementHandle element = page().locator(locator, locatorOptions)
            .elementHandle();
          return method.invoke(element, args);
        }
      );
    } catch (Throwable ignored) {
      return null;
    }
  }

  @SuppressWarnings("unchecked")
  public List<ElementHandle> findAll(String locator) {
    return (List<ElementHandle>) Proxy.newProxyInstance(
      List.class.getClassLoader(), new Class[] {List.class}, (proxy, method, args) -> {
        List<ElementHandle> elements = page().locator(locator)
          .elementHandles();
        return method.invoke(elements, args);
      }
    );
  }

  @SuppressWarnings("unchecked")
  public List<ElementHandle> findAll(String locator, Page.LocatorOptions locatorOptions) {
    return (List<ElementHandle>) Proxy.newProxyInstance(
      List.class.getClassLoader(), new Class[] {List.class}, (proxy, method, args) -> {
        List<ElementHandle> elements = page().locator(locator)
          .elementHandles();
        return method.invoke(elements, args);
      }
    );
  }
}
