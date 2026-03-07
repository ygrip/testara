package io.github.ygrip.testara.ui.validation;

import io.github.ygrip.testara.ui.executor.ActorManager;
import io.github.ygrip.testara.ui.interaction.SeeThat;
import io.github.ygrip.testara.validation.model.ValidationTag;
import io.github.ygrip.testara.validation.model.ValidatorLogic;

@ValidationTag(command = "HAS_CLASS", alias = "has class", overwrite = true)
public class ElementHasClassValidation extends ValidatorLogic<String, String> {

  @Override
  protected String setDefaultMessage() {
    return "element " + getActual() + " does not have class " + getExpected();
  }

  @Override
  public boolean validate() throws Exception {
    try {
      final var element = getActual();
      final var className = getExpected();
      ActorManager.currentActor()
        .attemptsTo(SeeThat.hasClass(className)
          .on(element));
      return true;
    } catch (Exception ignored) {
      return false;
    }
  }
}
