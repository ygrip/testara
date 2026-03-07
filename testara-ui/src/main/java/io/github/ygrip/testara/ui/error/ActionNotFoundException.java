package io.github.ygrip.testara.ui.error;

/**
 * <p>ActionNotFoundException class.</p>
 *
 * @author yunaz.ramadhan on 12/23/2019
 * @version $Id: $Id
 */
public class ActionNotFoundException extends RuntimeException {
  /**
   * <p>Constructor for ActionNotFoundException.</p>
   *
   * @param errorMessage a {@link String} object.
   */
  public ActionNotFoundException(String errorMessage){
    super(errorMessage);
  }

  /**
   * <p>Constructor for ActionNotFoundException.</p>
   *
   * @param errorMessage a {@link String} object.
   * @param err a {@link Throwable} object.
   */
  public ActionNotFoundException(String errorMessage,Throwable err){
    super(errorMessage,err);
  }
}
