package io.github.ygrip.testara.ui.error;

/**
 * <p>DuplicateEngineIdException class.</p>
 *
 * @author yunaz.ramadhan on 12/23/2019
 * @version $Id: $Id
 */
public class DuplicateEngineIdException extends RuntimeException {
  /**
   * <p>Constructor for DuplicateEngineIdException.</p>
   *
   * @param errorMessage a {@link String} object.
   */
  public DuplicateEngineIdException(String errorMessage) {
    super(errorMessage);
  }

  /**
   * <p>Constructor for DuplicateEngineIdException.</p>
   *
   * @param errorMessage a {@link String} object.
   * @param err a {@link Throwable} object.
   */
  public DuplicateEngineIdException(String errorMessage, Throwable err) {
    super(errorMessage, err);
  }
}
