package io.github.ygrip.testara.validation.logic;

import io.github.ygrip.testara.validation.model.ValidationTag;
import io.github.ygrip.testara.validation.model.ValidatorLogic;
import io.github.ygrip.testara.core.support.CommonHelper;

/**
 * <p>IsUniqueValidation class.</p>
 *
 * @author yunaz.ramadhan on 12/10/2019
 * @version $Id: $Id
 */
@ValidationTag(command = "IS_ARRAY", alias = {"is array"}, overwrite = true)
public class IsArrayValidation extends ValidatorLogic<Object, Boolean> {

  /**
   * {@inheritDoc}
   */
  @Override
  protected String setDefaultMessage() {
    return "Data is not array";
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean validate() throws Exception {
    boolean isValid;
    if (CommonHelper.isBlank(getActual()) || CommonHelper.isBlank(getExpected())) {
      isValid = false;
      setReason("Data to check is not valid");
    } else {
      boolean isArray = isArray(getActual()) || CommonHelper.isCollection(getActual());
      isValid = isArray == getExpected();
    }
    return isValid;
  }
}
