package io.github.ygrip.testara.ui.error;

/**
 * <p>UnrecognizedApplicationException class.</p>
 *
 * @author yunaz.ramadhan on 12/23/2019
 * @version $Id: $Id
 */
public class UnrecognizedApplicationException extends RuntimeException {
  /**
   * <p>Constructor for UnrecognizedApplicationException.</p>
   *
   * @param errorMessage a {@link String} object.
   */
  public UnrecognizedApplicationException(String errorMessage){
    super(errorMessage);
  }

  /**
   * <p>Constructor for UnrecognizedApplicationException.</p>
   *
   * @param errorMessage a {@link String} object.
   * @param err a {@link Throwable} object.
   */
  public UnrecognizedApplicationException(String errorMessage,Throwable err){
    super(errorMessage,err);
  }
}
