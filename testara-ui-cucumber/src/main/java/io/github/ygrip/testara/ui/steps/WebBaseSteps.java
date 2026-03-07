package io.github.ygrip.testara.ui.steps;

import static io.github.ygrip.testara.command.CommandExecutor.executeCommand;

import java.time.Duration;
import java.util.Map;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;

import com.fasterxml.jackson.core.type.TypeReference;
import io.github.ygrip.testara.core.context.TestComponent;
import io.github.ygrip.testara.core.registry.RegistryScope;
import io.github.ygrip.testara.core.transformer.TransformerService;
import io.github.ygrip.testara.ui.context.TestUI;
import io.github.ygrip.testara.ui.driver.DriverSessionManager;
import io.github.ygrip.testara.ui.executor.ActorManager;
import io.github.ygrip.testara.ui.interaction.Blur;
import io.github.ygrip.testara.ui.interaction.Clear;
import io.github.ygrip.testara.ui.interaction.Click;
import io.github.ygrip.testara.ui.interaction.Enter;
import io.github.ygrip.testara.ui.interaction.Focus;
import io.github.ygrip.testara.ui.interaction.ForceClick;
import io.github.ygrip.testara.ui.interaction.Hover;
import io.github.ygrip.testara.ui.interaction.Navigate;
import io.github.ygrip.testara.ui.interaction.Scroll;
import io.github.ygrip.testara.ui.interaction.SeeThat;
import io.github.ygrip.testara.ui.interaction.SelectOption;
import io.github.ygrip.testara.ui.interaction.Submit;
import io.github.ygrip.testara.ui.interaction.Tab;
import io.github.ygrip.testara.ui.interaction.WaitUntil;
import io.github.ygrip.testara.ui.observation.ThisPage;
import io.github.ygrip.testara.ui.page.Element;
import io.github.ygrip.testara.ui.page.NamedPage;

import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.extern.log4j.Log4j2;

/**
 * @author yunaz.ramadhan on 12/20/2019
 */
@Log4j2
@TestComponent(scope = RegistryScope.TEST)
public class WebBaseSteps {

  @Given("^(.+) using (\\w+) in (desktop|mobile|android|ios)$")
  public void actorNamedUsingDevice(String identifier, String application, String platform) throws Throwable {
    TestUI.withDefaultEngine()
      .forDriver(application, platform);
  }

  @When("^(.+) open \"([^\"]*)\" page$")
  public void actorOpenPage(String identifier, String pageName) throws Throwable {
    ActorManager.currentActor()
      .attemptsTo(Navigate.to(NamedPage.of(pageName)));
  }

  @When("^(.+) enter value \"([^\"]*)\" on \"([^\"]*)\"$")
  public void actorEnterValue(String identifier, String value, String element) throws Throwable {
    final String text = executeCommand(value);
    ActorManager.currentActor()
      .attemptsTo(
        Enter.text(text)
          .into(element), Submit.into(element)
      );
  }

  @When("^(.+) clear text from \"([^\"]*)\"$")
  public void clearText(String identifier, String element) throws Throwable {
    ActorManager.currentActor()
      .attemptsTo(Clear.field(element));
  }

  @When("^(.+) clear text from \"([^\"]*)\" in page \"([^\"]*)\"$")
  public void clearText(String identifier, String element, String page) throws Throwable {
    final var session = DriverSessionManager.inThisTestThread()
      .getCurrentDriver();
    ActorManager.currentActor()
      .attemptsTo(Clear.field(Element.of(element)
        .on(session.finder()
          .getPage(page))));
  }

  @When("^(.+) type value \"([^\"]*)\" to \"([^\"]*)\"$")
  public void actorTypeValueTo(String identifier, String value, String element) throws Throwable {
    final String text = executeCommand(value);
    ActorManager.currentActor()
      .attemptsTo(Enter.text(text)
        .into(element));
  }

