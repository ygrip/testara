package io.github.ygrip.testara.ui.error;

/**
 * <p>UnrecognizedProxyException class.</p>
 *
 * @author yunaz.ramadhan on 12/23/2019
 * @version $Id: $Id
 */
public class UnrecognizedProxyException extends RuntimeException {
  /**
   * <p>Constructor for UnrecognizedProxyException.</p>
   *
   * @param errorMessage a {@link String} object.
   */
  public UnrecognizedProxyException(String errorMessage){
    super(errorMessage);
  }

  /**
   * <p>Constructor for UnrecognizedProxyException.</p>
   *
   * @param errorMessage a {@link String} object.
   * @param err a {@link Throwable} object.
   */
  public UnrecognizedProxyException(String errorMessage,Throwable err){
    super(errorMessage,err);
  }
}
