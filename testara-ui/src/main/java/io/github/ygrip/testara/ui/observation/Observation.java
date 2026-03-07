package io.github.ygrip.testara.ui.observation;

import org.apache.commons.lang3.ObjectUtils;

import io.github.ygrip.testara.ui.driver.DriverSession;
import io.github.ygrip.testara.ui.error.SessionMismatchException;
import io.github.ygrip.testara.ui.interaction.InteractionContext;
import io.github.ygrip.testara.ui.page.Element;

public interface Observation<T> {
  Observation<T> root(Element root);

  T perform(InteractionContext context);

  default void support(DriverSession<?> session) throws SessionMismatchException {
    if(ObjectUtils.isEmpty(session)){
      throw new SessionMismatchException("#Observation expect a valid session");
    }
  }
}
