package io.github.ygrip.testara.cucumber.steps;

import io.github.ygrip.testara.core.context.TestComponent;
import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.core.mapper.MapperHelper;
import io.github.ygrip.testara.core.model.RetryableMethod;
import io.github.ygrip.testara.core.registry.RegistryScope;
import io.github.ygrip.testara.core.transformer.TransformerService;
import io.github.ygrip.testara.validation.ValidatorHelper;
import io.github.ygrip.testara.validation.model.DataValidation;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Then;
import org.hamcrest.Matchers;

import java.util.List;

import static io.github.ygrip.testara.core.support.CommonHelper.isBlank;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.matchesPattern;

@TestComponent(scope = RegistryScope.TEST)
public class GenericValidationSteps {

  @RetryableMethod
  @Then("{actor} see that")
  public void seeThat(String identifier, DataTable table) {
    List<DataValidation> validations = new TransformerService().sourceData(table.cells()).toList(DataValidation.class);
    ValidatorHelper.validates(validations);
  }

  @RetryableMethod
  @Then("{actor} see that {string}")
  public void seeThat(String identifier, String validationPath) {
    ValidatorHelper.validate(validationPath);
  }

  @RetryableMethod
  @Then("{actor} do these validations")
  public void doTheseValidation(String identifier, DataTable table) {
    List<DataValidation> validations = new TransformerService().sourceData(table.cells()).toList(DataValidation.class);
    ValidatorHelper.validates(validations);
  }

  @RetryableMethod
  @Then("{actor} validate that {string}")
  public void doValidateThat(String identifier, String validationPath) {
    ValidatorHelper.validate(validationPath);
  }

  @RetryableMethod
  @Then("{actor} response data {string} {shouldOrShouldNot} be empty")
  public void responseDataShouldNotBeEmpty(String identifier, String responseData, String beOrNotBe) {
    boolean actual =
        beOrNotBe.equalsIgnoreCase("should") == isBlank(TestFramework.context().converter().convert(responseData));
    assertThat(String.format("%s %s be empty", responseData, beOrNotBe), actual, equalTo(true));
  }

  @RetryableMethod
  @Then("{actor} data {string} {shouldOrShouldNot} equal with data {string}")
  public void dataShouldEqualTo(String identifier, String dataA, String shouldOrShouldNot, String dataB) {
    Object actual = TestFramework.context().converter().convert(dataA);
    Object expected = TestFramework.context().converter().convert(dataB);

    assertThat(String.format("%s %s equal with %s", actual, shouldOrShouldNot, expected),
        actual,
        shouldOrShouldNot.equalsIgnoreCase("should") ? equalTo(expected) : Matchers.not(expected));
  }

  @RetryableMethod
  @Then("{actor} data {string} {shouldOrShouldNot} match pattern {string}")
  public void dataShouldMatchWithPattern(String identifier, String data, String shouldOrShouldNot, String regex) {
    String actual = MapperHelper.toString(TestFramework.context().converter().convert(data));
    assertThat(String.format("%s %s satisfy pattern %s", actual, shouldOrShouldNot, regex),
        actual,
        shouldOrShouldNot.equalsIgnoreCase("should") ? matchesPattern(regex) : Matchers.not(matchesPattern(regex)));
  }

  @RetryableMethod
  @Then("{actor} all data in {string} {shouldOrShouldNot} equal with data {string}")
  public void allElementShouldEqualTo(String identifier, String dataA, String shouldOrShouldNot, String dataB)
      throws AssertionError {
    Object actual = TestFramework.context().converter().convert(dataA);
    Object expected = TestFramework.context().converter().convert(dataB);

    DataValidation validation = DataValidation.builder()
        .actual(actual)
        .expectation(expected)
        .validation(shouldOrShouldNot.equalsIgnoreCase("should") ? "ALL_EQUAL" : "ALL_NOT_EQUAL")
        .build();
    ValidatorHelper.validate(validation);
  }

  @RetryableMethod
  @Then("{actor} data {string} {shouldOrShouldNot} be {greaterOrLess} {thanOrEqual} data {string}")
  public void compareTwoData(String identifier,
      String dataA,
      String shouldOrShouldNot,
      String greaterOrLess,
      String thanOrThanEqual,
      String dataB) throws AssertionError {
    Object actual = TestFramework.context().converter().convert(dataA);
    Object expected = TestFramework.context().converter().convert(dataB);
    String validationMode;
    if (greaterOrLess.equalsIgnoreCase("less")) {
      if (thanOrThanEqual.equalsIgnoreCase("than")) {
        validationMode = shouldOrShouldNot.equalsIgnoreCase("should") ? "LESSER_THAN" : "NOT_LESSER_THAN";
      } else {
        validationMode = shouldOrShouldNot.equalsIgnoreCase("should") ? "LESSER_THAN_EQUAL" : "NOT_LESSER_THAN_EQUAL";
      }
    } else {
      if (thanOrThanEqual.equalsIgnoreCase("than")) {
        validationMode = shouldOrShouldNot.equalsIgnoreCase("should") ? "GREATER_THAN" : "NOT_GREATER_THAN";
      } else {
        validationMode = shouldOrShouldNot.equalsIgnoreCase("should") ? "GREATER_THAN_EQUAL" : "NOT_GREATER_THAN_EQUAL";
      }
    }

    DataValidation validation =
        DataValidation.builder().actual(actual).expectation(expected).validation(validationMode).build();
    ValidatorHelper.validate(validation);
  }

  @RetryableMethod
  @Then("{actor} data {string} {shouldOrShouldNot} be ordered by {ascendingOrDescending}")
  public void dataShouldBeOrderedIn(String identifier, String data, String shouldOrShouldNot, String mode)
      throws AssertionError {
    Object actual = TestFramework.context().converter().convert(data);

    DataValidation validation = DataValidation.builder()
        .actual(actual)
        .expectation(mode)
        .validation(shouldOrShouldNot.equalsIgnoreCase("should") ? "SORTED" : "NOT_SORTED")
        .build();
    ValidatorHelper.validate(validation);
  }

  @RetryableMethod
  @Then("{actor} data {string} {shouldOrShouldNot} contains {string}")
  public void dataShouldContains(String identifier, String targetData, String shouldOrShouldNot, String dataInside)
      throws AssertionError {
    Object actual = TestFramework.context().converter().convert(targetData);
    Object expected = TestFramework.context().converter().convert(dataInside);

    DataValidation validation = DataValidation.builder()
        .actual(actual)
        .expectation(expected)
        .validation(shouldOrShouldNot.equalsIgnoreCase("should") ? "CONTAINS" : "NOT_CONTAINS")
        .build();
    ValidatorHelper.validate(validation);
  }
}
