package io.github.ygrip.testara.command.data;

import com.fasterxml.jackson.core.type.TypeReference;
import io.github.ygrip.testara.command.model.CommandLogic;
import io.github.ygrip.testara.command.model.CommandTag;
import io.github.ygrip.testara.core.mapper.MapperHelper;
import org.apache.commons.lang3.ObjectUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>SortDescendingCommand class.</p>
 *
 * @author yunaz.ramadhan on 10/4/2019
 * @version $Id: $Id
 */
@CommandTag(command = "descending", alias = {"desc"}, overwrite = true, cacheable = true)
public class SortDescendingCommand implements CommandLogic<List<Object>> {
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
  public List<Object> execute(List<Object> parameters) {
    if (ObjectUtils.isEmpty(parameters)) {
      return null;
    } else {
      List<Object> data;
      if (parameters.size() > 1) {
        data = new ArrayList<>(parameters);
      } else {
        data = MapperHelper.toObject(parameters.get(0), new TypeReference<>() {
        });
      }
      List<Object> result = data.stream().sorted().collect(Collectors.toList());
      Collections.reverse(result);
      return result;
    }
  }
}
