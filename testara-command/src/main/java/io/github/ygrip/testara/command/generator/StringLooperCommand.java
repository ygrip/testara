package io.github.ygrip.testara.command.generator;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.github.ygrip.testara.command.model.CommandLogic;
import io.github.ygrip.testara.command.model.CommandTag;
import io.github.ygrip.testara.core.mapper.MapperHelper;
import org.apache.commons.lang3.ObjectUtils;

import java.util.List;

import static io.github.ygrip.testara.command.CommandExecutor.executeCommand;

/**
 * <p>StringLooperCommand class.</p>
 *
 * @author yunaz.ramadhan on 10/4/2019
 * @version $Id: $Id
 */
@CommandTag(command = "loop", overwrite = true, cacheable = true)
public class StringLooperCommand implements CommandLogic<String> {
  private static final String DEFAULT_SEPARATOR = "";
  private static final Integer DEFAULT_OCCURANCES = 1;

  /**
   * <p>loop.</p>
   *
   * @param input      a {@link Object} object.
   * @param separator  a {@link Object} object.
   * @param occurances a {@link Integer} object.
   * @return a {@link String} object.
   */
  public static String loop(Object input, Object separator, Integer occurances) {
    StringBuilder result = new StringBuilder();
    separator = MapperHelper.toString(executeCommand(separator));
    for (int i = 0; i < occurances; i++) {
      result.append(MapperHelper.toString(executeCommand(input)));
      if (i < occurances - 1) {
        if (ObjectUtils.isNotEmpty(separator)) {
          result.append(separator);
        }
      }
    }
    return result.toString();
  }

  /**
   * <p>loop.</p>
   *
   * @param input      a {@link Object} object.
   * @param occurances a {@link Integer} object.
   * @return a {@link String} object.
   * @throws JsonProcessingException if any.
   */
  public static String loop(Object input, Integer occurances) throws JsonProcessingException {
    return loop(input, DEFAULT_SEPARATOR, occurances);
  }

  /**
   * <p>loop.</p>
   *
   * @param input a {@link Object} object.
   * @return a {@link String} object.
   * @throws JsonProcessingException if any.
   */
  public static String loop(Object input) throws JsonProcessingException {
    return loop(input, DEFAULT_SEPARATOR, DEFAULT_OCCURANCES);
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
  public String execute(List<Object> parameters) throws JsonProcessingException {
    if (ObjectUtils.isEmpty(parameters)) {
      return "";
    } else if (parameters.size() == 1) {
      return loop(parameters.get(0));
    } else {
      Object temp = parameters.size() == 2 ? executeCommand(parameters.get(1)) : executeCommand(parameters.get(2));
      Integer occurances = DEFAULT_OCCURANCES;
      try {
        occurances = temp == null ? occurances : Integer.parseInt(temp.toString());
      } catch (Exception ignored) {

      }
      return parameters.size() == 2 ?
          loop(parameters.get(0), occurances) :
          loop(parameters.get(0), parameters.get(1), occurances);
    }
  }
}
