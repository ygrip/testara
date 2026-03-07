package io.github.ygrip.testara.validation.logic;

import io.github.ygrip.testara.validation.model.ValidationTag;
import io.github.ygrip.testara.validation.model.ValidatorLogic;

import java.util.List;

/**
 * <p>AllNotEqualValidation class.</p>
 *
 * @author yunaz.ramadhan on 12/11/2019
 * @version $Id: $Id
 */
@ValidationTag(command = "ALL_NOT_EQUAL", alias = {"all not equal",
    "all elements not equal"}, overwrite = true)
public class AllNotEqualValidation extends ValidatorLogic<Object, Object> {

  /**
   * {@inheritDoc}
   */
  @Override
  protected String setDefaultMessage() {
    return "There is actual data that is equal with expected data";
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean validate() throws Exception {
    List<?> collection = constructDataToCollections(getActual());

    if (collection == null || collection.isEmpty()) {
      setReason("Actual data is empty");
      return false;
    }
    int match = 0;
    boolean isError = false;
    for (Object item : collection) {
      try {
        if (!new NotEqualValidation().setActual(item).setExpected(getExpected()).validate()) {
          isError = true;
          break;
        } else {
          match++;
        }
      } catch (Exception ignored) {
        isError = true;
      }
    }

    return !isError && match == collection.size();
  }
}
