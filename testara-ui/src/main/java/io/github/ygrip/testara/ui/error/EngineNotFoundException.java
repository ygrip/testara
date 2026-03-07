package io.github.ygrip.testara.ui.error;

/**
 * <p>EngineNotFoundException class.</p>
 *
 * @author yunaz.ramadhan on 12/23/2019
 * @version $Id: $Id
 */
public class EngineNotFoundException extends RuntimeException {
  /**
   * <p>Constructor for EngineNotFoundException.</p>
   *
   * @param errorMessage a {@link String} object.
   */
  public EngineNotFoundException(String errorMessage) {
    super(errorMessage);
  }

  /**
   * <p>Constructor for EngineNotFoundException.</p>
   *
   * @param errorMessage a {@link String} object.
   * @param err a {@link Throwable} object.
   */
  public EngineNotFoundException(String errorMessage, Throwable err) {
    super(errorMessage, err);
  }
}
