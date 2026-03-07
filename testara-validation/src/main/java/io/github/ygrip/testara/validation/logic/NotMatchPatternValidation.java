package io.github.ygrip.testara.validation.logic;

import io.github.ygrip.testara.validation.model.ValidationTag;
import io.github.ygrip.testara.validation.model.ValidatorLogic;

/**
 * <p>NotMatchPatternValidation class.</p>
 *
 * @author yunaz.ramadhan on 12/11/2019
 * @version $Id: $Id
 */
@ValidationTag(command = "NOT_MATCH_PATTERN", alias = {"not match pattern",
    "not match regex"}, overwrite = true)
public class NotMatchPatternValidation extends ValidatorLogic<Object, String> {

  /**
   * {@inheritDoc}
   */
  @Override
  protected String setDefaultMessage() {
    return String.format("Data still match pattern %s, %s", getExpected(), getAdditionalMessages());
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean validate() throws Exception {
    return !new MatchPatternValidation().setActual(getActual())
        .setExpected(getExpected())
        .validate();
  }
}
