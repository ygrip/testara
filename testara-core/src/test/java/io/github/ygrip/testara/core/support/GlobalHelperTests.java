package io.github.ygrip.testara.core.support;

import io.github.ygrip.testara.core.BaseTests;
import io.github.ygrip.testara.core.TestWith;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@Tag("globalHelper")
@TestWith(properties = {"classpath:application.properties", "classpath:configuration.properties"})
public class GlobalHelperTests extends BaseTests {

  // ==================== isPrimitiveType tests ====================

  @Test
  public void isPrimitiveType_withWrapperTypes_shouldReturnTrue() {
    assertThat(CommonHelper.isPrimitiveType(Boolean.class), is(true));
    assertThat(CommonHelper.isPrimitiveType(Character.class), is(true));
    assertThat(CommonHelper.isPrimitiveType(Byte.class), is(true));
    assertThat(CommonHelper.isPrimitiveType(Short.class), is(true));
    assertThat(CommonHelper.isPrimitiveType(Integer.class), is(true));
    assertThat(CommonHelper.isPrimitiveType(Long.class), is(true));
    assertThat(CommonHelper.isPrimitiveType(Float.class), is(true));
    assertThat(CommonHelper.isPrimitiveType(Double.class), is(true));
    assertThat(CommonHelper.isPrimitiveType(Void.class), is(true));
  }

  @Test
  public void isPrimitiveType_withNonWrapperType_shouldReturnFalse() {
    assertThat(CommonHelper.isPrimitiveType(String.class), is(false));
    assertThat(CommonHelper.isPrimitiveType(Object.class), is(false));
    assertThat(CommonHelper.isPrimitiveType(List.class), is(false));
  }

  // ==================== isBlank tests ====================

  @Test
  public void isBlank_withNull_shouldReturnTrue() {
    assertThat(CommonHelper.isBlank(null), is(true));
  }

  @Test
  public void isBlank_withEmptyString_shouldReturnTrue() {
    assertThat(CommonHelper.isBlank(""), is(true));
  }

  @Test
  public void isBlank_withWhitespaceString_shouldReturnTrue() {
    assertThat(CommonHelper.isBlank("   "), is(true));
  }

  @Test
  public void isBlank_withNonEmptyString_shouldReturnFalse() {
    assertThat(CommonHelper.isBlank("hello"), is(false));
  }

  @Test
  public void isBlank_withEmptyCollection_shouldReturnTrue() {
    assertThat(CommonHelper.isBlank(new ArrayList<>()), is(true));
  }

  @Test
  public void isBlank_withNonEmptyCollection_shouldReturnFalse() {
    assertThat(CommonHelper.isBlank(Arrays.asList("a", "b")), is(false));
  }

  @Test
  public void isBlank_withEmptyHashMap_shouldReturnTrue() {
    assertThat(CommonHelper.isBlank(new HashMap<>()), is(true));
  }

  @Test
  public void isBlank_withNonEmptyHashMap_shouldReturnFalse() {
    Map<String, String> map = new HashMap<>();
    map.put("key", "value");
    assertThat(CommonHelper.isBlank(map), is(false));
  }

  @Test
  public void isBlank_withEmptyArray_shouldReturnTrue() {
    assertThat(CommonHelper.isBlank(new Object[]{}), is(true));
  }

  @Test
  public void isBlank_withNonEmptyArray_shouldReturnFalse() {
    assertThat(CommonHelper.isBlank(new Object[]{"a", "b"}), is(false));
  }

  @Test
  public void isBlank_withCheckEmptyStringFalse_shouldNotTrim() {
    // With checkEmptyString=false, " " is not blank (not trimmed)
    assertThat(CommonHelper.isBlank(" ", false), is(false));
  }

  @Test
  public void isBlank_withNonNullObject_shouldReturnFalse() {
    assertThat(CommonHelper.isBlank(new Object()), is(false));
  }

  // ==================== searchEnum tests ====================

  private enum TestEnum {
    VALUE_ONE, VALUE_TWO, THIRD_VALUE
  }

  @Test
  public void searchEnum_withExactMatch_shouldReturnEnum() {
    TestEnum result = CommonHelper.searchEnum(TestEnum.class, "VALUE_ONE");
    assertThat(result, equalTo(TestEnum.VALUE_ONE));
  }

  @Test
  public void searchEnum_withCaseInsensitiveMatch_shouldReturnEnum() {
    TestEnum result = CommonHelper.searchEnum(TestEnum.class, "value_two");
    assertThat(result, equalTo(TestEnum.VALUE_TWO));
  }

  @Test
  public void searchEnum_withNoMatch_shouldReturnNull() {
    TestEnum result = CommonHelper.searchEnum(TestEnum.class, "NON_EXISTENT");
    assertThat(result, is(nullValue()));
  }

  @Test
  public void searchEnum_withNullSearch_shouldReturnNull() {
    TestEnum result = CommonHelper.searchEnum(TestEnum.class, null);
    assertThat(result, is(nullValue()));
  }

