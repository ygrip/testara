package io.github.ygrip.testara.ui.steps;

import static io.github.ygrip.testara.command.CommandExecutor.executeCommand;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;

import com.fasterxml.jackson.core.type.TypeReference;
import io.github.ygrip.testara.core.context.TestComponent;
import io.github.ygrip.testara.core.registry.RegistryScope;
import io.github.ygrip.testara.core.transformer.TransformerService;
import io.github.ygrip.testara.ui.context.TestUI;
import io.github.ygrip.testara.ui.driver.DriverSessionManager;
import io.github.ygrip.testara.ui.error.ElementNotFoundException;
import io.github.ygrip.testara.ui.error.PageFailureException;
import io.github.ygrip.testara.ui.error.SessionMismatchException;
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
public class UIBaseSteps {

  @Given("{actor} using {word} in {devices}")
  public void actorNamedUsingDevice(String identifier, String application, String platform) throws Throwable {
    TestUI.withDefaultEngine()
      .forDriver(application, platform);
  }

  @When("{actor} open {string} page")
  public void actorOpenPage(String identifier, String pageName) throws Throwable {
    ActorManager.currentActor()
      .attemptsTo(Navigate.to(NamedPage.of(pageName)));
  }

  @When("{actor} enter value {string} on {string}")
  public void actorEnterValue(String identifier, String value, String element) throws Throwable {
    final String text = executeCommand(value);
    ActorManager.currentActor()
      .attemptsTo(
        Enter.text(text)
          .into(element), Submit.into(element)
      );
  }

  @When("{actor} clear text from {string}")
  public void clearText(String identifier, String element) throws Throwable {
    ActorManager.currentActor()
      .attemptsTo(Clear.field(element));
  }

  @When("{actor} clear text from {string} in page {string}")
  public void clearText(String identifier, String element, String page) throws Throwable {
    ActorManager.currentActor()
      .attemptsTo(Clear.field(toElementContext(element, page)));
  }

  private Element.ElementContext target(String element) {
    return ElementPhraseResolver.resolve(element)
      .orElseGet(() -> Element.of(element));
  }

  private Element.ElementContext toElementContext(String element, String page) {
    final var session = DriverSessionManager.inThisTestThread()
      .getCurrentDriver();
    Optional.ofNullable(session)
      .orElseThrow(() -> new PageFailureException("No available session found"));

    try {
      final var pageContext = session.finder().getPage(page);
      return ElementPhraseResolver.resolve(element, pageContext)
        .orElseGet(() -> Element.of(element).on(pageContext));
    } catch (Exception e) {
      throw new ElementNotFoundException("Element %s is not found".formatted(element), e.getCause());
    }
  }

  private Map<String, Object> resolveParameterValues(Map<String, Object> raw) {
    Map<String, Object> result = new LinkedHashMap<>();
    for (Map.Entry<String, Object> entry : raw.entrySet()) {
      Object value = entry.getValue();
      if (value instanceof String text) {
        result.put(entry.getKey(), executeCommand(text));
      } else {
        result.put(entry.getKey(), value);
      }
    }
    return result;
  }

  @When("{actor} type value {string} to {string}")
  public void actorTypeValueTo(String identifier, String value, String element) throws Throwable {
    final String text = executeCommand(value);
    ActorManager.currentActor()
      .attemptsTo(Enter.text(text)
        .into(element));
  }

  @When("{actor} enter value {string} on {string} in the {string} page")
  public void actorEnterValueInThePage(String identifier, String value, String element, String pageName)
    throws Throwable {
    final String text = executeCommand(value);
    final var target = toElementContext(element, pageName);
    ActorManager.currentActor()
      .attemptsTo(
        Enter.text(text)
          .into(target), Submit.into(target)
      );
  }

  @When("{actor} type value {string} to {string} in the {string} page")
  public void actorTypeValueTo(String identifier, String value, String element, String pageName) throws Throwable {
    final String text = executeCommand(value);
    final var target = toElementContext(element, pageName);
    ActorManager.currentActor()
      .attemptsTo(Enter.text(text)
        .into(target));
  }

