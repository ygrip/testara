package io.github.ygrip.testara.validation.logic;

import io.github.ygrip.testara.validation.model.ValidationTag;
import io.github.ygrip.testara.validation.model.ValidatorLogic;
import io.github.ygrip.testara.core.support.CommonHelper;

import java.util.List;

/**
 * <p>AllNoEmptyValidation class.</p>
 *
 * @author yunaz.ramadhan on 12/9/2019
 * @version $Id: $Id
 */
@ValidationTag(command = "ALL_NOT_EMPTY", alias = {"all not empty"}, overwrite = true)
public class AllNoEmptyValidation extends ValidatorLogic<Object, Boolean> {

  /** {@inheritDoc} */
  @Override
  protected String setDefaultMessage() {
    return String.format("Expected data %s contains empty element",
        getExpected() ? "should not" : "should");
  }

  /** {@inheritDoc} */
  @Override
  public boolean validate() throws Exception {
    List<?> collection = constructDataToCollections(getActual());

    return CommonHelper.isBlank(collection) ? !getExpected() : collection.stream()
        .allMatch(item -> CommonHelper.isBlank(item) != getExpected());
  }
}
