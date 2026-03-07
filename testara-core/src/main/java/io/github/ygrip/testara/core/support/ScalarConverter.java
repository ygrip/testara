package io.github.ygrip.testara.core.support;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Locale;

public final class ScalarConverter {

  @SuppressWarnings("unchecked")
  public <T> T convert(String value, Class<T> targetType) {

    if (value == null) {
      return null;
    }

    if (targetType == String.class || targetType == Object.class) {
      return (T) value;
    }

    if (targetType == int.class || targetType == Integer.class) {
      return (T) Integer.valueOf(value);
    }

    if (targetType == long.class || targetType == Long.class) {
      return (T) Long.valueOf(value);
    }

    if (targetType == boolean.class || targetType == Boolean.class) {
      return (T) Boolean.valueOf(value);
    }

    if (targetType == double.class || targetType == Double.class) {
      return (T) Double.valueOf(value);
    }

    if (targetType == float.class || targetType == Float.class) {
      return (T) Float.valueOf(value);
    }

    if (targetType.isEnum()) {
      return (T) Enum.valueOf((Class<Enum>) targetType, value.toUpperCase(Locale.ROOT));
    }

    // valueOf(String)
    try {
      Method m = targetType.getMethod("valueOf", String.class);
      if (m.canAccess(null)) {
        return (T) m.invoke(null, value);
      }
    } catch (NoSuchMethodException ignored) {
    } catch (Exception e) {
      return (T) value;
    }

    // Constructor(String)
    try {
      Constructor<T> c = targetType.getConstructor(String.class);
      return c.newInstance(value);
    } catch (NoSuchMethodException ignored) {
    } catch (Exception e) {
      return (T) value;
    }

    return (T) value;
  }
}

