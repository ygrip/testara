package io.github.ygrip.testara.validation.logic;

import io.github.ygrip.testara.validation.model.ValidationTag;
import io.github.ygrip.testara.validation.model.ValidatorLogic;

/**
 * <p>ContainsTextValidation class.</p>
 *
 * @author yunaz.ramadhan on 6/7/2020
 * @version $Id: $Id
 */
@ValidationTag(command = "STARTS_WITH", alias = {"starts with"}, overwrite = true)
public class StartsWithValidation extends ValidatorLogic<String, String> {

  /** {@inheritDoc} */
  @Override
  protected String setDefaultMessage() {
    return "Fail to validate data, actual data does not starts with expected data";
  }

  /** {@inheritDoc} */
  @Override
  public boolean validate() throws Exception {
    return getActual().startsWith(getExpected());
  }
}
