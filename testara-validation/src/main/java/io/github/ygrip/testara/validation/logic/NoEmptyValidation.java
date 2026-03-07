package io.github.ygrip.testara.validation.logic;

import io.github.ygrip.testara.validation.model.ValidationTag;
import io.github.ygrip.testara.validation.model.ValidatorLogic;
import io.github.ygrip.testara.core.support.CommonHelper;

/**
 * <p>NoEmptyValidation class.</p>
 *
 * @author yunaz.ramadhan on 12/9/2019
 * @version $Id: $Id
 */
@ValidationTag(command = "NOT_EMPTY", alias = {"not empty"}, overwrite = true)
public class NoEmptyValidation extends ValidatorLogic<Object, Boolean> {

  /** {@inheritDoc} */
  @Override
  protected String setDefaultMessage() {
    return String.format("Expected data %s be empty", getExpected() ? "should not" : "should");
  }

  /** {@inheritDoc} */
  @Override
  public boolean validate() throws Exception {
    return getExpected() == !CommonHelper.isBlank(getActual());
  }
}
