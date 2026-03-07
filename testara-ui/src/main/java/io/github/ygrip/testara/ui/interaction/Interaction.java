package io.github.ygrip.testara.ui.interaction;

import org.apache.commons.lang3.ObjectUtils;

import io.github.ygrip.testara.ui.driver.DriverSession;
import io.github.ygrip.testara.ui.error.SessionMismatchException;
import io.github.ygrip.testara.ui.page.Element;

public interface Interaction {
  void perform(InteractionContext context);

  Interaction root(Element root);

  default void support(DriverSession<?> session) throws SessionMismatchException {
    if(ObjectUtils.isEmpty(session)){
      throw new SessionMismatchException("#Interaction expect a valid session");
    }
  }
}
