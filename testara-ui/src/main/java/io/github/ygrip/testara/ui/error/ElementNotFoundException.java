package io.github.ygrip.testara.ui.error;

/**
 * <p>ElementNotFoundException class.</p>
 *
 * @author yunaz.ramadhan on 12/25/2019
 * @version $Id: $Id
 */
public class ElementNotFoundException extends RuntimeException {
  /**
   * <p>Constructor for ElementNotFoundException.</p>
   *
   * @param errorMessage a {@link String} object.
   */
  public ElementNotFoundException(String errorMessage){
    super(errorMessage);
  }

  /**
   * <p>Constructor for ElementNotFoundException.</p>
   *
   * @param errorMessage a {@link String} object.
   * @param err a {@link Throwable} object.
   */
  public ElementNotFoundException(String errorMessage,Throwable err){
    super(errorMessage,err);
  }
}
