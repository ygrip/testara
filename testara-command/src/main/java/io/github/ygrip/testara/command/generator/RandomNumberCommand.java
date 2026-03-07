package io.github.ygrip.testara.command.generator;

import io.github.ygrip.testara.command.model.CommandLogic;
import io.github.ygrip.testara.command.model.CommandTag;
import org.apache.commons.lang3.ObjectUtils;

import java.security.SecureRandom;
import java.text.ParseException;
import java.util.List;

import static io.github.ygrip.testara.command.CommandExecutor.executeCommand;

/**
 * <p>RandomNumberCommand class.</p>
 *
 * @author yunaz.ramadhan on 10/4/2019
 * @version $Id: $Id
 */
@CommandTag(command = "number", overwrite = true)
public class RandomNumberCommand implements CommandLogic<Integer> {
  /**
   * <p>getRandomNumberInRange.</p>
   *
   * @param min a int.
   * @param max a int.
   * @return a int.
   */
  public static int getRandomNumberInRange(int min, int max) {
    if (min > max) {
      throw new IllegalArgumentException("max must be greater than min");
    } else if (max == min) {
      return max;
    }

    SecureRandom r = new SecureRandom();
    return r.nextInt((max - min) + 1) + min;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean preProcessParameters() {
    return true;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Integer execute(List<Object> parameters) throws ParseException {
    if (parameters == null) {
      return 0;
    }
    int min = ObjectUtils.isEmpty(parameters) ? 0 : executeCommand(String.format("integer(%s)", parameters.get(0)));
    int max = ObjectUtils.isEmpty(parameters) || parameters.size() < 2 ?
        min :
        executeCommand(String.format("integer(%s)", parameters.get(1)));
    if (min == max) {
      return min;
    }
    return getRandomNumberInRange(min, max);
  }
}