  @When("^(.+) enter value \"([^\"]*)\" on \"([^\"]*)\" in the \"([^\"]*)\" page$")
  public void actorEnterValueInThePage(String identifier, String value, String element, String pageName)
    throws Throwable {
    final var session = DriverSessionManager.inThisTestThread()
      .getCurrentDriver();
    final String text = executeCommand(value);
    final var target = Element.of(element)
      .on(session.finder()
        .getPage(pageName));
    ActorManager.currentActor()
      .attemptsTo(
        Enter.text(text)
          .into(target), Submit.into(target)
      );
  }

  @When("^(.+) type value \"([^\"]*)\" to \"([^\"]*)\" in the \"([^\"]*)\" page$")
  public void actorTypeValueTo(String identifier, String value, String element, String pageName) throws Throwable {
    final var session = DriverSessionManager.inThisTestThread()
      .getCurrentDriver();
    final String text = executeCommand(value);
    final var target = Element.of(element)
      .on(session.finder()
        .getPage(pageName));
    ActorManager.currentActor()
      .attemptsTo(Enter.text(text)
        .into(target));
  }

  @When("^(.+) refresh page$")
  public void refreshPage(String identifier) throws Throwable {
    ActorManager.currentActor()
      .attemptsTo(Navigate.refresh());
  }

  @When("^(.+) reload page$")
  public void reloadPage(String identifier) throws Throwable {
    // this will also clear cookies
    ActorManager.currentActor()
      .attemptsTo(Navigate.reload());
  }

  @When("^(.+) open new tab$")
  public void actorOpenNewTab(String identifier) throws Throwable {
    ActorManager.currentActor()
      .attemptsTo(Tab.openNew());
  }

  @When("^(.+) open page \"([^\"]*)\" in a new tab$")
  public void actorOpenNewTab(String identifier, String pageName) throws Throwable {
    final var session = DriverSessionManager.inThisTestThread()
      .getCurrentDriver();
    final var url = session.finder()
      .getPage(pageName)
      .pageUrl();
    ActorManager.actorWith(session)
      .attemptsTo(Tab.openNew(url));
  }

  @When("^(.+) switch to tab at index (\\d+)$")
  public void actorOpenNewTab(String identifier, int index) throws Throwable {
    ActorManager.currentActor()
      .attemptsTo(Tab.switchTo(index));
  }

  @When("^(.+) scroll to the \"([^\"]*)\"$")
  public void actorScrollToTheElement(String identifier, String element) throws Throwable {
    ActorManager.currentActor()
      .attemptsTo(Scroll.to(element)
        .andAlignToTop());
  }

  @When("^(.+) scroll to the \"([^\"]*)\" in the \"([^\"]*)\"$")
  public void actorScrollToTheElementOnPage(String identifier, String element, String page) throws Throwable {
    final var session = DriverSessionManager.inThisTestThread()
      .getCurrentDriver();
    final var target = Element.of(element)
      .on(session.finder()
        .getPage(page));
    ActorManager.currentActor()
      .attemptsTo(Scroll.to(target)
        .andAlignToTop());
  }

  @When("^(.+) click the \"([^\"]*)\"$")
  public void actorClickOnTheElement(String identifier, String element) throws Throwable {
    ActorManager.currentActor()
      .attemptsTo(Click.on(element));
  }

  @When("^(.+) click the \"([^\"]*)\" in the \"([^\"]*)\" page$")
  public void actorClickOnTheElementOnPage(String identifier, String element, String page) throws Throwable {
    final var session = DriverSessionManager.inThisTestThread()
      .getCurrentDriver();
    final var target = Element.of(element)
      .on(session.finder()
        .getPage(page));
    ActorManager.currentActor()
      .attemptsTo(Click.on(target));
  }

  @When("^(.+) focus to \"([^\"]*)\"$")
  public void actorFocusToTheElement(String identifier, String element) throws Throwable {
    ActorManager.currentActor()
      .attemptsTo(Focus.on(element));
  }

