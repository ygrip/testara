package io.github.ygrip.testara.ui.interaction;

import org.apache.commons.lang3.ObjectUtils;

import io.github.ygrip.testara.ui.driver.DriverSession;
import io.github.ygrip.testara.ui.error.SessionMismatchException;
import io.github.ygrip.testara.ui.page.Element;

@FunctionalInterface
public interface Interaction {
  void perform(InteractionContext context);

  default Interaction root(Element root) {
    return this;
  }

  default void support(DriverSession<?> session) throws SessionMismatchException {
    if(ObjectUtils.isEmpty(session)){
      throw new SessionMismatchException("#Interaction expect a valid session");
    }
  }

  default String description() {
    return getClass().getSimpleName();
  }
}
