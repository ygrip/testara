package io.github.ygrip.testara.core.transformer;

import io.github.ygrip.testara.core.BaseTests;
import io.github.ygrip.testara.core.TestWith;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@Tag("transformedTable")
@TestWith(properties = {"classpath:application.properties", "classpath:configuration.properties"})
public class TransformedTableTests extends BaseTests {

  // ==================== Constructor tests ====================

  @Test
  public void constructor_withValidData_shouldInitializeCorrectly() {
    List<List<?>> data = Arrays.asList(
        Arrays.asList("name", "age", "city"),
        Arrays.asList("John", 30, "NYC"),
        Arrays.asList("Jane", 25, "LA")
    );

    TransformedTable table = new TransformedTable(data);

    assertThat(table.width(), equalTo(3));
    assertThat(table.height(), equalTo(2)); // excludes header row
  }

  @Test
  public void constructor_withNullData_shouldCreateEmptyTable() {
    TransformedTable table = new TransformedTable(null);

    assertThat(table.width(), equalTo(0));
    assertThat(table.height(), equalTo(0));
  }

  @Test
  public void constructor_withEmptyData_shouldCreateEmptyTable() {
    TransformedTable table = new TransformedTable(new ArrayList<>());

    assertThat(table.width(), equalTo(0));
    assertThat(table.height(), equalTo(0));
  }

  @Test
  public void constructor_withHeaderOnly_shouldHaveHeightOne() {
    List<List<?>> data = Arrays.asList(
        Arrays.asList("col1", "col2", "col3")
    );

    TransformedTable table = new TransformedTable(data);

    assertThat(table.width(), equalTo(3));
    assertThat(table.height(), equalTo(1)); // Header-only creates one mapped row with null values
  }

  // ==================== getColumnNames tests ====================

  @Test
  public void getColumnNames_shouldReturnHeaderRow() {
    List<List<?>> data = Arrays.asList(
        Arrays.asList("firstName", "lastName", "email"),
        Arrays.asList("John", "Doe", "john@example.com")
    );

    TransformedTable table = new TransformedTable(data);
    Object[] columnNames = table.getColumnNames();

    assertThat(columnNames, arrayContaining("firstName", "lastName", "email"));
  }

  @Test
  public void getColumnNames_withEmptyTable_shouldReturnEmptyArray() {
    TransformedTable table = new TransformedTable(null);
    Object[] columnNames = table.getColumnNames();

    assertThat(columnNames, emptyArray());
  }

  // ==================== asMaps tests ====================

  @Test
  public void asMaps_shouldReturnListOfMaps() {
    List<List<?>> data = Arrays.asList(
        Arrays.asList("name", "value"),
        Arrays.asList("key1", "val1"),
        Arrays.asList("key2", "val2")
    );

    TransformedTable table = new TransformedTable(data);
    List<Map<Object, ?>> maps = table.asMaps();

    assertThat(maps, hasSize(2));
    assertThat(maps.get(0).get("name"), equalTo("key1"));
    assertThat(maps.get(0).get("value"), equalTo("val1"));
    assertThat(maps.get(1).get("name"), equalTo("key2"));
    assertThat(maps.get(1).get("value"), equalTo("val2"));
  }

  @Test
  public void asMaps_withHeaderOnly_shouldReturnMapWithNullValues() {
    List<List<?>> data = Arrays.asList(
        Arrays.asList("col1", "col2")
    );

    TransformedTable table = new TransformedTable(data);
    List<Map<Object, ?>> maps = table.asMaps();

    assertThat(maps, hasSize(1));
    assertThat(maps.get(0).get("col1"), is(nullValue()));
    assertThat(maps.get(0).get("col2"), is(nullValue()));
  }

  // ==================== asLists tests ====================

  @Test
  public void asLists_shouldReturnOriginalData() {
    List<List<?>> data = Arrays.asList(
        Arrays.asList("a", "b"),
        Arrays.asList("1", "2")
    );

    TransformedTable table = new TransformedTable(data);
    List<List<?>> result = table.asLists();

    assertThat(result, equalTo(data));
  }

  // ==================== getCell tests ====================

