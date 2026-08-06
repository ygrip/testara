package io.github.ygrip.testara.validation.logic;

import io.github.ygrip.testara.validation.model.ValidationTag;
import io.github.ygrip.testara.validation.model.ValidatorLogic;

@ValidationTag(command = "ASSERTION_ERROR", overwrite = true)
public class AssertionErrorValidation extends ValidatorLogic<Boolean, Boolean> {
  @Override
  protected String setDefaultMessage() {
    return "validator assertion errors must be reported";
  }

  @Override
  public boolean validate() {
    throw new AssertionError("validator assertion failed");
  }
}
