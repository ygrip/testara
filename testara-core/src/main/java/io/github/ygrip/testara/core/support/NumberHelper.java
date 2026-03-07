package io.github.ygrip.testara.core.support;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public final class NumberHelper {

  private NumberHelper(){

  }

  private static String cleanNumericString(String input) {
    if (input == null)
      return "";

    input = input.replaceAll("[^\\d.]", "");
    int len = input.length();
    StringBuilder sb = new StringBuilder(len);
    for (int i = 0; i < len; i++) {
      char c = input.charAt(i);
      if (c != ',' && c != '_' && !Character.isWhitespace(c)) {
        sb.append(c);
      }
    }
    return sb.toString();
  }

  public static Number parseNumber(String input) {
    if (input == null) {
      return null;
    }

    String cleaned = input.trim();
    if (cleaned.isEmpty()) {
      return null;
    }

    cleaned = cleaned.replaceAll("[,_]", "");

    try {
      if (cleaned.contains(".") || cleaned.contains("e") || cleaned.contains("E")) {
        double d = Double.parseDouble(cleaned);
        if (d % 1 == 0 && d <= Long.MAX_VALUE && d >= Long.MIN_VALUE) {
          return (long) d;
        }
        return d;
      } else {
        long l = Long.parseLong(cleaned);
        return (l <= Integer.MAX_VALUE && l >= Integer.MIN_VALUE) ? (int) l : l;
      }
    } catch (NumberFormatException e) {
      return parseNumber(cleanNumericString(cleaned));
    }
  }

  @SuppressWarnings("unchecked")
  public static <T extends Number> T parseNumber(String input, Class<T> targetType) {
    Number cleaned = parseNumber(input);

    if (cleaned == null) {
      return null;
    }

    try {
      if (targetType == Integer.class) {
        return (T) Integer.valueOf(cleaned.intValue());
      } else if (targetType == Long.class) {
        return (T) Long.valueOf(cleaned.longValue());
      } else if (targetType == Float.class) {
        return (T) Float.valueOf(cleaned.floatValue());
      } else if (targetType == Double.class) {
        return (T) Double.valueOf(cleaned.doubleValue());
      } else if (targetType == Short.class) {
        return (T) Short.valueOf(cleaned.shortValue());
      } else if (targetType == Byte.class) {
        return (T) Byte.valueOf(cleaned.byteValue());
      } else if (targetType == BigDecimal.class) {
        return (T) new BigDecimal(cleaned.toString());
      } else {
        throw new IllegalArgumentException("Unsupported target type: " + targetType.getName());
      }
    } catch (NumberFormatException e) {
      return null;
    }
  }

  public static Number autoBoxingNumber(BigDecimal decimalValue) {
    // Detect numeric type
    if (decimalValue.stripTrailingZeros().scale() <= 0) {
      // integer type
      try {
        long lv = decimalValue.longValueExact();
        if (lv >= Integer.MIN_VALUE && lv <= Integer.MAX_VALUE)
          return (int) lv;
        else
          return lv;
      } catch (ArithmeticException e) {
        return decimalValue.toBigInteger();
      }
    } else {
      return decimalValue.doubleValue();
    }
  }

  public static boolean isNumeric(String input) {
    if (input == null) {
      return false;
    }
    input = input.trim();
    if (input.isEmpty()) {
      return false;
    }
    boolean result = false;
    for (int i = 0; i < input.length(); i++) {
      char charAt = input.charAt(i);
      result = '0' <= charAt && charAt <= '9';
      if (!result) {
        break;
      }
    }
    return result;
  }

  /**
   * <p>splitWise. is a generic method to split any number (regular number or floating number) with respect to the rest of divisible number from total number</p>
   *
   * @param total a {@link Number} object.
   * @return a {@link List} object.
   */
  @SuppressWarnings("unchecked")
  public static <T extends Number> List<T> splitWise(Number total, int dividedBy) {
    List<Number> result = new ArrayList<>();

    if (total.intValue() > 0 && dividedBy > 0) {
      Number division = total.doubleValue() / dividedBy;

      int index = 0;
      while (total.doubleValue() > 0) {
        index++;
        if (index == dividedBy) {
          if (total instanceof Byte) {
            result.add(total.byteValue());
          } else if (total instanceof Short) {
            result.add(total.shortValue());
          } else if (total instanceof Integer) {
            result.add(total.intValue());
          } else if (total instanceof Long) {
            result.add(total.longValue());
          } else if (total instanceof Float) {
            result.add(total.floatValue());
          } else {
            result.add(total.doubleValue());
          }
          break;
        } else {
          if (total instanceof Byte) {
            total = total.byteValue() - division.byteValue();
            result.add(division.byteValue());
          } else if (total instanceof Short) {
            total = total.shortValue() - division.shortValue();
            result.add(division.shortValue());
          } else if (total instanceof Integer) {
            total = total.intValue() - division.intValue();
            result.add(division.intValue());
          } else if (total instanceof Long) {
            total = total.longValue() - division.longValue();
            result.add(division.longValue());
          } else if (total instanceof Float) {
            total = total.floatValue() - division.floatValue();
            result.add(division.floatValue());
          } else {
            total = total.doubleValue() - division.doubleValue();
            result.add(division.doubleValue());
          }
        }
      }
    }
    return (List<T>) result;
  }
}
