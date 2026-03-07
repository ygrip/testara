package io.github.ygrip.testara.validation.logic;

import io.github.ygrip.testara.core.json.SchemaHelper;
import io.github.ygrip.testara.validation.model.ValidationTag;
import io.github.ygrip.testara.validation.model.ValidatorLogic;

/**
 * <p>MatchSchemaValidation class.</p>
 *
 * @author yunaz.ramadhan on 12/11/2019
 * @version $Id: $Id
 */
@ValidationTag(command = "MATCH_SCHEMA", alias = {"match schema"}, overwrite = true)
public class MatchSchemaValidation extends ValidatorLogic<Object, String> {

  /**
   * {@inheritDoc}
   */
  @Override
  protected String setDefaultMessage() {
    return String.format("Data not match schema %s, %s", getExpected(), getAdditionalMessages());
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean validate() throws Exception {
    boolean isValid = true;
    try {
      SchemaHelper.loadSchema(getExpected()).validate(getActual());
    } catch (Exception e) {
      isValid = false;
      addMessage(String.format("Found difference : %s", e.getMessage()));
    }
    return isValid;
  }
}
