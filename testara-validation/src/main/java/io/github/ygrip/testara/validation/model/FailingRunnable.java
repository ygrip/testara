package io.github.ygrip.testara.validation.model;

/**
 * <p>FailingRunnable interface.</p>
 *
 * @author yunaz.ramadhan on 2/15/2020
 * @version $Id: $Id
 */
@FunctionalInterface
public interface FailingRunnable {
  /**
   * <p>run.</p>
   *
   * @throws Exception if any.
   */
  void run() throws Exception;
}
