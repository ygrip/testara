package io.github.ygrip.testara.validation.logic;

import io.github.ygrip.testara.validation.model.ValidationTag;
import io.github.ygrip.testara.validation.model.ValidatorLogic;
import io.github.ygrip.testara.core.support.CommonHelper;

import java.util.List;

import static io.github.ygrip.testara.core.support.CommonHelper.isBlank;

/**
 * <p>AllContainsValidation class.</p>
 *
 * @author yunaz.ramadhan on 12/10/2019
 * @version $Id: $Id
 */
@ValidationTag(command = "ALL_CONTAINS", alias = {"all contains"}, overwrite = true)
public class AllContainsValidation extends ValidatorLogic<Object, Object> {

  /** {@inheritDoc} */
  @Override
  protected String setDefaultMessage() {
    return "Some expected data is not found in the actual data";
  }

  /** {@inheritDoc} */
  @Override
  public boolean validate() throws Exception {
    boolean isValid;
    Object actual = getActual();
    Object expected = getExpected();
    if (isBlank(actual) || isBlank(expected) || !CommonHelper.isCollection(actual)) {
      isValid = false;
      setReason("Data to compare is not valid");
    } else {
      List<?> collection = constructDataToCollections(actual);
      Integer match = 0;
      for (Object item : collection) {
        boolean check =
            new ContainsValidation().setActual(item).setExpected(expected).validate();
        if (check) {
          match++;
        }
      }
      isValid = match.equals(collection.size());
      setReason(String.format("%s out of %s match the condition", match, collection.size()));
    }
    return isValid;
  }
}
