package io.github.ygrip.testara.validation.logic;

import io.github.ygrip.testara.validation.model.ValidationTag;
import io.github.ygrip.testara.validation.model.ValidatorLogic;

import java.util.List;

/**
 * <p>InRangeValidation class.</p>
 *
 * @author yunaz.ramadhan on 12/10/2019
 * @version $Id: $Id
 */
@ValidationTag(command = "OUT_RANGE_OF", alias = {"out range of"}, overwrite = true)
public class OutOfRangeValidation extends ValidatorLogic<List<Object>, List<Object>> {

  /** {@inheritDoc} */
  @Override
  protected String setDefaultMessage() {
    return String.format("Data to check is not in range of %s", getExpected());
  }

  /** {@inheritDoc} */
  @Override
  public boolean validate() throws Exception {
    return !new InRangeValidation().setActual(getActual()).setExpected(getExpected()).validate();
  }
}
