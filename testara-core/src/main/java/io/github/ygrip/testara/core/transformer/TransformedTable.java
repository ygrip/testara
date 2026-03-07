package io.github.ygrip.testara.core.transformer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.github.ygrip.testara.core.support.CommonHelper.isBlank;

/**
 * <p>TransformedTable class.</p>
 *
 * @author yunaz.ramadhan on 10/5/2019
 * @version $Id: $Id
 */
public class TransformedTable {
  private final List<List<?>> data;
  private final int height;
  private final int width;
  private final Object[] columnNames;
  private List<Map<Object, ?>> mappedData;

  /**
   * <p>Constructor for TransformedTable.</p>
   *
   * @param data a {@link List} object.
   */
  public TransformedTable(List<List<?>> data) {
    this.mappedData = new ArrayList<>();
    this.data = data;
    this.columnNames = isBlank(data) ? new String[0] : data.get(0).toArray();
    this.width = isBlank(this.columnNames) ? 0 : this.columnNames.length;
    this.constructMappedData();
    this.height = isBlank(this.mappedData) ? 0 : this.mappedData.size();
  }

  private void constructMappedData() {
    if (this.data == null || data.size() < 1) {
      this.mappedData = new ArrayList<>();
    } else if (this.data.size() == 1) {
      LinkedHashMap<Object, Object> row = new LinkedHashMap<>();
      for (Object columnName : this.columnNames) {
        row.put(columnName, null);
      }
      this.mappedData.add(row);
    } else {
      for (int i = 1; i < this.data.size(); i++) {
        LinkedHashMap<Object, Object> row = new LinkedHashMap<>();
        for (int j = 0; j < this.columnNames.length; j++) {
          row.put(this.columnNames[j], this.data.get(i).get(j));
        }
        this.mappedData.add(row);
      }
    }
  }

  /**
   * <p>asMaps.</p>
   *
   * @return a {@link List} object.
   */
  public List<Map<Object, ?>> asMaps() {
    return this.mappedData;
  }

  /**
   * <p>width.</p>
   *
   * @return a int.
   */
  public int width() {
    return this.width;
  }

  /**
   * <p>height.</p>
   *
   * @return a int.
   */
  public int height() {
    return this.height;
  }

  /**
   * <p>Getter for the field <code>columnNames</code>.</p>
   *
   * @return an array of {@link Object} objects.
   */
  public Object[] getColumnNames() {
    return this.columnNames;
  }

  /**
   * <p>asLists.</p>
   *
   * @return a {@link List} object.
   */
  public List<List<?>> asLists() {
    return this.data;
  }

  /**
   * <p>getCell.</p>
   *
   * @param row    a int.
   * @param column a int.
   * @return a {@link Object} object.
   */
  public Object getCell(int row, int column) {
    if (row >= 0 && column >= 0) {
      try {
        return this.data.get(row).get(column);
      } catch (Exception ignored) {
        return null;
      }
    }
    return null;
  }

  /**
   * <p>getRow.</p>
   *
   * @param row a int.
   * @return a {@link List} object.
   */
  public List<?> getRow(int row) {
    return this.data.get(row);
  }

  /**
   * <p>columns.</p>
   *
   * @param column a int.
   * @return a {@link List} object.
   */
  public List<Object> columns(int column) {
    if (column < this.width && column >= 0) {
      return columns(this.columnNames[column]).get(0);
    }
    return null;
  }

  /**
   * <p>columns.</p>
   *
   * @param column a int.
   * @return a {@link List} object.
   */
  public List<List<Object>> columns(int... column) {
    List<List<Object>> result = new ArrayList<>();
    for (int col : column) {
      if (col < this.width && col >= 0) {
        result.add(columns(col));
      }
    }
    return result;
  }

  /**
   * <p>columns.</p>
   *
   * @param keys a {@link Object} object.
   * @return a {@link List} object.
   */
  public List<List<Object>> columns(Object... keys) {
    List<List<Object>> result = new ArrayList<>();
    if (this.data != null && !this.data.isEmpty() && keys != null) {
      for (Object key : keys) {
        List<Object> col = new ArrayList<>();
        for (Map<Object, ?> row : this.mappedData) {
          if (row.containsKey(key)) {
            col.add(row.get(key));
          }
        }
        result.add(col);
      }
    }
    return result;
  }
}
