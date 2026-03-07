package io.github.ygrip.testara.command.data;

import io.github.ygrip.testara.command.model.CommandLogic;
import io.github.ygrip.testara.command.model.CommandTag;
import io.github.ygrip.testara.core.support.CommonHelper;
import org.apache.commons.lang3.ObjectUtils;

import java.util.List;

/**
 * <p>SliceOfListCommand class.</p>
 *
 * @author yunaz.ramadhan on 10/4/2019
 * @version $Id: $Id
 */
@CommandTag(command = "sliceof", alias = {"slice of"}, overwrite = true, cacheable = true)
public class SliceOfListCommand implements CommandLogic<List<Object>> {

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
  @SuppressWarnings("unchecked")
  public List<Object> execute(List<Object> parameters) throws Exception {
    if (ObjectUtils.isEmpty(parameters)) {
      return null;
    } else if (parameters.size() == 1) {
      if (parameters.get(0) != null && CommonHelper.isCollection(parameters.get(0))) {
        return (List<Object>) parameters.get(0);
      } else {
        return null;
      }
    } else if (parameters.size() == 2) {
      if (parameters.get(0) != null && CommonHelper.isCollection(parameters.get(0))) {
        int startIndex = 0;
        int endIndex = Integer.parseInt(String.valueOf(parameters.get(1)));
        return slice((List<Object>) parameters.get(0), startIndex, endIndex);
      } else {
        return null;
      }
    } else {
      int startIndex = Integer.parseInt(String.valueOf(parameters.get(1)));
      int endIndex = Integer.parseInt(String.valueOf(parameters.get(2)));
      return slice((List<Object>) parameters.get(0), startIndex, endIndex);
    }
  }

  private List<Object> slice(List<Object> collection, int startIndex, int endIndex) throws Exception {
    if (collection == null) {
      return null;
    } else if (collection.isEmpty()) {
      return collection;
    } else if (collection.size() - 1 < endIndex) {
      throw new Exception("end index should not be greater than collection size");
    } else if (startIndex > endIndex) {
      throw new Exception("start index should not be greater than end index");
    } else {
      return collection.subList(startIndex, endIndex);
    }
  }
}
