package io.github.ygrip.testara.validation.logic;

import io.github.ygrip.testara.validation.model.ValidationTag;
import io.github.ygrip.testara.validation.model.ValidatorLogic;

/**
 * <p>NotAllContainsValidation class.</p>
 *
 * @author yunaz.ramadhan on 12/10/2019
 * @version $Id: $Id
 */
@ValidationTag(command = "NOT_ALL_CONTAINS", alias = {"not all contains"}, overwrite = true)
public class NotAllContainsValidation extends ValidatorLogic<Object, Object> {

  /** {@inheritDoc} */
  @Override
  protected String setDefaultMessage() {
    return "Some expected data is not found in the actual data";
  }

  /** {@inheritDoc} */
  @Override
  public boolean validate() throws Exception {
    return !new AllContainsValidation().setActual(getActual()).setExpected(getExpected()).validate();
  }
}
