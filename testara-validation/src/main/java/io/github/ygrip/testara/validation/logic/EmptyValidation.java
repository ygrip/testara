package io.github.ygrip.testara.validation.logic;

import io.github.ygrip.testara.validation.model.ValidationTag;
import io.github.ygrip.testara.validation.model.ValidatorLogic;
import io.github.ygrip.testara.core.support.CommonHelper;

/**
 * <p>NoEmptyValidation class.</p>
 *
 * @author yunaz.ramadhan on 18/11/2022
 * @version $Id: $Id
 */
@ValidationTag(command = "EMPTY", alias = {"empty"}, overwrite = true)
public class EmptyValidation extends ValidatorLogic<Object, Boolean> {

  /**
   * {@inheritDoc}
   */
  @Override
  protected String setDefaultMessage() {
    return String.format("Expected data %s be empty", getExpected() ? "should" : "should not");
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean validate() throws Exception {
    return getExpected() == CommonHelper.isBlank(getActual());
  }
}
