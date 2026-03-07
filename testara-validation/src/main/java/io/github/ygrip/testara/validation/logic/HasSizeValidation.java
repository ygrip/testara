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
@ValidationTag(command = "HAS_SIZE", alias = {"has size"}, overwrite = true)
public class HasSizeValidation extends ValidatorLogic<Object, Integer> {

  /**
   * {@inheritDoc}
   */
  @Override
  protected String setDefaultMessage() {
    return "List size did not match";
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean validate() throws Exception {
    List<?> collection = constructDataToCollections(getActual());
    return collection.size() == getExpected();
  }
}