  @When("^(.+) focus to \"([^\"]*)\" in the \"([^\"]*)\" page$")
  public void actorFocusTheElementOnPage(String identifier, String element, String page) throws Throwable {
    final var session = DriverSessionManager.inThisTestThread()
      .getCurrentDriver();
    final var target = Element.of(element)
      .on(session.finder()
        .getPage(page));
    ActorManager.currentActor()
      .attemptsTo(Focus.on(target));
  }

  @When("^(.+) blur from \"([^\"]*)\"$")
  public void actorBlurToTheElement(String identifier, String element) throws Throwable {
    ActorManager.currentActor()
      .attemptsTo(Blur.from(element));
  }

  @When("^(.+) blur from \"([^\"]*)\" in the \"([^\"]*)\" page$")
  public void actorBlurTheElementOnPage(String identifier, String element, String page) throws Throwable {
    final var session = DriverSessionManager.inThisTestThread()
      .getCurrentDriver();
    final var target = Element.of(element)
      .on(session.finder()
        .getPage(page));
    ActorManager.currentActor()
      .attemptsTo(Blur.from(target));
  }

  @Then("^(.+) is in \"([^\"]*)\" page$")
  public void actorIsIn(String identifier, String pageName) throws Throwable {
    ActorManager.currentActor()
      .attemptsTo(SeeThat.page(NamedPage.of(pageName)));
  }

  @Then("^(.+) should see \"([^\"]*)\" is (displayed|not displayed)$")
  public void actorShouldSee(String identifier, String element, String display) throws Throwable {
    display = display.trim()
      .toLowerCase();
    if (display.equals("displayed")) {
      ActorManager.currentActor()
        .attemptsTo(SeeThat.visible(element));
    } else {
      ActorManager.currentActor()
        .attemptsTo(SeeThat.hidden(element));
    }
  }

  @Then("^(.+) element \"([^\"]*)\" should contains text \"([^\"]*)\"$")
  public void elementShouldContainsText(String identifier, String element, String contains) throws Throwable {
    contains = executeCommand(contains);
    ActorManager.currentActor()
      .attemptsTo(SeeThat.containsText(contains)
        .on(element));
  }

  @Then("^(.+) element \"([^\"]*)\" should have attribute \"([^\"]*)\"$")
  public void elementShouldContainsAttribute(String identifier, String element, String attribute) throws Throwable {
    ActorManager.currentActor()
      .attemptsTo(SeeThat.attribute(attribute)
        .on(element));
  }

  @When("^(.+) select visible text \"([^\"]*)\" from drop down \"([^\"]*)\"$")
  public void actorSelectVisibleTextFromDropDown(String identifier, String visibleText, String element)
    throws Throwable {
    visibleText = executeCommand(visibleText);
    ActorManager.currentActor()
      .attemptsTo(
        ForceClick.on(element),
        SelectOption.from(element)
          .byVisibleText(visibleText)
      );
  }

  @When("^(.+) select value \"([^\"]*)\" from drop down \"([^\"]*)\"$")
  public void actorSelectValueFromDropDown(String identifier, String value, String element) throws Throwable {
    value = executeCommand(value);
    ActorManager.currentActor()
      .attemptsTo(
        ForceClick.on(element),
        SelectOption.from(element)
          .byValue(value)
      );
  }

  @When("^(.+) select index \"([^\"]*)\" from drop down \"([^\"]*)\"$")
  public void actorSelectIndexFromDropDown(String identifier, String index, String element) throws Throwable {
    index = executeCommand(index);
    assert index != null;
    int selected = Integer.parseInt(index);
    ActorManager.currentActor()
      .attemptsTo(
        ForceClick.on(element),
        SelectOption.from(element)
          .byIndex(selected)
      );
  }

  @Then("^(.+) should see \"([^\"]*)\" is (clickable|not clickable)$")
  public void elementIsClickAble(String identifier, String element, String clickable) throws Throwable {
    clickable = clickable.trim()
      .toLowerCase();
    if (clickable.equals("clickable")) {
      ActorManager.currentActor()
        .attemptsTo(SeeThat.clickable(element));
    } else {
      ActorManager.currentActor()
        .attemptsTo(SeeThat.disabled(element));
    }
  }

