package io.github.ygrip.testara.ui.selenium;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.openqa.selenium.WebDriver;

import com.fasterxml.jackson.core.type.TypeReference;

import io.github.ygrip.testara.core.BaseTests;
import io.github.ygrip.testara.core.TestWith;
import io.github.ygrip.testara.core.file.FileHelper;
import io.github.ygrip.testara.core.mapper.MapperHelper;
import io.github.ygrip.testara.core.support.CommonHelper;
import io.github.ygrip.testara.ui.context.TestUI;
import io.github.ygrip.testara.ui.driver.DriverSessionManager;
import io.github.ygrip.testara.ui.interaction.Scroll;
import io.github.ygrip.testara.ui.observation.AllText;
import io.github.ygrip.testara.ui.observation.CountElements;
import io.github.ygrip.testara.ui.observation.ExecuteScript;
import io.github.ygrip.testara.ui.observation.TheAttribute;
import io.github.ygrip.testara.ui.observation.TheText;
import io.github.ygrip.testara.ui.populator.PopulateFor;
import io.github.ygrip.testara.ui.populator.Resolve;
import io.github.ygrip.testara.ui.selenium.driver.SeleniumSession;
import io.github.ygrip.testara.ui.selenium.engine.SeleniumEngine;
import io.github.ygrip.testara.ui.executor.ActorManager;
import io.github.ygrip.testara.ui.interaction.Click;
import io.github.ygrip.testara.ui.interaction.Enter;
import io.github.ygrip.testara.ui.interaction.Navigate;
import io.github.ygrip.testara.ui.interaction.SeeThat;
import io.github.ygrip.testara.ui.interaction.Submit;
import io.github.ygrip.testara.ui.interaction.WaitUntil;
import io.github.ygrip.testara.ui.page.Element;
import io.github.ygrip.testara.ui.page.NamedPage;

import lombok.extern.log4j.Log4j2;

@Log4j2
@Tag("ui")
@Tag("selenium")
@TestWith(properties = {"classpath:application.properties", "classpath:configuration.properties"})
public class SeleniumDriverTests extends BaseTests {

  @Test
  void openChromeBrowser() throws Exception {
    final var chrome = TestUI.with(SeleniumEngine.class)
      .forDriver("chrome")
      .instanceOf(WebDriver.class);
    chrome.get("https://www.google.com");
  }

  @Test
  void openFirefoxBrowser() throws Exception {
    final var firefox = TestUI.with(SeleniumEngine.class)
      .forDriver("firefox")
      .instanceOf(WebDriver.class);
    firefox.get("https://www.google.com");
  }

  @Test
  void openChromeMobileEmulation() throws Exception {
    final var chrome = TestUI.with(SeleniumEngine.class)
      .forDriver("chrome", "mobile")
      .instanceOf(WebDriver.class);
    chrome.get("https://www.google.com");
  }

  @Test
  void openFirefoxMobileEmulation() throws Exception {
    final var firefox = TestUI.with(SeleniumEngine.class)
      .forDriver("firefox", "mobile")
      .instanceOf(WebDriver.class);
    firefox.get("https://www.google.com");
  }

  @Test
  void reuseBrowserInTheSameThread() throws Exception {
    var chrome = TestUI.with(SeleniumEngine.class)
      .forDriver("chrome")
      .instanceOf(WebDriver.class);
    chrome.get("https://www.google.com");

    var anotherChrome = TestUI.with(SeleniumEngine.class)
      .forDriver("chrome")
      .instanceOf(WebDriver.class);
    anotherChrome.get("https://www.github.com/ygrip");

    assertThat(chrome, equalTo(anotherChrome));
    chrome.quit();
  }

  @Test
  void usingScreenPlayPattern() throws Exception {
    final var pageName = "github";
    try(SeleniumSession session = TestUI.with(SeleniumEngine.class).forDriver("chrome")){
      ActorManager.actorWith(session).attemptsTo(
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

  @Test
  @Timeout(value = 2, unit = java.util.concurrent.TimeUnit.MINUTES)
  void populatePageData() throws Exception {
    //@formatter:off
    final var pageName = "pokemon";
    final var startNanos = System.nanoTime();
    SeleniumSession session = TestUI.with(SeleniumEngine.class).forDriver("chrome");
    ActorManager.actorWith(session).attemptsTo(
      Navigate.to(NamedPage.of(pageName)),
      WaitUntil.page(NamedPage.of(pageName)).loaded().withTimeout(Duration.ofSeconds(2)),
      SeeThat.page(NamedPage.of(pageName))
    );

    String domain = "https://pokemondb.net";

    // Types: list of type strings per card (each "pokemon types" item is one type link;)
    final var getSiblingTextScript = "return arguments[0].previousElementSibling.innerText;";
    var typesPopulator = Resolve.from(AllText.of("pokemon types"));
    var pokemonNumberPopulator = Resolve.from(TheText.of("pokemon number")).asInteger();
    var linkPopulator = Resolve.from(TheAttribute.of("href").on("pokemon link"));
    var generationNumberPopulator = Resolve.from(ExecuteScript.of(getSiblingTextScript).withNoArguments())
      .asInteger();

    // One pokemon card: number, name, link, image, types (each .set().with() ends with .build() to chain)
    var pokemonCardPopulator = PopulateFor.all("info card")
      .set("number", pokemonNumberPopulator)
      .set("name", Resolve.from(TheText.of("pokemon name")))
      .set("image", Resolve.from(TheAttribute.of("src").on("image link")))
      .set("types", typesPopulator)
      .set("link").with(linkPopulator).into(link -> domain + link)
      .build();

    List<Map<String, Object>> result = PopulateFor.all("generations")
      .perform(Scroll.to(Element.of("generation number").precedingSibling()).andAlignToTop())
      .set("total", Resolve.from(CountElements.of("info card")).asInteger())
      .set("pokemons", pokemonCardPopulator)
      .set("generation", generationNumberPopulator)
      .andThen().resolveAs(new TypeReference<>() {});

    long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
    if (elapsedMs > 120_000) {
      log.warn("process exceeded 2 minutes: {} ms", elapsedMs);
    }

    if (!CommonHelper.isBlank(result)) {
      String baseDir = System.getProperty("user.dir");
      Path resultPath = Path.of(baseDir, "target", "pokemon.json");
      FileHelper.writeJson(result, resultPath.toString());
      String resultJson = Files.readString(resultPath);
      List<?> resultList = MapperHelper.toObject(resultJson, List.class);
      assertThat("target/pokemon.json not be empty", resultList, is(notNullValue()));
    }
    //@formatter:on
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
