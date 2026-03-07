package io.github.ygrip.testara.validation.logic;

import io.github.ygrip.testara.validation.model.ValidationTag;
import io.github.ygrip.testara.validation.model.ValidatorLogic;

/**
 * <p>NotEqualValidation class.</p>
 *
 * @author yunaz.ramadhan on 12/10/2019
 * @version $Id: $Id
 */
@ValidationTag(command = "NOT_EQUAL", alias = {"not equal"}, overwrite = true)
public class NotEqualValidation extends ValidatorLogic<Object, Object> {

  /** {@inheritDoc} */
  @Override
  protected String setDefaultMessage() {
    return "Actual data is equal with expected data";
  }

  /** {@inheritDoc} */
  @Override
  public boolean validate() throws Exception {
    return !new EqualValidation().setActual(getActual()).setExpected(getExpected()).validate();
  }
}