  @Test
  public void getCell_withValidIndices_shouldReturnValue() {
    List<List<?>> data = Arrays.asList(
        Arrays.asList("col1", "col2"),
        Arrays.asList("val1", "val2"),
        Arrays.asList("val3", "val4")
    );

    TransformedTable table = new TransformedTable(data);

    assertThat(table.getCell(0, 0), equalTo("col1"));
    assertThat(table.getCell(1, 1), equalTo("val2"));
    assertThat(table.getCell(2, 0), equalTo("val3"));
  }

  @Test
  public void getCell_withNegativeRow_shouldReturnNull() {
    List<List<?>> data = Arrays.asList(
        Arrays.asList("a"),
        Arrays.asList("b")
    );

    TransformedTable table = new TransformedTable(data);

    assertThat(table.getCell(-1, 0), is(nullValue()));
  }

  @Test
  public void getCell_withNegativeColumn_shouldReturnNull() {
    List<List<?>> data = Arrays.asList(
        Arrays.asList("a"),
        Arrays.asList("b")
    );

    TransformedTable table = new TransformedTable(data);

    assertThat(table.getCell(0, -1), is(nullValue()));
  }

  @Test
  public void getCell_withRowOutOfBounds_shouldReturnNull() {
    List<List<?>> data = Arrays.asList(
        Arrays.asList("a"),
        Arrays.asList("b")
    );

    TransformedTable table = new TransformedTable(data);

    assertThat(table.getCell(10, 0), is(nullValue()));
  }

  @Test
  public void getCell_withColumnOutOfBounds_shouldReturnNull() {
    List<List<?>> data = Arrays.asList(
        Arrays.asList("a"),
        Arrays.asList("b")
    );

    TransformedTable table = new TransformedTable(data);

    assertThat(table.getCell(0, 10), is(nullValue()));
  }

  // ==================== getRow tests ====================

  @Test
  public void getRow_withValidIndex_shouldReturnRow() {
    List<List<?>> data = Arrays.asList(
        Arrays.asList("header1", "header2"),
        Arrays.asList("row1col1", "row1col2"),
        Arrays.asList("row2col1", "row2col2")
    );

    TransformedTable table = new TransformedTable(data);

    assertThat(table.getRow(0), equalTo(Arrays.asList("header1", "header2")));
    assertThat(table.getRow(1), equalTo(Arrays.asList("row1col1", "row1col2")));
  }

  // ==================== columns(int) tests ====================

  @Test
  public void columns_withValidIndex_shouldReturnColumn() {
    List<List<?>> data = Arrays.asList(
        Arrays.asList("name", "age"),
        Arrays.asList("John", 30),
        Arrays.asList("Jane", 25)
    );

    TransformedTable table = new TransformedTable(data);
    List<Object> column = table.columns(0);

    assertThat(column, hasSize(2));
    assertThat(column, contains("John", "Jane"));
  }

  @Test
  public void columns_withSecondIndex_shouldReturnSecondColumn() {
    List<List<?>> data = Arrays.asList(
        Arrays.asList("name", "age"),
        Arrays.asList("John", 30),
        Arrays.asList("Jane", 25)
    );

    TransformedTable table = new TransformedTable(data);
    List<Object> column = table.columns(1);

    assertThat(column, hasSize(2));
    assertThat(column, contains(30, 25));
  }

  @Test
  public void columns_withNegativeIndex_shouldReturnNull() {
    List<List<?>> data = Arrays.asList(
        Arrays.asList("a"),
        Arrays.asList("b")
    );

    TransformedTable table = new TransformedTable(data);

    assertThat(table.columns(-1), is(nullValue()));
  }

  @Test
  public void columns_withOutOfBoundsIndex_shouldReturnNull() {
    List<List<?>> data = Arrays.asList(
        Arrays.asList("a"),
        Arrays.asList("b")
    );

    TransformedTable table = new TransformedTable(data);

    assertThat(table.columns(10), is(nullValue()));
  }

  // ==================== columns(int...) tests ====================

  @Test
  public void columns_withMultipleIndices_shouldReturnMultipleColumns() {
    List<List<?>> data = Arrays.asList(
        Arrays.asList("a", "b", "c"),
        Arrays.asList(1, 2, 3),
        Arrays.asList(4, 5, 6)
    );

    TransformedTable table = new TransformedTable(data);
    List<List<Object>> columns = table.columns(new int[]{0, 2});

    assertThat(columns, hasSize(2));
    assertThat(columns.get(0), contains(1, 4));
    assertThat(columns.get(1), contains(3, 6));
  }

