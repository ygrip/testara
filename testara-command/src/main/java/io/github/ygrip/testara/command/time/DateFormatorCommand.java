package io.github.ygrip.testara.command.time;

import io.github.ygrip.testara.command.model.CommandLogic;
import io.github.ygrip.testara.command.model.CommandTag;
import io.github.ygrip.testara.core.time.DateHelper;
import org.apache.commons.lang3.ObjectUtils;

import java.util.List;

/**
 * <p>DateFormatorCommand class.</p>
 *
 * @author yunaz.ramadhan on 10/4/2019
 * @version $Id: $Id
 */
@CommandTag(command = "date", overwrite = true)
public class DateFormatorCommand implements CommandLogic<String> {
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
  public String execute(List<Object> parameters) throws Exception {
    return ObjectUtils.isEmpty(parameters) ?
        DateHelper.getCurrentDate() :
        parameters.size() == 1 ?
            DateHelper.getDate(String.valueOf(parameters.get(0))) :
            parameters.size() == 2 ?
                DateHelper.getDate(String.valueOf(parameters.get(0)), String.valueOf(parameters.get(1))) :
                DateHelper.getDate(String.valueOf(parameters.get(0)),
                    String.valueOf(parameters.get(1)),
                    String.valueOf(parameters.get(2)));
  }
}
