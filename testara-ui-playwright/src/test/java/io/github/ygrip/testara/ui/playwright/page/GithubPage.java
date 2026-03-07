package io.github.ygrip.testara.ui.playwright.page;

import io.github.ygrip.testara.ui.model.DeviceType;
import io.github.ygrip.testara.ui.model.Locator;
import io.github.ygrip.testara.ui.model.Page;
import com.microsoft.playwright.ElementHandle;

@Page(name = "github",
  url = "https://github.com",
  platforms = {DeviceType.DEFAULT, DeviceType.DESKTOP, DeviceType.MOBILE}
)
public class GithubPage extends PlaywrightPage {
  private static final Locator SEARCH_BAR = Locator.className("search-input");
  private static final Locator INPUT_SEARCH_FIELD = Locator.id("query-builder-test");
  private final ElementHandle githubLogo = findOne("header svg.octicon-mark-github");
  private final ElementHandle githubIcon = findOne("header svg.octicon-mark-github");
  private final ElementHandle PROFILE_CARD = findOne(".vcard-names-container");
}
