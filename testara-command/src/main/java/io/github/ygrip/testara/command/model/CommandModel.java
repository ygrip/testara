package io.github.ygrip.testara.command.model;

import io.github.ygrip.testara.command.CommandExecutor;
import io.github.ygrip.testara.core.support.StringHelper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * <p>CommandModel class.</p>
 *
 * @author yunaz.ramadhan on 10/4/2019
 * @version $Id: $Id
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public final class CommandModel {
  private String command;
  private String parentCommand;
  private boolean cacheable;
  private List<Object> parameters;

  /**
   * Add single object parameter to the command object
   *
   * @param param is object data type for the parameter
   * @return instance of CommandModel
   */
  public CommandModel addParameter(Object param) {
    if (null == this.parameters)
      this.parameters = new ArrayList<>();
    this.parameters.add(param);
    return this;
  }

  /**
   * Add list of object parameters to the command object
   *
   * @param params is list of object data type for the parameters
   * @return instance of CommandModel
   */
  public CommandModel addParameters(List<?> params) {
    if (null == this.parameters)
      this.parameters = new ArrayList<>();
    this.parameters.addAll(params);
    return this;
  }

  /**
   * Return string of parameters in command object, each is separated by command separator
   *
   * @return string data type
   */
  public String printParameters() {
    StringJoiner joiner = new StringJoiner(CommandExecutor.defaultSeparator());
    for (Object param : this.parameters) {
      if (param != null) {
        joiner.add(StringHelper.ellipsize(param.toString(), CommandExecutor.maxCharacters()));
      }
    }
    return joiner.toString();
  }

  /**
   * {@inheritDoc}
   *
   * Convert back command object to the unparsed string of command
   */
  @Override
  public String toString() {
    return String.format("%s(%s)", this.command, this.printParameters());
  }
}
