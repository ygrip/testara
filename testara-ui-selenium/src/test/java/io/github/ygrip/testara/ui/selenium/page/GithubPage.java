package io.github.ygrip.testara.ui.selenium.page;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;

import io.github.ygrip.testara.ui.model.DeviceType;
import io.github.ygrip.testara.ui.model.Locator;
import io.github.ygrip.testara.ui.model.Page;

@Page(name = "github",
  url = "https://github.com",
  platforms = {DeviceType.DEFAULT, DeviceType.DESKTOP, DeviceType.MOBILE}
)
public class GithubPage extends SeleniumPage {
  private static final Locator SEARCH_BAR = Locator.className("search-input");
  private static final Locator INPUT_SEARCH_FIELD = Locator.id("query-builder-test");
  private WebElement githubLogo = findOne(By.cssSelector("header svg.octicon-mark-github"));
  @FindBy(css = "header svg.octicon-mark-github")
  private WebElement githubIcon;
  private static final By PROFILE_CARD = By.className("vcard-names-container");
}
