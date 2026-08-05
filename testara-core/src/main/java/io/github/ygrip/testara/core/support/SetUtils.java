package io.github.ygrip.testara.core.support;

import org.apache.commons.lang3.ObjectUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * <p>SetUtils class.</p>
 *
 * @author yunaz.ramadhan on 10/4/2019
 * @version $Id: $Id
 */
public class SetUtils {
  /**
   * <p>union.</p>
   *
   * @param collection a {@link List} object.
   * @param <T>        a T object.
   * @return a {@link List} object.
   */
  @SafeVarargs
  public static <T> List<T> union(List<T>... collection) {
    return union(Arrays.asList(collection));
  }

  /**
   * <p>union.</p>
   *
   * @param collections a {@link List} object.
   * @param <T>         a T object.
   * @return a {@link List} object.
   */
  public static <T> List<T> union(List<List<T>> collections) {
    Set<T> combinedSet = new HashSet<>();

    if (ObjectUtils.isEmpty(collections)) {
      return new ArrayList<>();
    } else {
      for (List<T> col : collections) {
        combinedSet.addAll(col);
      }
    }

    return new ArrayList<>(combinedSet);
  }

  /**
   * <p>intersection.</p>
   *
   * @param collection a {@link List} object.
   * @param <T>        a T object.
   * @return a {@link List} object.
   */
  @SafeVarargs
  public static <T> List<T> intersection(List<T>... collection) {
    return intersection(Arrays.asList(collection));
  }

  /**
   * <p>intersection.</p>
   *
   * @param collections a {@link List} object.
   * @param <T>         a T object.
   * @return a {@link List} object.
   */
  public static <T> List<T> intersection(List<List<T>> collections) {
    Set<T> filteredSet = new HashSet<>();

    if (ObjectUtils.isEmpty(collections)) {
      return new ArrayList<>();
    } else if (collections.size() == 1) {
      return new ArrayList<>();
    } else {
      for (int i = 0; i < collections.get(0).size(); i++) {
        boolean state = false;
        for (int j = 1; j < collections.size(); j++) {
          state = collections.get(j).contains(collections.get(0).get(i));
        }
        if (state) {
          filteredSet.add(collections.get(0).get(i));
        }
      }
    }

    return new ArrayList<>(filteredSet);
  }

  /**
   * <p>difference.</p>
   *
   * @param collection a {@link List} object.
   * @param <T>        a T object.
   * @return a {@link List} object.
   */
  @SafeVarargs
  public static <T> List<T> difference(List<T>... collection) {
    return difference(Arrays.asList(collection));
  }

  /**
   * <p>difference.</p>
   *
   * @param collections a {@link List} object.
   * @param <T>         a T object.
   * @return a {@link List} object.
   */
  public static <T> List<T> difference(List<List<T>> collections) {
    Set<T> filteredSet = new HashSet<>();

    if (ObjectUtils.isEmpty(collections)) {
      return new ArrayList<>();
    } else if (collections.size() == 1) {
      return new ArrayList<>();
    } else {
      List<T> combined = union(collections);
      List<T> intersect = intersection(collections);

      for (T obj : combined) {
        if (!intersect.contains(obj)) {
          filteredSet.add(obj);
        }
      }
    }

    return new ArrayList<>(filteredSet);
  }
}