  @Test
  public void searchEnum_withEmptySearch_shouldReturnNull() {
    TestEnum result = CommonHelper.searchEnum(TestEnum.class, "");
    assertThat(result, is(nullValue()));
  }

  // ==================== splitCollection tests ====================

  @Test
  public void splitCollection_withEvenSplit_shouldSplitEvenly() {
    List<Integer> input = Arrays.asList(1, 2, 3, 4, 5, 6);
    List<List<Integer>> result = CommonHelper.splitCollection(input, 2);
    assertThat(result, hasSize(3));
    assertThat(result.get(0), equalTo(Arrays.asList(1, 2)));
    assertThat(result.get(1), equalTo(Arrays.asList(3, 4)));
    assertThat(result.get(2), equalTo(Arrays.asList(5, 6)));
  }

  @Test
  public void splitCollection_withUnevenSplit_shouldHaveRemainderInLast() {
    List<Integer> input = Arrays.asList(1, 2, 3, 4, 5);
    List<List<Integer>> result = CommonHelper.splitCollection(input, 2);
    assertThat(result, hasSize(3));
    assertThat(result.get(2), hasSize(1));
  }

  @Test
  public void splitCollection_withChunkSizeZero_shouldReturnEmptyList() {
    List<Integer> input = Arrays.asList(1, 2, 3);
    List<List<Integer>> result = CommonHelper.splitCollection(input, 0);
    assertThat(result, is(empty()));
  }

  @Test
  public void splitCollection_withNegativeChunkSize_shouldReturnEmptyList() {
    List<Integer> input = Arrays.asList(1, 2, 3);
    List<List<Integer>> result = CommonHelper.splitCollection(input, -1);
    assertThat(result, is(empty()));
  }

  @Test
  public void splitCollection_withChunkSizeLargerThanList_shouldReturnSingleChunk() {
    List<Integer> input = Arrays.asList(1, 2, 3);
    List<List<Integer>> result = CommonHelper.splitCollection(input, 10);
    assertThat(result, hasSize(1));
    assertThat(result.get(0), equalTo(input));
  }

  // ==================== parseStringToObject tests ====================

  @Test
  public void parseStringToObject_withNull_shouldReturnNull() {
    Object result = CommonHelper.parseStringToObject(null);
    assertThat(result, is(nullValue()));
  }

  @Test
  public void parseStringToObject_withEmptyString_shouldReturnEmptyString() {
    Object result = CommonHelper.parseStringToObject("");
    assertThat(result, equalTo(""));
  }

  @Test
  public void parseStringToObject_withTrueString_shouldReturnBoolean() {
    Object result = CommonHelper.parseStringToObject("true");
    assertThat(result, equalTo(true));
  }

  @Test
  public void parseStringToObject_withFalseString_shouldReturnBoolean() {
    Object result = CommonHelper.parseStringToObject("false");
    assertThat(result, equalTo(false));
  }

  @Test
  public void parseStringToObject_withNullString_shouldReturnFalse() {
    // "null" is parsed as Boolean.parseBoolean which returns false
    Object result = CommonHelper.parseStringToObject("null");
    assertThat(result, equalTo(false));
  }

  @Test
  public void parseStringToObject_withNumberString_shouldReturnNumber() {
    Object result = CommonHelper.parseStringToObject("123");
    assertThat(result, instanceOf(Number.class));
  }

  @Test
  public void parseStringToObject_withDecimalString_shouldReturnNumber() {
    Object result = CommonHelper.parseStringToObject("3.14");
    assertThat(result, instanceOf(Number.class));
  }

  @Test
  public void parseStringToObject_withLeadingZeroString_shouldReturnString() {
    // Numbers starting with 0 (like "007") should remain strings
    Object result = CommonHelper.parseStringToObject("007");
    assertThat(result, equalTo("007"));
  }

  @Test
  public void parseStringToObject_withNegativeNumber_shouldReturnNumber() {
    Object result = CommonHelper.parseStringToObject("-42");
    assertThat(result, instanceOf(Number.class));
  }

  @Test
  public void parseStringToObject_withRegularString_shouldReturnString() {
    Object result = CommonHelper.parseStringToObject("hello world");
    assertThat(result, equalTo("hello world"));
  }

  @Test
  public void parseStringToObject_withJsonObject_shouldReturnMap() {
    Object result = CommonHelper.parseStringToObject("{\"key\": \"value\"}");
    assertThat(result, instanceOf(Map.class));
  }

  @Test
  public void parseStringToObject_withJsonArray_shouldReturnList() {
    Object result = CommonHelper.parseStringToObject("[1, 2, 3]");
    assertThat(result, instanceOf(List.class));
  }

  // ==================== mergeMapObject tests ====================

  @Test
  public void mergeMapObject_withBothNonEmpty_shouldMerge() {
    Map<String, String> primary = new HashMap<>();
    primary.put("a", "1");
    primary.put("b", "2");

    Map<String, String> secondary = new HashMap<>();
    secondary.put("b", "secondary_b");
    secondary.put("c", "3");

    Map<String, String> result = CommonHelper.mergeMapObject(primary, secondary);

    assertThat(result.get("a"), equalTo("1"));
    assertThat(result.get("b"), equalTo("2")); // primary takes precedence
    assertThat(result.get("c"), equalTo("3"));
  }

