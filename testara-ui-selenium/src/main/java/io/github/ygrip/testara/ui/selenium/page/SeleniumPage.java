package io.github.ygrip.testara.ui.selenium.page;

import java.lang.reflect.Proxy;
import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.PageFactory;

import io.github.ygrip.testara.ui.selenium.driver.SeleniumSession;
import io.github.ygrip.testara.ui.page.PageContext;

import lombok.extern.log4j.Log4j2;

@Log4j2
public abstract class SeleniumPage extends PageContext<SeleniumSession> {

  public SeleniumPage() {
    PageFactory.initElements(driver().instance(), this);
  }

  @Override
  public String currentUrl() {
    return driver().instance()
      .getCurrentUrl();
  }

  @Override
  public String pageTitle() {
    return driver().instance()
      .getTitle();
  }

  @Override
  public void open(String url) {
    driver().instance()
      .get(url);
  }

  @Override
  public void refresh() {
    driver().instance()
      .navigate()
      .refresh();
  }

  @Override
  public void reload() {
    driver().instance()
      .manage()
      .deleteAllCookies();
    driver().instance()
      .navigate()
      .refresh();
  }

  @Override
  public void forward() {
    driver().instance()
      .navigate()
      .forward();
  }

  @Override
  public void back() {
    driver().instance()
      .navigate()
      .back();
  }

  public WebElement findOne(By locator) {
    try {
      return (WebElement) Proxy.newProxyInstance(
        WebElement.class.getClassLoader(), new Class[] {WebElement.class}, (proxy, method, args) -> {
          WebElement element = driver().instance()
            .findElement(locator);
          return method.invoke(element, args);
        }
      );
    } catch (Throwable ignored) {
      return null;
    }
  }

  @SuppressWarnings("unchecked")
  public List<WebElement> findAll(By locator) {
    return (List<WebElement>) Proxy.newProxyInstance(
      List.class.getClassLoader(), new Class[] {List.class}, (proxy, method, args) -> {
        List<WebElement> elements = driver().instance()
          .findElements(locator);
        return method.invoke(elements, args);
      }
    );
  }
}
