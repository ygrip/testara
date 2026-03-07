package io.github.ygrip.testara.validation.logic;

import io.github.ygrip.testara.validation.model.ValidationTag;
import io.github.ygrip.testara.validation.model.ValidatorLogic;
import io.github.ygrip.testara.core.support.CommonHelper;
import org.assertj.core.util.Sets;

import java.util.List;
import java.util.Set;

/**
 * <p>IsUniqueValidation class.</p>
 *
 * @author yunaz.ramadhan on 12/10/2019
 * @version $Id: $Id
 */
@ValidationTag(command = "IS_UNIQUE", alias = {"is unique"}, overwrite = true)
public class IsUniqueValidation extends ValidatorLogic<Object, Boolean> {

  /** {@inheritDoc} */
  @Override
  protected String setDefaultMessage() {
    return "Data still contains duplicate";
  }

  /** {@inheritDoc} */
  @Override
  public boolean validate() throws Exception {
    boolean isValid;
    if (CommonHelper.isBlank(getActual()) || CommonHelper.isBlank(getExpected())) {
      isValid = false;
      setReason("Data to check is not valid");
    } else {
      List<?> collection = constructDataToCollections(getActual());
      Set<Object> unique = Sets.newHashSet(collection);
      int diff = collection.size() - unique.size();
      isValid = diff == 0 == getExpected();
      if (getExpected()) {
        setReason(String.format("found %s duplicate data", diff));
      } else {
        setReason("Data is unique");
      }
    }
    return isValid;
  }
}
