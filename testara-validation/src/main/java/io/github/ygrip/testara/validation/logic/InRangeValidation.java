package io.github.ygrip.testara.validation.logic;

import io.github.ygrip.testara.validation.model.ValidationTag;
import io.github.ygrip.testara.validation.model.ValidatorLogic;

import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>InRangeValidation class.</p>
 *
 * @author yunaz.ramadhan on 12/10/2019
 * @version $Id: $Id
 */
@ValidationTag(command = "IN_RANGE_OF", alias = {"in range of"}, overwrite = true)
public class InRangeValidation extends ValidatorLogic<List<Object>, List<Object>> {

  /** {@inheritDoc} */
  @Override
  protected String setDefaultMessage() {
    return String.format("Data to check is not in range of %s", getExpected());
  }

  /** {@inheritDoc} */
  @Override
  public boolean validate() throws Exception {
    if (getExpected() == null || getExpected().isEmpty() || getActual() == null
        || getActual().isEmpty()) {
      setReason("Data to compare cannot be blank");
      return false;
    }
    List<Object> expectedSort = getExpected().stream().sorted().collect(Collectors.toList());
    List<Object> actualSort = getActual().stream().sorted().collect(Collectors.toList());

    if (expectedSort.size() <= 1) {
      setReason(
          "At least 2 elements from expectation should be provided\nThese 2 elements will be used as lowest range and highest range inclusively");
      return false;
    }

    int lastExpected = expectedSort.size() - 1;
    int lastActual = actualSort.size() - 1;

    return String.valueOf(expectedSort.get(0)).compareTo(String.valueOf(actualSort.get(0))) <= 0 &&
        String.valueOf(expectedSort.get(lastExpected))
            .compareTo(String.valueOf(actualSort.get(lastActual))) >= 0;
  }
}
