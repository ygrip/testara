package io.github.ygrip.testara.command.generator;

import io.github.ygrip.testara.command.error.CommandNotFoundException;
import io.github.ygrip.testara.command.model.CommandLogic;
import io.github.ygrip.testara.command.model.CommandModel;
import io.github.ygrip.testara.command.model.CommandTag;
import io.github.ygrip.testara.core.support.CommonHelper;
import org.apache.commons.lang3.ObjectUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.StringJoiner;

import static io.github.ygrip.testara.command.CommandExecutor.bulkExecuteCommand;
import static io.github.ygrip.testara.command.CommandExecutor.executeCommand;

/**
 * <p>CombineCommand class.</p>
 *
 * @author yunaz.ramadhan on 10/4/2019
 * @version $Id: $Id
 */
@CommandTag(command = "combine", alias = "", overwrite = true, cacheable = true)
public class CombineCommand implements CommandLogic<String> {
  private static String combine(List<Object> parameters, String delimiter) {
    StringJoiner joiner = new StringJoiner(delimiter);
    String result = "";
    if (ObjectUtils.isNotEmpty(parameters)) {
      for (Object param : parameters) {
        joiner.add(param == null ? "" : param.toString());
      }
      result = joiner.toString();
    }
    return result;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean preProcessParameters() {
    return false;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String execute(List<Object> parameters) {
    String delimiter = "";
    List<Object> parsed = new ArrayList<>();
    for (Object param : parameters) {
      if (param instanceof CommandModel command) {
        if (command.getCommand().trim().equalsIgnoreCase("delimiter")) {
          Object newDelimiter = command.getParameters().get(0);
          delimiter = newDelimiter == null ? "" : newDelimiter.toString();
        } else {
          Object temp;
          try {
            temp = executeCommand(command);
          } catch (CommandNotFoundException e) {
            command.setParameters(Collections.singletonList(combine(bulkExecuteCommand(command.getParameters()), "")));
            temp = command.toString();
          }
          parsed.add(temp);
        }
      } else {
        if (!CommonHelper.isBlank(param, false)) {
          parsed.add(param);
        }
      }
    }
    return combine(parsed, delimiter);
  }
}
