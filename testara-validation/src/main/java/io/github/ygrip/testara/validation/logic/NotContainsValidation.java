package io.github.ygrip.testara.validation.logic;

import io.github.ygrip.testara.validation.model.ValidationTag;
import io.github.ygrip.testara.validation.model.ValidatorLogic;

/**
 * <p>NotContainsValidation class.</p>
 *
 * @author yunaz.ramadhan on 12/10/2019
 * @version $Id: $Id
 */
@ValidationTag(command = "NOT_CONTAINS", alias = {"not contains"}, overwrite = true)
public class NotContainsValidation extends ValidatorLogic<Object, Object> {

  /**
   * {@inheritDoc}
   */
  @Override
  protected String setDefaultMessage() {
    return "Actual data contains expected data";
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean validate() throws Exception {
    return !new ContainsValidation().setActual(getActual()).setExpected(getExpected()).validate();
  }
}
