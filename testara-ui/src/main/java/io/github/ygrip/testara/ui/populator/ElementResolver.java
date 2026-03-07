package io.github.ygrip.testara.ui.populator;

import java.util.Optional;

import org.apache.commons.lang3.ObjectUtils;

import io.github.ygrip.testara.core.support.NumberHelper;
import io.github.ygrip.testara.ui.executor.Actor;
import io.github.ygrip.testara.ui.model.AcceptedResolverDataType;
import io.github.ygrip.testara.ui.observation.Observation;
import io.github.ygrip.testara.ui.page.Element;

/**
 * <p>Abstract ElementResolver class.</p>
 *
 * @author yunaz.ramadhan on 12/29/2019
 * @version $Id: $Id
 */
public final class ElementResolver {
  private Element element;
  private final Observation<?> observation;
  private AcceptedResolverDataType TARGET_DATA;

  ElementResolver(Observation<?> observation) {
    this.observation = observation;
  }

  /**
   * <p>asInteger.</p>
   *
   * @return a {@link ElementResolver} object.
   */
  public ElementResolver asInteger() {
    return as(AcceptedResolverDataType.INTEGER);
  }

  /**
   * <p>asLong.</p>
   *
   * @return a {@link ElementResolver} object.
   */
  public ElementResolver asLong() {
    return as(AcceptedResolverDataType.LONG);
  }

  /**
   * <p>asBoolean.</p>
   *
   * @return a {@link ElementResolver} object.
   */
  public ElementResolver asBoolean() {
    return as(AcceptedResolverDataType.BOOLEAN);
  }

  /**
   * <p>asFloat.</p>
   *
   * @return a {@link ElementResolver} object.
   */
  public ElementResolver asFloat() {
    return as(AcceptedResolverDataType.FLOAT);
  }

  /**
   * <p>asDouble.</p>
   *
   * @return a {@link ElementResolver} object.
   */
  public ElementResolver asDouble() {
    return as(AcceptedResolverDataType.DOUBLE);
  }

  /**
   * <p>asObject.</p>
   *
   * @return a {@link ElementResolver} object.
   */
  public ElementResolver asObject() {
    return as(AcceptedResolverDataType.OBJECT);
  }

  /**
   * <p>as.</p>
   *
   * @param type a {@link AcceptedResolverDataType} object.
   * @return a {@link ElementResolver} object.
   */
  public ElementResolver as(AcceptedResolverDataType type) {
    this.TARGET_DATA = type;
    return this;
  }

  AcceptedResolverDataType getTargetDataType() {
    return this.TARGET_DATA;
  }

  private Element getTarget() {
    return this.element;
  }

  ElementResolver setTarget(Element element) {
    this.element = element;
    return this;
  }

  /**
   * <p>result.</p>
   *
   * @param actor a {@link Actor} object.
   * @return a {@link Object} object.
   */
  public Object result(Actor actor) {
    Object result = null;
    final var target = getTarget();
    if (ObjectUtils.isEmpty(target)) {
      result = actor.observe(observation);
    } else {
      result = actor.observe(observation.root(target));
    }

    final var targetType = Optional.ofNullable(getTargetDataType())
      .orElse(AcceptedResolverDataType.OBJECT);
    switch (targetType) {
      case INTEGER:
        result = Optional.ofNullable(result)
          .map(Object::toString)
          .map(number -> NumberHelper.parseNumber(number, Integer.class))
          .orElse(null);
        break;
      case LONG:
        result = Optional.ofNullable(result)
          .map(Object::toString)
          .map(number -> NumberHelper.parseNumber(number, Long.class))
          .orElse(null);
        break;
      case DOUBLE:
        result = Optional.ofNullable(result)
          .map(Object::toString)
          .map(number -> NumberHelper.parseNumber(number, Double.class))
          .orElse(null);
        break;
      case BOOLEAN:
        result = Optional.ofNullable(result)
          .map(Object::toString)
          .map(String::trim)
          .map(Boolean::parseBoolean)
          .orElse(null);
        break;
      case FLOAT:
        result = Optional.ofNullable(result)
          .map(Object::toString)
          .map(number -> NumberHelper.parseNumber(number, Float.class))
          .orElse(null);
        break;
      case STRING:
        result = Optional.ofNullable(result)
          .map(Object::toString)
          .orElse(null);
        break;
      default:
        break;
    }
    return result;
  }

  enum LocatorType {
    PARENT, SELF, PRECEDING_SIBLING, FOLLOWING_SIBLING, CHILD
  }
}
