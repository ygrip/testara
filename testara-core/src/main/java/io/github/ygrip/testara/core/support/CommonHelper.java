package io.github.ygrip.testara.core.support;

import io.github.ygrip.testara.core.mapper.MapperHelper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>GlobalHelper class.</p>
 *
 * @author yunaz.ramadhan on 12/7/2019
 * @version $Id: $Id
 */
public final class CommonHelper {
  private final static Set<Class<?>> WRAPPER_TYPES = getWrapperTypes();
  private final static String VALID_NUMBER_FORMAT = "[+-]?(\\d+([.]\\d*)?([eE][+-]?\\d+)?|[.]\\d+([eE][+-]?\\d+)?)";

  private static Set<Class<?>> getWrapperTypes() {
    Set<Class<?>> ret = new HashSet<Class<?>>();
    ret.add(Boolean.class);
    ret.add(Character.class);
    ret.add(Byte.class);
    ret.add(Short.class);
    ret.add(Integer.class);
    ret.add(Long.class);
    ret.add(Float.class);
    ret.add(Double.class);
    ret.add(Void.class);
    return ret;
  }

  /**
   * <p>isPrimitiveType.</p>
   *
   * @param clazz a {@link Class} object.
   * @return a boolean.
   */
  public static boolean isPrimitiveType(Class<?> clazz) {
    return WRAPPER_TYPES.contains(clazz);
  }

  /**
   * <p>isBlank.</p>
   *
   * @param obj              a {@link Object} object.
   * @param checkEmptyString a {@link Boolean} object.
   * @return a boolean.
   */
  public static boolean isBlank(Object obj, Boolean checkEmptyString) {
    if (obj instanceof String) {
      return obj == null || checkEmptyString ? ((String) obj).trim().isEmpty() : ((String) obj).isEmpty();
    } else if (obj instanceof Collection) {
      return obj == null || ((Collection<?>) obj).isEmpty();
    } else if (obj instanceof HashMap) {
      return obj == null || ((HashMap<?, ?>) obj).isEmpty();
    } else if (obj != null && obj.getClass().isArray()) {
      return obj == null || Arrays.stream((Object[]) obj).count() == 0;
    } else {
      return obj == null;
    }
  }

  /**
   * <p>isBlank.</p>
   *
   * @param obj a {@link Object} object.
   * @return a boolean.
   */
  public static boolean isBlank(Object obj) {
    return isBlank(obj, true);
  }

  /**
   * <p>searchEnum.</p>
   *
   * @param enumeration a {@link Class} object.
   * @param search      a {@link String} object.
   * @param <T>         a T object.
   * @return a T object.
   */
  public static <T extends Enum<?>> T searchEnum(Class<T> enumeration, String search) {
    if (!isBlank(search)) {
      for (T each : enumeration.getEnumConstants()) {
        if (each.name().compareToIgnoreCase(search) == 0) {
          return each;
        }
      }
    }
    return null;
  }

  /**
   * <p>splitCollection.</p>
   *
   * @param arrayToSplit a {@link List} object.
   * @param chunkSize    a int.
   * @param <T>          a T object.
   * @return a {@link List} object.
   */
  @SuppressWarnings("unchecked")
  public static <T> List<List<T>> splitCollection(List<T> arrayToSplit, int chunkSize) {
    if (chunkSize <= 0) {
      return new ArrayList<>();  // just in case :)
    }
    // first we have to check if the array can be split in multiple
    // arrays of equal 'chunk' size
    int rest =
        arrayToSplit.size() % chunkSize;  // if rest>0 then our last array will have less elements than the others
    // then we check in how many arrays we can split our input array
    int chunks =
        arrayToSplit.size() / chunkSize + (rest > 0 ? 1 : 0); // we may have to add an additional array for the 'rest'
    // now we know how many arrays we need and create our result array
    List<List<T>> arrays = Arrays.asList(new List[chunks]);
    // we create our resulting arrays by copying the corresponding
    // part from the input array. If we have a rest (rest>0), then
    // the last array will have less elements than the others. This
    // needs to be handled separately, so we iterate 1 times less.
    for (int i = 0; i < (rest > 0 ? chunks - 1 : chunks); i++) {
      // this copies 'chunk' times 'chunkSize' elements into a new array
      List<T> chunk = new ArrayList<>();
      Object[] temp = Arrays.copyOfRange(arrayToSplit.toArray(), i * chunkSize, i * chunkSize + chunkSize);
      for (Object item : temp) {
        chunk.add((T) item);
      }
      arrays.set(i, chunk);
    }
    if (rest > 0) { // only when we have a rest
      // we copy the remaining elements into the last chunk
      List<T> chunk = new ArrayList<>();
      Object[] temp =
          Arrays.copyOfRange(arrayToSplit.toArray(), (chunks - 1) * chunkSize, (chunks - 1) * chunkSize + rest);
      for (Object item : temp) {
        chunk.add((T) item);
      }
      arrays.set(chunks - 1, chunk);
    }
    return arrays; // that's it
  }

