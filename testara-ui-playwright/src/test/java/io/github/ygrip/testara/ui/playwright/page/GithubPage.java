package io.github.ygrip.testara.ui.playwright.page;

import io.github.ygrip.testara.ui.model.DeviceType;
import io.github.ygrip.testara.ui.model.Locator;
import io.github.ygrip.testara.ui.model.Page;

@Page(name = "github",
  url = "https://github.com",
  platforms = {DeviceType.DEFAULT, DeviceType.DESKTOP, DeviceType.MOBILE}
)
public class GithubPage extends PlaywrightPage {
  private static final Locator SEARCH_BAR = Locator.className("search-input");
  private static final Locator INPUT_SEARCH_FIELD = Locator.id("query-builder-test");
  private final com.microsoft.playwright.Locator githubLogo = findOne("header svg.octicon-mark-github");
  private final com.microsoft.playwright.Locator githubIcon = findOne("header svg.octicon-mark-github");
  private final com.microsoft.playwright.Locator PROFILE_CARD = findOne(".vcard-names-container");
}
