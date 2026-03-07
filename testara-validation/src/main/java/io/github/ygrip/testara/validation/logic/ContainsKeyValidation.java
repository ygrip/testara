package io.github.ygrip.testara.validation.logic;

import io.github.ygrip.testara.validation.model.ValidationTag;
import io.github.ygrip.testara.validation.model.ValidatorLogic;

import java.util.Map;

/**
 * <p>AllEqualValidation class.</p>
 *
 * @author yunaz.ramadhan on 12/11/2019
 * @version $Id: $Id
 */
@ValidationTag(command = "CONTAINS_KEY", alias = {"contains key"}, overwrite = true)
public class ContainsKeyValidation extends ValidatorLogic<Map, String> {

  /**
   * {@inheritDoc}
   */
  @Override
  protected String setDefaultMessage() {
    return "Map object does not contains expected keys";
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean validate() throws Exception {
    return getActual().containsKey(getExpected());
  }
}
