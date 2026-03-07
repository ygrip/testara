package io.github.ygrip.testara.validation;

import io.github.ygrip.testara.core.BaseTests;
import io.github.ygrip.testara.core.TestWith;
import io.github.ygrip.testara.core.model.PopulatedTag;
import io.github.ygrip.testara.validation.model.DataValidation;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static io.github.ygrip.testara.validation.support.AssertUtils.assertDoesNotThrow;
import static io.github.ygrip.testara.validation.support.AssertUtils.assertDoesThrow;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.not;

@Tag("validation")
@TestWith(properties = {"classpath:application.properties", "classpath:configuration.properties"})
public class ValidationTests extends BaseTests {

  @Test
  public void populateValidation() {
    List<PopulatedTag> registeredCommandName = ValidatorHelper.getAvailableValidator();
    assertThat(registeredCommandName, not(empty()));
  }

  @Test
  public void emptyValidation() {
    List<Integer> collection = new ArrayList<>();
    DataValidation validation =
        DataValidation.builder().validation("NOT_EMPTY").actual(collection).expectation(false).build();
    assertDoesNotThrow(() -> ValidatorHelper.validate(validation));
    Integer[] arrays = new Integer[] {};
    DataValidation validation2 =
        DataValidation.builder().validation("NOT_EMPTY").actual(arrays).expectation(false).build();
    assertDoesNotThrow(() -> ValidatorHelper.validate(validation2));
  }

  @Test
  public void notEmptyValidation() {
    List<Integer> collection = new ArrayList<>();
    collection.add(30);
    collection.add(40);
    collection.add(50);
    DataValidation validation =
        DataValidation.builder().validation("NOT_EMPTY").actual(collection).expectation(true).build();
    assertDoesNotThrow(() -> ValidatorHelper.validate(validation));
    Integer[] arrays = new Integer[] {20, 10, 50};
    DataValidation validation2 =
        DataValidation.builder().validation("NOT_EMPTY").actual(arrays).expectation(true).build();
    assertDoesNotThrow(() -> ValidatorHelper.validate(validation2));
  }

  @Test
  public void sortedValidation() {
    List<Integer> collection = new ArrayList<>();
    collection.add(30);
    collection.add(40);
    collection.add(50);
    DataValidation validation =
        DataValidation.builder().validation("SORTED").actual(collection).expectation("ASCENDING").build();
    assertDoesNotThrow(() -> ValidatorHelper.validate(validation));
    Integer[] arrays = new Integer[] {20, 10, 5};
    DataValidation validation2 =
        DataValidation.builder().validation("SORTED").actual(arrays).expectation("DESCENDING").build();
    assertDoesNotThrow(() -> ValidatorHelper.validate(validation2));
  }

  @Test
  public void notSortedValidation() {
    List<Integer> collection = new ArrayList<>();
    collection.add(30);
    collection.add(40);
    collection.add(50);
    DataValidation validation =
        DataValidation.builder().validation("NOT_SORTED").actual(collection).expectation("DESCENDING").build();
    assertDoesNotThrow(() -> ValidatorHelper.validate(validation));
    Integer[] arrays = new Integer[] {20, 10, 5};
    DataValidation validation2 =
        DataValidation.builder().validation("NOT_SORTED").actual(arrays).expectation("ASCENDING").build();
    assertDoesNotThrow(() -> ValidatorHelper.validate(validation2));
  }

  @Test
  public void failNotSorted() {
    List<Integer> collection = new ArrayList<>();
    collection.add(30);
    collection.add(40);
    collection.add(50);
    DataValidation validation =
        DataValidation.builder().validation("NOT_SORTED").actual(collection).expectation("ASCENDING").build();
    assertDoesThrow(() -> ValidatorHelper.validate(validation));
    DataValidation validation2 =
        DataValidation.builder().validation("NOT_SORTED").actual("yunaz").expectation("ASCENDING").build();
    assertDoesThrow(() -> ValidatorHelper.validate(validation2));
    DataValidation validation3 =
        DataValidation.builder().validation("NOT_SORTED").actual(collection).expectation("RANDOM").build();
    assertDoesThrow(() -> ValidatorHelper.validate(validation3));
  }

