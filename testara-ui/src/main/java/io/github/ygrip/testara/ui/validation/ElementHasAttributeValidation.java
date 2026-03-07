package io.github.ygrip.testara.ui.validation;

import io.github.ygrip.testara.ui.executor.ActorManager;
import io.github.ygrip.testara.ui.interaction.SeeThat;
import io.github.ygrip.testara.validation.model.ValidationTag;
import io.github.ygrip.testara.validation.model.ValidatorLogic;

@ValidationTag(command = "HAS_ATTRIBUTE", alias = "has attribute", overwrite = true)
public class ElementHasAttributeValidation extends ValidatorLogic<String, String> {

  @Override
  protected String setDefaultMessage() {
    return "element " + getActual() + " does not have attribute " + getExpected();
  }

  @Override
  public boolean validate() throws Exception {
    try {
      final var element = getActual();
      final var attribute = getExpected();
      ActorManager.currentActor()
        .attemptsTo(SeeThat.attribute(attribute)
          .on(element));
      return true;
    } catch (Exception ignored) {
      return false;
    }
  }
}
