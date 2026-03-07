package io.github.ygrip.testara.validation.logic;

import io.github.ygrip.testara.validation.model.ValidationTag;
import io.github.ygrip.testara.validation.model.ValidatorLogic;

/**
 * <p>EqualValidation class.</p>
 *
 * @author yunaz.ramadhan on 12/10/2019
 * @version $Id: $Id
 */
@ValidationTag(command = "EQUAL", overwrite = true)
public class EqualValidation extends ValidatorLogic<Object, Object> {

  /** {@inheritDoc} */
  @Override
  protected String setDefaultMessage() {
    return "Actual data is not equal with expected data";
  }

  /** {@inheritDoc} */
  @Override
  public boolean validate() throws Exception {
    return areEqual(getActual(), getExpected());
  }
}