  @Test
  public void failSorted() {
    List<Integer> collection = new ArrayList<>();
    collection.add(30);
    collection.add(40);
    collection.add(50);
    DataValidation validation =
        DataValidation.builder().validation("SORTED").actual(collection).expectation("DESCENDING").build();
    assertDoesThrow(() -> ValidatorHelper.validate(validation));
    DataValidation validation2 =
        DataValidation.builder().validation("SORTED").actual("yunaz").expectation("ASCENDING").build();
    assertDoesThrow(() -> ValidatorHelper.validate(validation2));
    DataValidation validation3 =
        DataValidation.builder().validation("SORTED").actual(collection).expectation("RANDOM").build();
    assertDoesThrow(() -> ValidatorHelper.validate(validation3));
  }

  @Test
  public void greaterThanEqualValidation() {
    List<Integer> collection = new ArrayList<>();
    collection.add(30);
    collection.add(40);
    collection.add(50);
    DataValidation validation =
        DataValidation.builder().validation("GREATER_THAN_EQUAL").actual(collection).expectation(30).build();
    assertDoesNotThrow(() -> ValidatorHelper.validate(validation));
    Integer[] arrays = new Integer[] {20, 10, 50};
    DataValidation validation2 =
        DataValidation.builder().validation("GREATER_THAN_EQUAL").actual(arrays).expectation(9).build();
    assertDoesNotThrow(() -> ValidatorHelper.validate(validation2));
  }

  @Test
  public void lesserThanEqualValidation() {
    List<Integer> collection = new ArrayList<>();
    collection.add(30);
    collection.add(40);
    collection.add(50);
    DataValidation validation =
        DataValidation.builder().validation("LESSER_THAN_EQUAL").actual(collection).expectation(50).build();
    assertDoesNotThrow(() -> ValidatorHelper.validate(validation));
    Integer[] arrays = new Integer[] {20, 10, 50};
    DataValidation validation2 =
        DataValidation.builder().validation("LESSER_THAN_EQUAL").actual(arrays).expectation(51).build();
    assertDoesNotThrow(() -> ValidatorHelper.validate(validation2));
  }

  @Test
  public void allEqualValidation() {
    List<Boolean> collection = new ArrayList<>();
    collection.add(true);
    collection.add(true);
    collection.add(true);
    DataValidation validation =
        DataValidation.builder().validation("ALL_EQUAL").actual(collection).expectation(true).build();
    assertDoesNotThrow(() -> ValidatorHelper.validate(validation));
    Boolean[] arrays = new Boolean[] {true, true, true};
    DataValidation validation2 =
        DataValidation.builder().validation("ALL_EQUAL").actual(arrays).expectation(true).build();
    assertDoesNotThrow(() -> ValidatorHelper.validate(validation2));
  }

  @Test
  public void allNotEqualValidation() {
    List<Boolean> collection = new ArrayList<>();
    collection.add(false);
    collection.add(false);
    collection.add(false);
    DataValidation validation =
        DataValidation.builder().validation("ALL_NOT_EQUAL").actual(collection).expectation(true).build();
    assertDoesNotThrow(() -> ValidatorHelper.validate(validation));
    Boolean[] arrays = new Boolean[] {false, false, false};
    DataValidation validation2 =
        DataValidation.builder().validation("ALL_NOT_EQUAL").actual(arrays).expectation(true).build();
    assertDoesNotThrow(() -> ValidatorHelper.validate(validation2));
  }

  @Test
  public void failOnAllEqualValidation() {
    List<Boolean> collection = new ArrayList<>();
    collection.add(true);
    collection.add(false);
    collection.add(true);
    DataValidation validation =
        DataValidation.builder().validation("ALL_EQUAL").actual(collection).expectation(true).build();
    assertDoesThrow(() -> ValidatorHelper.validate(validation));
    Boolean[] arrays = new Boolean[] {true, true, false};
    DataValidation validation2 =
        DataValidation.builder().validation("ALL_EQUAL").actual(arrays).expectation(true).build();
    assertDoesThrow(() -> ValidatorHelper.validate(validation2));
  }

  @Test
  public void unknownValidation() {
    DataValidation validation = DataValidation.builder().validation("yunaz").actual(true).expectation(true).build();
    assertDoesThrow(() -> ValidatorHelper.validate(validation));
  }
}