  @When("{actor} refresh page")
  public void refreshPage(String identifier) throws Throwable {
    ActorManager.currentActor()
      .attemptsTo(Navigate.refresh());
  }

  @When("{actor} reload page")
  public void reloadPage(String identifier) throws Throwable {
    // this will also clear cookies
    ActorManager.currentActor()
      .attemptsTo(Navigate.reload());
  }

  @When("{actor} open new tab")
  public void actorOpenNewTab(String identifier) throws Throwable {
    ActorManager.currentActor()
      .attemptsTo(Tab.openNew());
  }

  @When("{actor} open page {string} in a new tab")
  public void actorOpenNewTab(String identifier, String pageName) throws Throwable {
    final var session = DriverSessionManager.inThisTestThread()
      .getCurrentDriver();
    final var url = session.finder()
      .getPage(pageName)
      .pageUrl();
    ActorManager.actorWith(session)
      .attemptsTo(Tab.openNew(url));
  }

  @When("{actor} switch to tab at index {int}")
  public void actorOpenNewTab(String identifier, int index) throws Throwable {
    ActorManager.currentActor()
      .attemptsTo(Tab.switchTo(index));
  }

  @When("{actor} scroll to the {string}")
  public void actorScrollToTheElement(String identifier, String element) throws Throwable {
    ActorManager.currentActor()
      .attemptsTo(Scroll.to(target(element))
        .andAlignToTop());
  }

  @When("{actor} scroll to the {string} in the {string}")
  public void actorScrollToTheElementOnPage(String identifier, String element, String page) throws Throwable {
    final var target = toElementContext(element, page);
    ActorManager.currentActor()
      .attemptsTo(Scroll.to(target)
        .andAlignToTop());
  }

  @When("{actor} click the {string}")
  public void actorClickOnTheElement(String identifier, String element) throws Throwable {
    ActorManager.currentActor()
      .attemptsTo(Click.on(target(element)));
  }

  @When("{actor} click the {string} with parameter")
  public void actorClickElementWithParameter(String identifier, String element, DataTable table) throws Throwable {
    Map<String, Object> parameters = new TransformerService().sourceData(table.cells())
      .to(new TypeReference<>() {
      });
    ActorManager.currentActor()
      .attemptsTo(Click.on(Element.named(element)
        .with(resolveParameterValues(parameters))));
  }

  @When("{actor} click the {string} in the {string} page")
  public void actorClickOnTheElementOnPage(String identifier, String element, String page) throws Throwable {
    final var target = toElementContext(element, page);
    ActorManager.currentActor()
      .attemptsTo(Click.on(target));
  }

  @When("{actor} focus to {string}")
  public void actorFocusToTheElement(String identifier, String element) throws Throwable {
    ActorManager.currentActor()
      .attemptsTo(Focus.on(target(element)));
  }

  @When("{actor} focus to {string} in the {string} page")
  public void actorFocusTheElementOnPage(String identifier, String element, String page) throws Throwable {
    final var target = toElementContext(element, page);
    ActorManager.currentActor()
      .attemptsTo(Focus.on(target));
  }

  @When("{actor} blur from {string}")
  public void actorBlurToTheElement(String identifier, String element) throws Throwable {
    ActorManager.currentActor()
      .attemptsTo(Blur.from(target(element)));
  }

  @When("{actor} blur from {string} in the {string} page")
  public void actorBlurTheElementOnPage(String identifier, String element, String page) throws Throwable {
    final var target = toElementContext(element, page);
    ActorManager.currentActor()
      .attemptsTo(Blur.from(target));
  }

  @Then("{actor} is in {string} page")
  public void actorIsIn(String identifier, String pageName) throws Throwable {
    ActorManager.currentActor()
      .attemptsTo(SeeThat.page(NamedPage.of(pageName)));
  }

  @Then("{actor} should see {string} is {displayedOrNotDisplayed}")
  public void actorShouldSee(String identifier, String element, String display) throws Throwable {
    display = display.trim()
      .toLowerCase();
    final var elementContext = target(element);
    if (display.equals("displayed")) {
      ActorManager.currentActor()
        .attemptsTo(SeeThat.visible(elementContext));
    } else {
      ActorManager.currentActor()
        .attemptsTo(SeeThat.hidden(elementContext));
    }
  }

