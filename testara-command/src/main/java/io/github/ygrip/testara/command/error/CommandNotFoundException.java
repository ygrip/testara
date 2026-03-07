package io.github.ygrip.testara.command.error;

/**
 * <p>CommandNotFoundException class.</p>
 *
 * @author yunaz.ramadhan on 10/4/2019
 * @version $Id: $Id
 */
public class CommandNotFoundException extends RuntimeException {
  /**
   * <p>Constructor for CommandNotFoundException.</p>
   *
   * @param errorMessage a {@link String} object.
   * @param err a {@link Throwable} object.
   */
  public CommandNotFoundException(String errorMessage, Throwable err) {
    super(errorMessage, err);
  }

  /**
   * <p>Constructor for CommandNotFoundException.</p>
   *
   * @param errorMessage a {@link String} object.
   */
  public CommandNotFoundException(String errorMessage){
    super(errorMessage);
  }
}