  @Test
  public void mergeMapObject_withNullPrimary_shouldUseSecondary() {
    Map<String, String> secondary = new HashMap<>();
    secondary.put("key", "value");

    Map<String, String> result = CommonHelper.mergeMapObject(null, secondary);
    assertThat(result.get("key"), equalTo("value"));
  }

  @Test
  public void mergeMapObject_withNullSecondary_shouldUsePrimary() {
    Map<String, String> primary = new HashMap<>();
    primary.put("key", "value");

    Map<String, String> result = CommonHelper.mergeMapObject(primary, null);
    assertThat(result.get("key"), equalTo("value"));
  }

  @Test
  public void mergeMapObject_withBothNull_shouldReturnEmptyMap() {
    Map<String, String> result = CommonHelper.mergeMapObject(null, null);
    assertThat(result, is(notNullValue()));
    assertThat(result, is(anEmptyMap()));
  }

  // ==================== getFieldsUpTo tests ====================

  private static class ParentClass {
    private String parentField;
    protected int protectedField;
  }

  private static class ChildClass extends ParentClass {
    private String childField;
  }

  @Test
  public void getFieldsUpTo_shouldIncludeAllFields() {
    List<Field> fields = CommonHelper.getFieldsUpTo(ChildClass.class, Object.class);
    List<String> fieldNames = fields.stream().map(Field::getName).toList();

    assertThat(fieldNames, hasItem("childField"));
    assertThat(fieldNames, hasItem("parentField"));
    assertThat(fieldNames, hasItem("protectedField"));
  }

  @Test
  public void getFieldsUpTo_withExclusiveParent_shouldStopAtParent() {
    List<Field> fields = CommonHelper.getFieldsUpTo(ChildClass.class, ParentClass.class);
    List<String> fieldNames = fields.stream().map(Field::getName).toList();

    assertThat(fieldNames, hasItem("childField"));
    assertThat(fieldNames, not(hasItem("parentField")));
  }

  @Test
  public void getFieldsUpTo_withNullExclusiveParent_shouldOnlyReturnDeclared() {
    List<Field> fields = CommonHelper.getFieldsUpTo(ChildClass.class, null);
    List<String> fieldNames = fields.stream().map(Field::getName).toList();

    assertThat(fieldNames, hasItem("childField"));
    assertThat(fieldNames, not(hasItem("parentField")));
  }

  // ==================== getMethodsUpTo tests ====================

  private static class ParentMethodClass {
    public void parentMethod() {
    }
  }

  private static class ChildMethodClass extends ParentMethodClass {
    public void childMethod() {
    }
  }

  @Test
  public void getMethodsUpTo_shouldIncludeAllMethods() {
    List<Method> methods = CommonHelper.getMethodsUpTo(ChildMethodClass.class, Object.class);
    List<String> methodNames = methods.stream().map(Method::getName).toList();

    assertThat(methodNames, hasItem("childMethod"));
    assertThat(methodNames, hasItem("parentMethod"));
  }

  @Test
  public void getMethodsUpTo_withExclusiveParent_shouldStopAtParent() {
    List<Method> methods = CommonHelper.getMethodsUpTo(ChildMethodClass.class, ParentMethodClass.class);
    List<String> methodNames = methods.stream().map(Method::getName).toList();

    assertThat(methodNames, hasItem("childMethod"));
    assertThat(methodNames, not(hasItem("parentMethod")));
  }

  // ==================== convertObjectToList tests ====================

  @Test
  public void convertObjectToList_withArray_shouldReturnList() {
    Object[] array = {"a", "b", "c"};
    List<?> result = CommonHelper.convertObjectToList(array);
    assertThat(result, hasSize(3));
    assertThat(result, contains("a", "b", "c"));
  }

  @Test
  public void convertObjectToList_withCollection_shouldReturnList() {
    Collection<String> collection = Arrays.asList("x", "y", "z");
    List<?> result = CommonHelper.convertObjectToList(collection);
    assertThat(result, hasSize(3));
    assertThat(result, contains("x", "y", "z"));
  }

  @Test
  public void convertObjectToList_withNonCollection_shouldReturnEmptyList() {
    List<?> result = CommonHelper.convertObjectToList("string");
    assertThat(result, is(empty()));
  }

  // ==================== isCollection tests ====================

  @Test
  public void isCollection_withArray_shouldReturnTrue() {
    assertThat(CommonHelper.isCollection(new Object[]{}), is(true));
  }

  @Test
  public void isCollection_withList_shouldReturnTrue() {
    assertThat(CommonHelper.isCollection(new ArrayList<>()), is(true));
  }

  @Test
  public void isCollection_withNonCollection_shouldReturnFalse() {
    assertThat(CommonHelper.isCollection("string"), is(false));
  }
}
