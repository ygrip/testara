package io.github.ygrip.testara.ui.validation;

import io.github.ygrip.testara.ui.executor.ActorManager;
import io.github.ygrip.testara.ui.interaction.SeeThat;
import io.github.ygrip.testara.validation.model.ValidationTag;
import io.github.ygrip.testara.validation.model.ValidatorLogic;

@ValidationTag(command = "IS_VISIBLE", alias = "is visible", overwrite = true)
public class ElementVisibilityValidation extends ValidatorLogic<String, Boolean> {

  @Override
  protected String setDefaultMessage() {
    return "element " + getActual() + " does " + (getExpected() ? "not visible" : "hidden");
  }

  @Override
  public boolean validate() throws Exception {
    try {
      final var element = getActual();
      final var state = getExpected();
      if (state) {
        ActorManager.currentActor()
          .attemptsTo(SeeThat.visible(element));
      } else {
        ActorManager.currentActor()
          .attemptsTo(SeeThat.hidden(element));
      }

      return true;
    } catch (Exception err) {
      err.printStackTrace();
      return false;
    }
  }
}
