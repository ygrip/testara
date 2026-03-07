package io.github.ygrip.testara.ui.error;

/**
 * <p>PageNotFoundException class.</p>
 *
 * @author yunaz.ramadhan on 12/23/2019
 * @version $Id: $Id
 */
public class PageNotFoundException extends RuntimeException {
  /**
   * <p>Constructor for PageNotFoundException.</p>
   *
   * @param errorMessage a {@link String} object.
   */
  public PageNotFoundException(String errorMessage) {
    super(errorMessage);
  }

  /**
   * <p>Constructor for PageNotFoundException.</p>
   *
   * @param errorMessage a {@link String} object.
   * @param err a {@link Throwable} object.
   */
  public PageNotFoundException(String errorMessage, Throwable err) {
    super(errorMessage, err);
  }
}
