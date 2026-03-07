package io.github.ygrip.testara.core.support;

import io.github.ygrip.testara.core.BaseTests;
import io.github.ygrip.testara.core.TestWith;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@Tag("setUtils")
@TestWith(properties = {"classpath:application.properties", "classpath:configuration.properties"})
public class SetUtilsTests extends BaseTests {

  // ==================== union tests ====================

  @Test
  public void union_withVarargs_shouldCombineAllElements() {
    List<Integer> list1 = Arrays.asList(1, 2, 3);
    List<Integer> list2 = Arrays.asList(3, 4, 5);
    List<Integer> list3 = Arrays.asList(5, 6, 7);

    List<Integer> result = SetUtils.union(list1, list2, list3);

    assertThat(result, hasSize(7));
    assertThat(result, containsInAnyOrder(1, 2, 3, 4, 5, 6, 7));
  }

  @Test
  public void union_withListOfLists_shouldCombineAllElements() {
    List<List<String>> collections = Arrays.asList(
        Arrays.asList("a", "b"),
        Arrays.asList("b", "c"),
        Arrays.asList("c", "d")
    );

    List<String> result = SetUtils.union(collections);

    assertThat(result, hasSize(4));
    assertThat(result, containsInAnyOrder("a", "b", "c", "d"));
  }

  @Test
  public void union_withEmptyCollections_shouldReturnEmptyList() {
    List<List<String>> collections = new ArrayList<>();

    List<String> result = SetUtils.union(collections);

    assertThat(result, is(empty()));
  }

  @Test
  public void union_withNullCollections_shouldReturnEmptyList() {
    List<String> result = SetUtils.union((List<List<String>>) null);

    assertThat(result, is(empty()));
  }

  @Test
  public void union_withDuplicates_shouldRemoveDuplicates() {
    List<Integer> list1 = Arrays.asList(1, 1, 2, 2);
    List<Integer> list2 = Arrays.asList(2, 2, 3, 3);

    List<Integer> result = SetUtils.union(list1, list2);

    assertThat(result, hasSize(3));
    assertThat(result, containsInAnyOrder(1, 2, 3));
  }

  @Test
  public void union_withSingleList_shouldReturnUniqueElements() {
    List<List<Integer>> collections = Arrays.asList(
        Arrays.asList(1, 2, 3)
    );

    List<Integer> result = SetUtils.union(collections);

    assertThat(result, hasSize(3));
    assertThat(result, containsInAnyOrder(1, 2, 3));
  }

  // ==================== intersection tests ====================

  @Test
  public void intersection_withVarargs_shouldReturnCommonElements() {
    List<Integer> list1 = Arrays.asList(1, 2, 3, 4);
    List<Integer> list2 = Arrays.asList(2, 3, 4, 5);
    List<Integer> list3 = Arrays.asList(3, 4, 5, 6);

    List<Integer> result = SetUtils.intersection(list1, list2, list3);

    assertThat(result, hasSize(2));
    assertThat(result, containsInAnyOrder(3, 4));
  }

  @Test
  public void intersection_withListOfLists_shouldReturnCommonElements() {
    List<List<String>> collections = Arrays.asList(
        Arrays.asList("a", "b", "c"),
        Arrays.asList("b", "c", "d"),
        Arrays.asList("c", "d", "e")
    );

    List<String> result = SetUtils.intersection(collections);

    assertThat(result, hasSize(1));
    assertThat(result, contains("c"));
  }

  @Test
  public void intersection_withEmptyCollections_shouldReturnEmptyList() {
    List<List<String>> collections = new ArrayList<>();

    List<String> result = SetUtils.intersection(collections);

    assertThat(result, is(empty()));
  }

  @Test
  public void intersection_withNullCollections_shouldReturnEmptyList() {
    List<String> result = SetUtils.intersection((List<List<String>>) null);

    assertThat(result, is(empty()));
  }

  @Test
  public void intersection_withSingleList_shouldReturnEmptyList() {
    List<List<Integer>> collections = Arrays.asList(
        Arrays.asList(1, 2, 3)
    );

    List<Integer> result = SetUtils.intersection(collections);

    assertThat(result, is(empty()));
  }

  @Test
  public void intersection_withNoCommonElements_shouldReturnEmptyList() {
    List<Integer> list1 = Arrays.asList(1, 2, 3);
    List<Integer> list2 = Arrays.asList(4, 5, 6);

    List<Integer> result = SetUtils.intersection(list1, list2);

    assertThat(result, is(empty()));
  }

  @Test
  public void intersection_withAllCommonElements_shouldReturnAll() {
    List<Integer> list1 = Arrays.asList(1, 2, 3);
    List<Integer> list2 = Arrays.asList(1, 2, 3);

    List<Integer> result = SetUtils.intersection(list1, list2);

    assertThat(result, hasSize(3));
    assertThat(result, containsInAnyOrder(1, 2, 3));
  }

  // ==================== difference tests ====================

  @Test
  public void difference_withVarargs_shouldReturnDifferentElements() {
    List<Integer> list1 = Arrays.asList(1, 2, 3);
    List<Integer> list2 = Arrays.asList(2, 3, 4);

    List<Integer> result = SetUtils.difference(list1, list2);

    assertThat(result, hasSize(2));
    assertThat(result, containsInAnyOrder(1, 4));
  }

  @Test
  public void difference_withListOfLists_shouldReturnDifferentElements() {
    List<List<String>> collections = Arrays.asList(
        Arrays.asList("a", "b", "c"),
        Arrays.asList("b", "c", "d")
    );

    List<String> result = SetUtils.difference(collections);

    assertThat(result, hasSize(2));
    assertThat(result, containsInAnyOrder("a", "d"));
  }

  @Test
  public void difference_withEmptyCollections_shouldReturnEmptyList() {
    List<List<String>> collections = new ArrayList<>();

    List<String> result = SetUtils.difference(collections);

    assertThat(result, is(empty()));
  }

  @Test
  public void difference_withNullCollections_shouldReturnEmptyList() {
    List<String> result = SetUtils.difference((List<List<String>>) null);

    assertThat(result, is(empty()));
  }

  @Test
  public void difference_withSingleList_shouldReturnEmptyList() {
    List<List<Integer>> collections = Arrays.asList(
        Arrays.asList(1, 2, 3)
    );

    List<Integer> result = SetUtils.difference(collections);

    assertThat(result, is(empty()));
  }

  @Test
  public void difference_withIdenticalLists_shouldReturnEmptyList() {
    List<Integer> list1 = Arrays.asList(1, 2, 3);
    List<Integer> list2 = Arrays.asList(1, 2, 3);

    List<Integer> result = SetUtils.difference(list1, list2);

    assertThat(result, is(empty()));
  }

  @Test
  public void difference_withNoOverlap_shouldReturnAllElements() {
    List<Integer> list1 = Arrays.asList(1, 2, 3);
    List<Integer> list2 = Arrays.asList(4, 5, 6);

    List<Integer> result = SetUtils.difference(list1, list2);

    assertThat(result, hasSize(6));
    assertThat(result, containsInAnyOrder(1, 2, 3, 4, 5, 6));
  }

  @Test
  public void difference_withMultipleLists_shouldReturnUniqueElements() {
    List<Integer> list1 = Arrays.asList(1, 2, 3, 4);
    List<Integer> list2 = Arrays.asList(2, 3, 4, 5);
    List<Integer> list3 = Arrays.asList(3, 4, 5, 6);

    List<Integer> result = SetUtils.difference(list1, list2, list3);

    // Only elements not in all lists
    assertThat(result, containsInAnyOrder(1, 2, 5, 6));
  }
}