  /**
   * <p>parseStringToObject.</p>
   *
   * @param input a {@link String} object.
   * @return a {@link Object} object.
   */
  public static Object parseStringToObject(String input) {
    if (input == null) {
      return null;
    } else if (input.trim().isEmpty()) {
      return input;
    }
    try {
      List<Character> checkedStringLiteral = Arrays.asList('[', '{', 't', 'f', 'n', 'T', 'F', 'N');
      input = input.trim();
      int length = input.length() - 1;
      char startLiteral = input.charAt(0);
      char endLiteral = input.charAt(length);
      if (checkedStringLiteral.contains(startLiteral)) {
        if (Character.isLetter(startLiteral) && Character.isLetter(endLiteral) && length <= 5) {
          if (Arrays.asList("true", "false", "null").contains(input.toLowerCase())) {
            //input might be parsable to boolean or null object
            return Boolean.parseBoolean(input.trim().toLowerCase());
          }
        } else if ((startLiteral == '[' && endLiteral == ']') || (startLiteral == '{' && endLiteral == '}')) {
          //input might be parsable to an array or object
          try {
            Object temp = MapperHelper.toObject(input, Object.class);
            return temp == null ? input : temp;
          } catch (Exception ignored) {

          }
        }
      } else if (Character.isDigit(startLiteral) || startLiteral == '-' || startLiteral == '.') {
        if (input.matches(VALID_NUMBER_FORMAT)) {
          if (input.startsWith("0") && input.length() > 1 && (!input.contains(".") || !input.contains(","))) {
            //input is string
            return input;
          } else {
            //input might be parsable to a valid number
            Object temp = MapperHelper.toObject(input, Object.class);
            return temp == null ? input : temp;
          }
        }
      }
    } catch (Exception ignored) {
      return input;
    }
    return input;
  }

  /**
   * <p>mergeMapObject.</p>
   *
   * @param primary   a {@link Map} object.
   * @param secondary a {@link Map} object.
   * @param <K>       a K object.
   * @param <V>       a V object.
   * @return a {@link Map} object.
   */
  public static <K, V> Map<K, V> mergeMapObject(Map<K, V> primary, Map<K, V> secondary) {
    primary = isBlank(primary) ? new HashMap<>() : primary;
    secondary = isBlank(secondary) ? new HashMap<>() : secondary;
    Map<K, V> combined = new HashMap<>(secondary);
    combined.putAll(primary);
    return combined;
  }

  /**
   * <p>getFieldsUpTo.</p>
   *
   * @param startClass      a {@link Class} object.
   * @param exclusiveParent a {@link Class} object.
   * @return a {@link List} object.
   */
  public static List<Field> getFieldsUpTo(Class<?> startClass, Class<?> exclusiveParent) {
    List<Field> currentClassFields = new ArrayList<>(Arrays.asList(startClass.getDeclaredFields()));

    List<String> fieldNames = currentClassFields.stream().map(Field::getName).collect(Collectors.toList());
    if (exclusiveParent != null) {
      Class<?> parentClass = startClass.getSuperclass();
      if (parentClass != null && (!(parentClass.equals(exclusiveParent)))) {
        List<Field> parentClassFields = getFieldsUpTo(parentClass, exclusiveParent).stream()
            .filter(field -> !fieldNames.contains(field.getName()))
            .collect(Collectors.toList());
        currentClassFields.addAll(parentClassFields);
      }
    }

    return currentClassFields;
  }

  /**
   * <p>getMethodsUpTo.</p>
   *
   * @param startClass      a {@link Class} object.
   * @param exclusiveParent a {@link Class} object.
   * @return a {@link List} object.
   */
  public static List<Method> getMethodsUpTo(Class<?> startClass, Class<?> exclusiveParent) {
    List<Method> currentClassMethods = new ArrayList<>(Arrays.asList(startClass.getDeclaredMethods()));

    List<String> methodIdentifier = currentClassMethods.stream()
        .map(identifier -> identifier.toGenericString().replace(startClass.getName(), ""))
        .collect(Collectors.toList());
    if (exclusiveParent != null) {
      Class<?> parentClass = startClass.getSuperclass();
      if (parentClass != null && (!(parentClass.equals(exclusiveParent)))) {
        List<Method> parentClassMethods = getMethodsUpTo(parentClass, exclusiveParent).stream()
            .filter(method -> !methodIdentifier.contains(method.toGenericString().replace(parentClass.getName(), "")))
            .collect(Collectors.toList());
        currentClassMethods.addAll(parentClassMethods);
      }
    }

    return currentClassMethods;
  }

  /**
   * <p>getParameterizedType.</p>
   *
   * @param inputClass a {@link Class} object.
   * @param index      a int.
   * @return a {@link Type} object.
   */
  public static Type getParameterizedType(Class<?> inputClass, int index) {
    if (index < 0) {
      return null;
    } else {
      Type[] parameters = ((ParameterizedType) inputClass.getGenericSuperclass()).getActualTypeArguments();
      if (index > parameters.length - 1) {
        return null;
      } else {
        return parameters[index];
      }
    }
  }

  public static List<?> convertObjectToList(Object obj) {
    List<?> list = new ArrayList<>();
    if (obj.getClass().isArray()) {
      list = Arrays.asList((Object[]) obj);
    } else if (obj instanceof Collection) {
      list = new ArrayList<>((Collection<?>) obj);
    }
    return list;
  }

  public static boolean isCollection(Object obj) {
    return obj.getClass().isArray() || obj instanceof Collection;
  }
}
