package io.github.ygrip.testara.validation.logic;

import io.github.ygrip.testara.validation.model.ValidationTag;
import io.github.ygrip.testara.validation.model.ValidatorLogic;

/**
 * <p>NotSortedValidation class.</p>
 *
 * @author yunaz.ramadhan on 12/10/2019
 * @version $Id: $Id
 */
@ValidationTag(command = "NOT_SORTED", alias = {"not sorted"}, overwrite = true)
public class NotSortedValidation extends ValidatorLogic<Object, String> {

  /**
   * {@inheritDoc}
   */
  @Override
  protected String setDefaultMessage() {
    return String.format("Data is sorted by %s", getExpected());
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean validate() throws Exception {
    return !new SortedValidation().setActual(getActual()).setExpected(getExpected()).validate();
  }
}