  @When("^(.+) do \"([^\"]*)\" in \"([^\"]*)\" page$")
  public void actorDoActionOnPageWithName(String identifier, String action, String pageName) throws Throwable {
    ActorManager.currentActor()
      .executeTask(action, pageName);
  }

  @When("^(.+) do \"([^\"]*)\" in \"([^\"]*)\" page with parameter$")
  public void actorDoActionOnPageWithName(String identifier, String action, String pageName, DataTable table)
    throws Throwable {
    Map<String, Object> additionalParameter = new TransformerService().sourceData(table.cells())
      .to(new TypeReference<>() {
      });
    ActorManager.currentActor()
      .executeTask(action, pageName, additionalParameter);
  }

  @When("^(.+) do \"([^\"]*)\"$")
  public void actorDoActionOnCurrentPage(String identifier, String action) throws Throwable {
    ActorManager.currentActor()
      .executeTask(action);
  }

  @When("^(.+) do \"([^\"]*)\" with parameter$")
  public void actorDoActionOnCurrentPage(String identifier, String action, DataTable table) throws Throwable {
    Map<String, Object> additionalParameter = new TransformerService().sourceData(table.cells())
      .to(new TypeReference<>() {
      });
    ActorManager.currentActor()
      .executeTask(action, additionalParameter);
  }

  @When("^(.+) wait until \"([^\"]*)\" is (enabled|visible|disabled|not visible|clickable|present|not present|not clickable|selected)$")
  public void waitUntilElementIs(String identifier, String element, String condition) throws Throwable {
    try {
      switch (condition) {
        case "enabled":
          ActorManager.currentActor()
            .attemptsTo(WaitUntil.enabled(element));
          break;
        case "visible":
          ActorManager.currentActor()
            .attemptsTo(WaitUntil.visible(element));
          break;
        case "disabled":
          ActorManager.currentActor()
            .attemptsTo(WaitUntil.disabled(element));
          break;
        case "not visible", "not present":
          ActorManager.currentActor()
            .attemptsTo(WaitUntil.hidden(element));
          break;
        case "clickable":
          ActorManager.currentActor()
            .attemptsTo(WaitUntil.clickable(element));
          break;
        case "present":
          ActorManager.currentActor()
            .attemptsTo(WaitUntil.present(element));
          break;
        case "selected":
          ActorManager.currentActor()
            .attemptsTo(WaitUntil.selected(element));
          break;
        default:
          break;
      }
    } catch (Exception exception) {
      log.warn("Element located by {} is not {}", element, condition, exception.getCause());
    }
  }

  private boolean doesElementHasCondition(String element, String condition) throws Throwable {
    condition = condition.trim()
      .toLowerCase();
    try {
      return switch (condition) {
        case "enabled" -> {
          ActorManager.currentActor()
            .attemptsTo(SeeThat.enabled(element));
          yield true;
        }
        case "visible" -> {
          ActorManager.currentActor()
            .attemptsTo(SeeThat.visible(element));
          yield true;
        }
        case "disabled" -> {
          ActorManager.currentActor()
            .attemptsTo(SeeThat.disabled(element));
          yield true;
        }
        case "not visible", "not present" -> {
          ActorManager.currentActor()
            .attemptsTo(SeeThat.hidden(element));
          yield true;
        }
        case "clickable" -> {
          ActorManager.currentActor()
            .attemptsTo(SeeThat.clickable(element));
          yield true;
        }
        case "present" -> {
          ActorManager.currentActor()
            .attemptsTo(SeeThat.present(element));
          yield true;
        }
        case "selected" -> {
          ActorManager.currentActor()
            .attemptsTo(SeeThat.selected(element));
          yield true;
        }
        default -> false;
      };
    } catch (Exception ignored) {
      log.warn("Element does not have attribute for {}", condition);
      return false;
    }
  }

