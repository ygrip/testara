package io.github.ygrip.testara.command.time;

import io.github.ygrip.testara.command.model.CommandLogic;
import io.github.ygrip.testara.command.model.CommandTag;
import io.github.ygrip.testara.core.time.DateHelper;
import org.apache.commons.lang3.ObjectUtils;

import java.util.List;

/**
 * <p>GetDateTimeCommand class.</p>
 *
 * @author yunaz.ramadhan on 10/4/2019
 * @version $Id: $Id
 */
@CommandTag(command = "last_day_of_month", overwrite = true)
public class GetLastDayOfMonthCommand implements CommandLogic<Integer> {
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
  public Integer execute(List<Object> parameters) {
    return ObjectUtils.isEmpty(parameters) ?
        DateHelper.getLastDayOfMonth() :
        DateHelper.getLastDayOfMonth(Long.parseLong(String.valueOf(parameters.get(0))));
  }
}