  @Test
  public void columns_withInvalidIndicesInArray_shouldSkipInvalid() {
    List<List<?>> data = Arrays.asList(
        Arrays.asList("a", "b"),
        Arrays.asList(1, 2)
    );

    TransformedTable table = new TransformedTable(data);
    List<List<Object>> columns = table.columns(new int[]{0, 10, -1});

    assertThat(columns, hasSize(1)); // Only valid index 0
  }

  // ==================== columns(Object...) tests ====================

  @Test
  public void columns_withColumnNames_shouldReturnColumnValues() {
    List<List<?>> data = Arrays.asList(
        Arrays.asList("firstName", "lastName"),
        Arrays.asList("John", "Doe"),
        Arrays.asList("Jane", "Smith")
    );

    TransformedTable table = new TransformedTable(data);
    List<List<Object>> columns = table.columns("firstName");

    assertThat(columns, hasSize(1));
    assertThat(columns.get(0), contains("John", "Jane"));
  }

  @Test
  public void columns_withMultipleColumnNames_shouldReturnMultipleColumns() {
    List<List<?>> data = Arrays.asList(
        Arrays.asList("name", "city", "age"),
        Arrays.asList("John", "NYC", 30),
        Arrays.asList("Jane", "LA", 25)
    );

    TransformedTable table = new TransformedTable(data);
    List<List<Object>> columns = table.columns("name", "age");

    assertThat(columns, hasSize(2));
    assertThat(columns.get(0), contains("John", "Jane"));
    assertThat(columns.get(1), contains(30, 25));
  }

  @Test
  public void columns_withNonExistentColumnName_shouldReturnEmptyListForThatColumn() {
    List<List<?>> data = Arrays.asList(
        Arrays.asList("name", "age"),
        Arrays.asList("John", 30)
    );

    TransformedTable table = new TransformedTable(data);
    List<List<Object>> columns = table.columns("nonexistent");

    assertThat(columns, hasSize(1));
    assertThat(columns.get(0), is(empty()));
  }

  @Test
  public void columns_withNullKeys_shouldReturnEmptyList() {
    List<List<?>> data = Arrays.asList(
        Arrays.asList("name"),
        Arrays.asList("John")
    );

    TransformedTable table = new TransformedTable(data);
    List<List<Object>> columns = table.columns((Object[]) null);

    assertThat(columns, is(empty()));
  }

  // ==================== width and height tests ====================

  @Test
  public void width_shouldReturnNumberOfColumns() {
    List<List<?>> data = Arrays.asList(
        Arrays.asList("a", "b", "c", "d", "e"),
        Arrays.asList(1, 2, 3, 4, 5)
    );

    TransformedTable table = new TransformedTable(data);

    assertThat(table.width(), equalTo(5));
  }

  @Test
  public void height_shouldReturnNumberOfDataRows() {
    List<List<?>> data = Arrays.asList(
        Arrays.asList("header"),
        Arrays.asList("row1"),
        Arrays.asList("row2"),
        Arrays.asList("row3")
    );

    TransformedTable table = new TransformedTable(data);

    assertThat(table.height(), equalTo(3));
  }

  // ==================== Mixed data type tests ====================

  @Test
  public void asMaps_withMixedTypes_shouldPreserveTypes() {
    List<List<?>> data = Arrays.asList(
        Arrays.asList("string", "int", "double", "bool"),
        Arrays.asList("hello", 42, 3.14, true)
    );

    TransformedTable table = new TransformedTable(data);
    Map<Object, ?> row = table.asMaps().get(0);

    assertThat(row.get("string"), equalTo("hello"));
    assertThat(row.get("int"), equalTo(42));
    assertThat(row.get("double"), equalTo(3.14));
    assertThat(row.get("bool"), equalTo(true));
  }

  @Test
  public void getCell_withNullValue_shouldReturnNull() {
    List<List<?>> data = Arrays.asList(
        Arrays.asList("col"),
        Arrays.asList((Object) null)
    );

    TransformedTable table = new TransformedTable(data);

    assertThat(table.getCell(1, 0), is(nullValue()));
  }
}
