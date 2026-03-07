package io.github.ygrip.testara.command.model;

import java.util.List;

/**
 * <p>CommandLogic interface.</p>
 *
 * @author yunaz.ramadhan on 10/4/2019
 * @version $Id: $Id
 */
public interface CommandLogic<T> {
  /**
   * Indicate whether the command will process its parameter first or not
   * if it is set to true then, command will try parsing and executing its parameters first
   * before proceeding to its own command logic
   *
   * @return boolean data type
   */
  boolean preProcessParameters();

  /**
   * Method that holds logic to the command object
   *
   * @param parameters is list of object that is stated as parameter
   * @return generic object data type
   * @throws Exception when there are failure during method execution
   */
  T execute(List<Object> parameters) throws Exception;

  /**
   * Method that will return command info, info or metadata is build from the CommandTag annotation used by the CommandLogic instance
   *
   * @return default command info
   */
  default CommandInfo info() {
    return new CommandInfo(this.getClass());
  }
}
