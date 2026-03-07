package io.github.ygrip.testara.command.data;

import com.fasterxml.jackson.core.type.TypeReference;
import io.github.ygrip.testara.command.model.CommandLogic;
import io.github.ygrip.testara.command.model.CommandTag;
import io.github.ygrip.testara.core.mapper.MapperHelper;
import org.apache.commons.lang3.ObjectUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * <p>KeySetCommand class.</p>
 *
 * @author yunaz.ramadhan on 8/24/2020
 * @version $Id: $Id
 */
@CommandTag(command = "keyset", overwrite = true, cacheable = true)
public class KeySetCommand implements CommandLogic<List<String>> {
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
  public List<String> execute(List<Object> parameters) throws Exception {
    if (ObjectUtils.isEmpty(parameters)) {
      return new ArrayList<>();
    } else {
      List<String> result = new ArrayList<>();
      if (!ObjectUtils.isEmpty(parameters.get(0)) && parameters.get(0) instanceof HashMap) {
        result.addAll(Objects.requireNonNull(MapperHelper.toObject(parameters.get(0),
            new TypeReference<Map<String, Object>>() {
            })).keySet());
      }
      return result;
    }
  }
}
