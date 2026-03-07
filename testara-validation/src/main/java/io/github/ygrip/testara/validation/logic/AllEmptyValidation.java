package io.github.ygrip.testara.validation.logic;

import io.github.ygrip.testara.validation.model.ValidationTag;
import io.github.ygrip.testara.validation.model.ValidatorLogic;
import io.github.ygrip.testara.core.support.CommonHelper;

import java.util.List;

/**
 * <p>AllNoEmptyValidation class.</p>
 *
 * @author yunaz.ramadhan on 18/11/2022
 * @version $Id: $Id
 */
@ValidationTag(command = "ALL_EMPTY", alias = {"all empty"}, overwrite = true)
public class AllEmptyValidation extends ValidatorLogic<Object, Boolean> {

  /**
   * {@inheritDoc}
   */
  @Override
  protected String setDefaultMessage() {
    return String.format("Expected data %s contains element",
        getExpected() ? "should" : "should not");
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean validate() throws Exception {
    List<?> collection = constructDataToCollections(getActual());

    return CommonHelper.isBlank(collection) ? getExpected() : collection.stream()
        .allMatch(item -> CommonHelper.isBlank(item) == getExpected());
  }
}
