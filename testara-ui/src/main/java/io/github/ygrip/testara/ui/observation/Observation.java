package io.github.ygrip.testara.ui.observation;

import org.apache.commons.lang3.ObjectUtils;

import io.github.ygrip.testara.ui.driver.DriverSession;
import io.github.ygrip.testara.ui.error.SessionMismatchException;
import io.github.ygrip.testara.ui.interaction.InteractionContext;
import io.github.ygrip.testara.ui.page.Element;

@FunctionalInterface
public interface Observation<T> {
  default Observation<T> root(Element root) {
    return this;
  }

  T perform(InteractionContext context);

  default void support(DriverSession<?> session) throws SessionMismatchException {
    if(ObjectUtils.isEmpty(session)){
      throw new SessionMismatchException("#Observation expect a valid session");
    }
  }

  default String description() {
    return getClass().getSimpleName();
  }
}
