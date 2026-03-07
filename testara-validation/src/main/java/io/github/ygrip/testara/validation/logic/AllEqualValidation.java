package io.github.ygrip.testara.validation.logic;

import io.github.ygrip.testara.validation.model.ValidationTag;
import io.github.ygrip.testara.validation.model.ValidatorLogic;

import java.util.List;

/**
 * <p>AllEqualValidation class.</p>
 *
 * @author yunaz.ramadhan on 12/11/2019
 * @version $Id: $Id
 */
@ValidationTag(command = "ALL_EQUAL", alias = {"all equal",
    "all elements should equal"}, overwrite = true)
public class AllEqualValidation extends ValidatorLogic<Object, Object> {

  /**
   * {@inheritDoc}
   */
  @Override
  protected String setDefaultMessage() {
    return "All data is not equal";
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
        if (!new EqualValidation().setActual(item).setExpected(getExpected()).validate()) {
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
