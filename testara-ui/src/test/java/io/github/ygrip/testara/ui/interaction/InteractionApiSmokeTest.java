package io.github.ygrip.testara.ui.interaction;

import io.github.ygrip.testara.ui.page.NamedPage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class InteractionApiSmokeTest {

  @Test
  void documentedFluentInteractionFactoriesExist() {
    assertDoesNotThrow(() -> {
      Enter.class.getMethod("text", String.class);
      Enter.Into.class.getMethod("into", String.class);
      Click.class.getMethod("on", String.class);
      Clear.class.getMethod("field", String.class);
      Scroll.class.getMethod("to", String.class);
      Scroll.ScrollTo.class.getMethod("andAlignToTop");
      WaitUntil.class.getMethod("visible", String.class);
      SelectOption.class.getMethod("from", String.class);
      SelectOption.From.class.getMethod("byVisibleText", String.class);
      Navigate.class.getMethod("to", NamedPage.NamedPageContext.class);
      SeeThat.class.getMethod("visible", String.class);
      SeeThat.class.getMethod("containsText", String.class);
      SeeThat.ContainsText.class.getMethod("on", String.class);
      SeeThat.class.getMethod("page", NamedPage.NamedPageContext.class);
    });
  }
}