  @When("^(.+) click the \"([^\"]*)\" if the \"([^\"]*)\" is (enabled|visible|disabled|not visible|clickable|present|not present|not clickable|selected)$")
  public void clickIf(String identifier, String targetElement, String elementToCheck, String condition)
    throws Throwable {
    if (doesElementHasCondition(elementToCheck, condition)) {
      actorClickOnTheElement(identifier, targetElement);
    }
  }

  @When("^(.+) type \"([^\"]*)\" into \"([^\"]*)\" if the \"([^\"]*)\" is (enabled|visible|disabled|not visible|clickable|present|not present|not clickable|selected)$")
  public void clickIf(String identifier, String input, String targetElement, String elementToCheck, String condition)
    throws Throwable {
    if (doesElementHasCondition(elementToCheck, condition)) {
      actorTypeValueTo(identifier, input, targetElement);
    }
  }

  @Then("^(.+) should see current url (contains|contains ignore case|equal|equal ignore case|matches|starts with|starts with ignore case|ends with|ends with ignore case) with value \"([^\"]*)\"$")
  public void browserUrlValidation(String identifier, String validation, String expectedUrl) throws Exception {
    expectedUrl = executeCommand(expectedUrl);
    final var session = DriverSessionManager.inThisTestThread()
      .getCurrentDriver();
    final var actor = ActorManager.actorWith(session);

    actor.attemptsTo(WaitUntil.url(expectedUrl)
      .withTimeout(Duration.ofSeconds(5)));
    final String actualUrl = actor.observe(ThisPage.url());

    if (validation.equalsIgnoreCase("equal")) {
      MatcherAssert.assertThat(
        String.format("Current url is not equal with '%s'", expectedUrl),
        actualUrl,
        Matchers.equalTo(expectedUrl)
      );
    } else if (validation.equalsIgnoreCase("equal ignore case")) {
      MatcherAssert.assertThat(
        String.format("Current url does not equal ignore case '%s'", expectedUrl),
        actualUrl,
        Matchers.equalToIgnoringCase(expectedUrl)
      );
    } else if (validation.equalsIgnoreCase("contains")) {
      MatcherAssert.assertThat(
        String.format("Current url does not contain '%s'", expectedUrl),
        actualUrl,
        Matchers.containsString(expectedUrl)
      );
    } else if (validation.equalsIgnoreCase("contains ignore case")) {
      MatcherAssert.assertThat(
        String.format("Current url does not contain ignore case '%s'", expectedUrl),
        actualUrl,
        Matchers.containsStringIgnoringCase(expectedUrl)
      );
    } else if (validation.equalsIgnoreCase("starts with")) {
      MatcherAssert.assertThat(
        String.format("Current url does not starts with '%s'", expectedUrl),
        actualUrl,
        Matchers.startsWith(expectedUrl)
      );
    } else if (validation.equalsIgnoreCase("ends with")) {
      MatcherAssert.assertThat(
        String.format("Current url does not ends with '%s'", expectedUrl),
        actualUrl,
        Matchers.endsWith(expectedUrl)
      );
    } else if (validation.equalsIgnoreCase("starts with ignore case")) {
      MatcherAssert.assertThat(
        String.format("Current url does not starts with ignore case '%s'", expectedUrl),
        actualUrl,
        Matchers.startsWithIgnoringCase(expectedUrl)
      );
    } else if (validation.equalsIgnoreCase("ends with ignore case")) {
      MatcherAssert.assertThat(
        String.format("Current url does not ends with ignore case '%s'", expectedUrl),
        actualUrl,
        Matchers.endsWithIgnoringCase(expectedUrl)
      );
    } else if (validation.equalsIgnoreCase("matches")) {
      MatcherAssert.assertThat(
        String.format("Current url is not match with '%s'", expectedUrl),
        actualUrl,
        Matchers.matchesPattern(expectedUrl)
      );
    }
  }

  @Then("^(.+) hover on the \"([^\"]*)\"$")
  public void hoverElement(String identifier, String element) throws Throwable {
    ActorManager.currentActor()
      .attemptsTo(Hover.on(element));
  }
}
