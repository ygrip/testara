package io.github.ygrip.testara.ui.error;

/**
 * <p>PageFailureException class.</p>
 *
 * @author yunaz.ramadhan on 12/23/2019
 * @version $Id: $Id
 */
public class PageFailureException extends RuntimeException {
  /**
   * <p>Constructor for PageFailureException.</p>
   *
   * @param errorMessage a {@link String} object.
   */
  public PageFailureException(String errorMessage) {
    super(errorMessage);
  }

  /**
   * <p>Constructor for PageFailureException.</p>
   *
   * @param errorMessage a {@link String} object.
   * @param err a {@link Throwable} object.
   */
  public PageFailureException(String errorMessage, Throwable err) {
    super(errorMessage, err);
  }
}
