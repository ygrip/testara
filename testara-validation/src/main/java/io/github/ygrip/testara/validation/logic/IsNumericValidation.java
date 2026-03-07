package io.github.ygrip.testara.validation.logic;

import io.github.ygrip.testara.core.support.NumberHelper;
import io.github.ygrip.testara.validation.model.ValidationTag;
import io.github.ygrip.testara.validation.model.ValidatorLogic;
import io.github.ygrip.testara.core.support.CommonHelper;

import java.util.List;

/**
 * <p>IsNumericValidation class.</p>
 *
 * @author yunaz.ramadhan on 7/22/2024
 * @version $Id: $Id
 */
@ValidationTag(command = "IS_NUMERIC", alias = {"is numeric"}, overwrite = true)
public class IsNumericValidation extends ValidatorLogic<List<String>, Boolean> {
  private int matched = 0;

  /**
   * {@inheritDoc}
   */
  @Override
  protected String setDefaultMessage() {
    return "Data is not numeric";
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean validate() throws Exception {
    matched = 0;
    if (CommonHelper.isBlank(getActual())) {
      setReason("Data to check is not valid");
      return false;
    } else {
      for (String item : getActual()) {
        if (NumberHelper.isNumeric(item)) {
          matched++;
        }
      }
      setReason(String.format("%s of %s items is numeric", matched, getActual().size()));
    }
    return matched == getActual().size();
  }
}
