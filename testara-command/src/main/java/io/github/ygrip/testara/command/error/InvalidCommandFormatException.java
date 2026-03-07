package io.github.ygrip.testara.command.error;

/**
 * <p>InvalidCommandFormatException class.</p>
 *
 * @author yunaz.ramadhan on 10/4/2019
 * @version $Id: $Id
 */
public class InvalidCommandFormatException extends RuntimeException {
  /**
   * <p>Constructor for InvalidCommandFormatException.</p>
   *
   * @param errorMessage a {@link String} object.
   * @param err a {@link Throwable} object.
   */
  public InvalidCommandFormatException(String errorMessage, Throwable err) {
    super(errorMessage, err);
  }
  /**
   * <p>Constructor for InvalidCommandFormatException.</p>
   *
   * @param errorMessage a {@link String} object.
   */
  public InvalidCommandFormatException(String errorMessage){
    super(errorMessage);
  }
}
