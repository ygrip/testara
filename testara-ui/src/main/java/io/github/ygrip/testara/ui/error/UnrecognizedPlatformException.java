package io.github.ygrip.testara.ui.error;

/**
 * <p>UnrecognizedPlatformException class.</p>
 *
 * @author yunaz.ramadhan on 12/23/2019
 * @version $Id: $Id
 */
public class UnrecognizedPlatformException extends RuntimeException {
  /**
   * <p>Constructor for UnrecognizedPlatformException.</p>
   *
   * @param errorMessage a {@link String} object.
   */
  public UnrecognizedPlatformException(String errorMessage){
    super(errorMessage);
  }

  /**
   * <p>Constructor for UnrecognizedPlatformException.</p>
   *
   * @param errorMessage a {@link String} object.
   * @param err a {@link Throwable} object.
   */
  public UnrecognizedPlatformException(String errorMessage,Throwable err){
    super(errorMessage,err);
  }
}
