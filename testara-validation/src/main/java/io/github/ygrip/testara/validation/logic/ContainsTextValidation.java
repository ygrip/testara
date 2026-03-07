package io.github.ygrip.testara.validation.logic;

import io.github.ygrip.testara.validation.model.ValidationTag;
import io.github.ygrip.testara.validation.model.ValidatorLogic;

/**
 * <p>ContainsTextValidation class.</p>
 *
 * @author yunaz.ramadhan on 6/7/2020
 * @version $Id: $Id
 */
@ValidationTag(command = "CONTAINS_TEXT", alias = {"contains text", "has text"}, overwrite = true)
public class ContainsTextValidation extends ValidatorLogic<String, String> {

  /** {@inheritDoc} */
  @Override
  protected String setDefaultMessage() {
    return "Fail to validate data, actual data does not contains text in expected data";
  }

  /** {@inheritDoc} */
  @Override
  public boolean validate() throws Exception {
    return getActual().contains(getExpected());
  }
}
