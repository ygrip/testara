package io.github.ygrip.testara.validation.logic;

import io.github.ygrip.testara.validation.model.ValidationTag;
import io.github.ygrip.testara.validation.model.ValidatorLogic;

import java.util.List;

/**
 * <p>GreaterThanValidation class.</p>
 *
 * @author yunaz.ramadhan on 12/11/2019
 * @version $Id: $Id
 */
@ValidationTag(command = "GREATER_THAN", alias = {"greater than", ">"}, overwrite = true)
public class GreaterThanValidation extends ValidatorLogic<Object, Object> {

  /** {@inheritDoc} */
  @Override
  protected String setDefaultMessage() {
    return "Actual data is not greater than expected data in value";
  }

  /** {@inheritDoc} */
  @Override
  @SuppressWarnings({"rawtypes", "unchecked"})
  public boolean validate() throws Exception {
    if (getActual() == null || getExpected() == null) {
      return false;
    } else {
      boolean isValid = true;
      List<?> collection = constructDataToCollections(getActual());

      for (Object item : collection) {
        if (item == null) {
          isValid = false;
          break;
        }
        if (item instanceof Comparable && getExpected() instanceof Comparable) {
          isValid = ((Comparable) item).compareTo(getExpected()) > 0;
        } else {
          isValid = item.toString().compareTo(getExpected().toString()) > 0;
        }
        if (!isValid) {
          break;
        }
      }
      return isValid;
    }
  }
}
