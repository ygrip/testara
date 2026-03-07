package io.github.ygrip.testara.ui.validation;

import io.github.ygrip.testara.ui.executor.ActorManager;
import io.github.ygrip.testara.ui.interaction.SeeThat;
import io.github.ygrip.testara.validation.model.ValidationTag;
import io.github.ygrip.testara.validation.model.ValidatorLogic;

@ValidationTag(command = "IS_ENABLED", alias = "is enabled", overwrite = true)
public class ElementEnabledValidation extends ValidatorLogic<String, Boolean> {

  @Override
  protected String setDefaultMessage() {
    return "element " + getActual() + " does " + (getExpected() ? "disabled" : "enabled");
  }

  @Override
  public boolean validate() throws Exception {
    try {
      final var element = getActual();
      final var state = getExpected();
      if (state) {
        ActorManager.currentActor()
          .attemptsTo(SeeThat.enabled(element));
      } else {
        ActorManager.currentActor()
          .attemptsTo(SeeThat.disabled(element));
      }

      return true;
    } catch (Exception ignored) {
      return false;
    }
  }
}
