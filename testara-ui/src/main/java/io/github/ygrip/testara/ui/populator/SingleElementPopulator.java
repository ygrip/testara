package io.github.ygrip.testara.ui.populator;

import java.time.Duration;

import com.fasterxml.jackson.core.type.TypeReference;
import io.github.ygrip.testara.core.mapper.MapperHelper;
import io.github.ygrip.testara.ui.driver.DriverSession;
import io.github.ygrip.testara.ui.executor.Actor;
import io.github.ygrip.testara.ui.page.Element;

/**
 * <p>SingleElementPopulator class.</p>
 *
 * @author yunaz.ramadhan on 12/29/2019
 * @version $Id: $Id
 */
public final class SingleElementPopulator extends BasicElementPopulator<SingleElementPopulator> {
  private final static Duration DEFAULT_TIMEOUT = Duration.ofSeconds(2);

  SingleElementPopulator(Actor actor, DriverSession<?> session, Element target) {
    super(actor, session, target);
  }

  @Override
  @SuppressWarnings("unchecked")
  Object resolve() throws Exception {
    Object instance = target().one(DEFAULT_TIMEOUT);
    final var resolvedElement = Element.instance(finder(), pageContext(), instance)
      .withChild(target());
    performPreConditionActions();
    Object result = process(resolvedElement);
    performPostConditionActions();
    return result;
  }

  /**
   * <p>resolveAs.</p>
   *
   * @param reference a {@link Class} object.
   * @param <T>       a T object.
   * @return a T object.
   */
  public <T> T resolveAs(Class<T> reference) throws Exception {
    return MapperHelper.toObject(resolve(), reference);
  }

  /**
   * <p>resolveAs.</p>
   *
   * @param reference a {@link TypeReference} object.
   * @param <T>       a T object.
   * @return a T object.
   */
  public <T> T resolveAs(TypeReference<T> reference) throws Exception {
    return MapperHelper.toObject(resolve(), reference);
  }
}
