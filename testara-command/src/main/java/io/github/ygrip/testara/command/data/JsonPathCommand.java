package io.github.ygrip.testara.command.data;

import io.github.ygrip.testara.command.model.CommandLogic;
import io.github.ygrip.testara.command.model.CommandTag;
import io.github.ygrip.testara.core.json.JsonHelper;
import org.apache.commons.lang3.ObjectUtils;

import java.util.List;

/**
 * <p>JsonPathCommand class.</p>
 *
 * @author yunaz.ramadhan on 10/4/2019
 * @version $Id: $Id
 */
@CommandTag(command = "jsonpath", overwrite = true, cacheable = true)
public class JsonPathCommand implements CommandLogic<Object> {
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
  public Object execute(List<Object> parameters) throws Exception {
    return ObjectUtils.isEmpty(parameters) || parameters.size() < 2 ?
        null :
        ObjectUtils.isEmpty(parameters.get(0)) || ObjectUtils.isEmpty(parameters.get(1)) ?
            null :
            get(parameters.get(0), String.valueOf(parameters.get(1)));
  }

  private Object get(Object object, String path) {
    try (JsonHelper.JsonPathHolder jsonPath = JsonHelper.instance()) {
      return jsonPath.parse(object).read(path);
    } catch (Exception ignored) {

    }
    return null;
  }
}