  @Then("{actor} element {string} should contains text {string}")
  public void elementShouldContainsText(String identifier, String element, String contains) throws Throwable {
    contains = executeCommand(contains);
    ActorManager.currentActor()
      .attemptsTo(SeeThat.containsText(contains)
        .on(element));
  }

  @Then("{actor} element {string} should have attribute {string}")
  public void elementShouldContainsAttribute(String identifier, String element, String attribute) throws Throwable {
    ActorManager.currentActor()
      .attemptsTo(SeeThat.attribute(attribute)
        .on(element));
  }

  @When("{actor} select visible text {string} from drop down {string}")
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

  @When("{actor} select value {string} from drop down {string}")
  public void actorSelectValueFromDropDown(String identifier, String value, String element) throws Throwable {
    value = executeCommand(value);
    ActorManager.currentActor()
      .attemptsTo(
        ForceClick.on(element),
        SelectOption.from(element)
          .byValue(value)
      );
  }

  @When("{actor} select index {string} from drop down {string}")
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

  @Then("{actor} should see {string} is {clickableOrNotClickable}")
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

  @When("{actor} do {string} in {string} page")
  public void actorDoActionOnPageWithName(String identifier, String action, String pageName) throws Throwable {
    ActorManager.currentActor()
      .executeTask(action, pageName);
  }

  @When("{actor} do {string} in {string} page with parameter")
  public void actorDoActionOnPageWithName(String identifier, String action, String pageName, DataTable table)
    throws Throwable {
    Map<String, Object> additionalParameter = new TransformerService().sourceData(table.cells())
      .to(new TypeReference<>() {
      });
    ActorManager.currentActor()
      .executeTask(action, pageName, additionalParameter);
  }

  @When("{actor} do {string}")
  public void actorDoActionOnCurrentPage(String identifier, String action) throws Throwable {
    ActorManager.currentActor()
      .executeTask(action);
  }

  @When("{actor} do {string} with parameter")
  public void actorDoActionOnCurrentPage(String identifier, String action, DataTable table) throws Throwable {
    Map<String, Object> additionalParameter = new TransformerService().sourceData(table.cells())
      .to(new TypeReference<>() {
      });
    ActorManager.currentActor()
      .executeTask(action, additionalParameter);
  }

  @When("{actor} wait until page {string} is loaded")
  public void waitUntilPage(String identifier, String page) throws Throwable {
    ActorManager.currentActor().attemptsTo(WaitUntil.page(page).loaded());
  }

    @When("{actor} wait until {string} is {elementState}")
  public void waitUntilElementIs(String identifier, String element, String condition) throws Throwable {
    switch (condition.trim().toLowerCase()) {
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
        throw new IllegalArgumentException("Unsupported wait-until state: '" + condition
          + "'. Expected one of: enabled, visible, disabled, not visible, clickable, present, not present, selected");
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

  @When("{actor} click the {string} if the {string} is {elementState}")
  public void clickIf(String identifier, String targetElement, String elementToCheck, String condition)
    throws Throwable {
    if (doesElementHasCondition(elementToCheck, condition)) {
      actorClickOnTheElement(identifier, targetElement);
    }
  }

  @When("{actor} type {string} into {string} if the {string} is {elementState}")
  public void clickIf(String identifier, String input, String targetElement, String elementToCheck, String condition)
    throws Throwable {
    if (doesElementHasCondition(elementToCheck, condition)) {
      actorTypeValueTo(identifier, input, targetElement);
    }
  }

  @Then("{actor} should see current url {stringValidation} with value {string}")
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
    } else {
      throw new IllegalArgumentException("Unsupported url validation keyword: '" + validation
        + "'. Expected one of: equal, equal ignore case, contains, contains ignore case, "
        + "starts with, ends with, starts with ignore case, ends with ignore case, matches");
    }
  }

  @Then("{actor} hover on the {string}")
  public void hoverElement(String identifier, String element) throws Throwable {
    ActorManager.currentActor()
      .attemptsTo(Hover.on(target(element)));
  }
}
