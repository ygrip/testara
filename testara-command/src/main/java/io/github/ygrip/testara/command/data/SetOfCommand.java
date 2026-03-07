package io.github.ygrip.testara.command.data;

import com.fasterxml.jackson.core.type.TypeReference;
import io.github.ygrip.testara.command.model.CommandLogic;
import io.github.ygrip.testara.command.model.CommandTag;
import io.github.ygrip.testara.core.mapper.MapperHelper;
import io.github.ygrip.testara.core.support.CommonHelper;
import org.apache.commons.lang3.ObjectUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * <p>SetOfCommand class.</p>
 *
 * @author yunaz.ramadhan on 8/24/2020
 * @version $Id: $Id
 */
@CommandTag(command = "setof", overwrite = true, cacheable = true)
public class SetOfCommand implements CommandLogic<List<Object>> {
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
  public List<Object> execute(List<Object> parameters) throws Exception {
    if (ObjectUtils.isEmpty(parameters)) {
      return parameters;
    } else {
      Set<Object> unique;
      if (CommonHelper.isCollection(parameters.get(0))) {
        unique = new HashSet<>(Objects.requireNonNull(MapperHelper.toObject(parameters.get(0),
            new TypeReference<List<Object>>() {
            })));
      } else {
        unique = new HashSet<>(Collections.singletonList(parameters.get(0)));
      }
      return new ArrayList<>(unique);
    }
  }
}
