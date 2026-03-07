package io.github.ygrip.testara.command.data;

import com.fasterxml.jackson.core.type.TypeReference;
import io.github.ygrip.testara.command.model.CommandLogic;
import io.github.ygrip.testara.command.model.CommandTag;
import io.github.ygrip.testara.core.mapper.MapperHelper;
import io.github.ygrip.testara.core.support.CommonHelper;
import org.apache.commons.lang3.ObjectUtils;

import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * <p>OneOfCommand class.</p>
 *
 * @author yunaz.ramadhan on 10/4/2019
 * @version $Id: $Id
 */
@CommandTag(command = "oneof", overwrite = true)
public class OneOfCommand implements CommandLogic<Object> {
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
    return ObjectUtils.isEmpty(parameters) ?
        null :
        parameters.size() == 1 && CommonHelper.isCollection(parameters.get(0)) ?
            getOne(Objects.requireNonNull(MapperHelper.toObject(parameters.get(0), new TypeReference<>() {
            }))) :
            getOne(parameters);
  }

  private Object getOne(List<Object> input) {
    return input.get(new Random().nextInt(input.size()));
  }
}
