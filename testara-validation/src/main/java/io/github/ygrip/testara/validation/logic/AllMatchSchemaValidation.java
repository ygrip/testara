package io.github.ygrip.testara.validation.logic;

import io.github.ygrip.testara.core.json.SchemaHelper;
import io.github.ygrip.testara.validation.model.ValidationTag;
import io.github.ygrip.testara.validation.model.ValidatorLogic;

import java.util.ArrayList;
import java.util.List;

import static io.github.ygrip.testara.core.support.CommonHelper.isBlank;

/**
 * <p>AllMatchSchemaValidation class.</p>
 *
 * @author yunaz.ramadhan on 2/21/2020
 * @version $Id: $Id
 */
@ValidationTag(command = "ALL_MATCH_SCHEMA", alias = {"all match schema", "all elements match schema"},
    overwrite = true)
public class AllMatchSchemaValidation extends ValidatorLogic<List<Object>, String> {

  /**
   * {@inheritDoc}
   */
  @Override
  protected String setDefaultMessage() {
    return String.format("Not all data match with schema %s, %s", getExpected(), getAdditionalMessages());
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean validate() throws Exception {
    boolean isValid = true;

    List<Integer> indexes = new ArrayList<>();

    SchemaHelper.Validator schema = SchemaHelper.loadSchema(getExpected());
    for (int i = 0; i < getActual().size(); i++) {
      boolean check = true;
      try {
        schema.validate(getActual().get(i));
      } catch (Exception err) {
        check = false;
        addMessage(err.getMessage());
      }
      if (!check) {
        indexes.add(i);
      }
    }
    if (!isBlank(indexes)) {
      isValid = false;
      addMessage(String.format("There are some difference in data with index(es) : %s", indexes));
    }
    return isValid;
  }
}
