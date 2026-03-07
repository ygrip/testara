package io.github.ygrip.testara.ui.populator;

import io.github.ygrip.testara.ui.observation.Observation;

/**
 * <p>ResolveThe class.</p>
 *
 * @author yunaz.ramadhan on 12/29/2019
 * @version $Id: $Id
 */
public final class Resolve {

  private Resolve() {

  }

  /**
   * <p>from.</p>
   *
   * @param observation a {@link Observation} object.
   * @return a {@link ElementResolver} object.
   */
  public static ElementResolver from(Observation<?> observation) {
    return new ElementResolver(observation);
  }

}
