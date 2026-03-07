package io.github.ygrip.testara.validation.logic;

import io.github.ygrip.testara.validation.model.ValidationTag;
import io.github.ygrip.testara.validation.model.ValidatorLogic;

import java.util.List;

/**
 * <p>AllContainsTextValidation class.</p>
 *
 * @author yunaz.ramadhan on 7/22/2024
 * @version $Id: $Id
 */
@ValidationTag(command = "ALL_CONTAINS_TEXT", alias = {"all contains text", "all has text"}, overwrite = true)
public class AllContainsTextValidation extends ValidatorLogic<List<String>, String> {
  private int matched = 0;

  /**
   * {@inheritDoc}
   */
  @Override
  protected String setDefaultMessage() {
    return "Fail to validate data, actual data does not contains text in expected data";
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean validate() throws Exception {
    matched = 0;
    if (getActual() == null || getActual().isEmpty()) {
      setReason("Actual data should not be empty");
      return false;
    } else {
      for (String item : getActual()) {
        if (item.contains(getExpected())) {
          matched++;
        }
      }
      setReason(String.format("%s of %s items contains expected text", matched, getActual().size()));
    }
    return matched == getActual().size();
  }
}
