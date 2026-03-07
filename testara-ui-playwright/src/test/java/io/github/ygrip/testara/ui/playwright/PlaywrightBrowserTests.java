package io.github.ygrip.testara.ui.playwright;

import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.ygrip.testara.core.BaseTests;
import io.github.ygrip.testara.core.TestWith;
import io.github.ygrip.testara.ui.context.TestUI;
import io.github.ygrip.testara.ui.driver.DriverSessionManager;
import io.github.ygrip.testara.ui.executor.ActorManager;
import io.github.ygrip.testara.ui.interaction.Click;
import io.github.ygrip.testara.ui.interaction.Enter;
import io.github.ygrip.testara.ui.interaction.Navigate;
import io.github.ygrip.testara.ui.interaction.SeeThat;
import io.github.ygrip.testara.ui.interaction.Submit;
import io.github.ygrip.testara.ui.interaction.WaitUntil;
import io.github.ygrip.testara.ui.page.Element;
import io.github.ygrip.testara.ui.page.NamedPage;
import io.github.ygrip.testara.ui.playwright.driver.PlaywrightSession;

import lombok.extern.log4j.Log4j2;

@Log4j2
@Tag("ui")
@Tag("playwright")
@TestWith(properties = {"classpath:application.properties", "classpath:configuration.properties"})
public class PlaywrightBrowserTests extends BaseTests {

  @Test
  void usingScreenPlayPattern() throws Exception {
    final var pageName = "github";
    try (PlaywrightSession session = TestUI.with("playwright")
      .forDriver("chrome")) {
      ActorManager.actorWith(session)
        .attemptsTo(
          Navigate.to(NamedPage.of(pageName)),
          WaitUntil.page(NamedPage.of(pageName)).loaded().withTimeout(Duration.ofSeconds(2)),
          SeeThat.page(NamedPage.of(pageName)),
          WaitUntil.visible(Element.of("github logo")),
          Click.on(Element.of("search bar")),
          WaitUntil.visible(Element.of("input search field")),
          Enter.text("user:ygrip").into(Element.of("input search field")),
          Submit.into(Element.of("input search field")),
          WaitUntil.visible(Element.of("profile card")).withTimeout(Duration.ofSeconds(3)),
          SeeThat.containsText("Yunaz Gilang Ramadhan").on(Element.of("profile card"))
        );
    }
  }

  @AfterEach
  public void afterEach() {
    try {
      DriverSessionManager.inThisTestThread()
        .getCurrentDriver()
        .close();
    } catch (Exception err) {
      log.warn("Got issue while closing active driver : {}", err.getMessage());
    }
  }
}
