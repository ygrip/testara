package io.github.ygrip.testara.command.data;

import io.github.ygrip.testara.command.model.CommandLogic;
import io.github.ygrip.testara.command.model.CommandTag;
import io.github.ygrip.testara.core.config.PropertyResolver;
import io.github.ygrip.testara.core.context.TestFramework;
import org.apache.commons.lang3.ObjectUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <p>GetPropertiesCommand class.</p>
 *
 * @author yunaz.ramadhan on 10/4/2019
 * @version $Id: $Id
 */
@CommandTag(command = "properties", alias = "prop", overwrite = true, cacheable = true)
public class GetPropertiesCommand implements CommandLogic<Object> {
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
  public Object execute(List<Object> parameters) {
    if (ObjectUtils.isEmpty(parameters)) {
      return null;
    } else {
      Object result;
      if (parameters.size() == 1) {
        result = TestFramework.context().configuration().get(String.valueOf(parameters.get(0))).orElse(null);
        if (ObjectUtils.isEmpty(result)) {
          Map<String, PropertyResolver.PropertyValue> temp =
              TestFramework.context().configuration().getByPrefix(String.valueOf(parameters.get(0)));
          result = temp.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().value()));
        }
      } else {
        result = TestFramework.context()
            .configuration()
            .get(String.valueOf(parameters.get(0)), String.valueOf(parameters.get(1)));
      }
      return result;
    }
  }
}
