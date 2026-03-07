package io.github.ygrip.testara.core.transformer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.core.file.FileHelper;
import io.github.ygrip.testara.core.mapper.MapperHelper;
import io.github.ygrip.testara.core.model.DefaultProperties;
import io.github.ygrip.testara.core.converter.ObjectConverter;
import io.github.ygrip.testara.core.converter.ObjectConverterLoader;
import io.github.ygrip.testara.core.support.CommonHelper;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import lombok.extern.log4j.Log4j2;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static io.github.ygrip.testara.core.support.CommonHelper.isBlank;
import static io.github.ygrip.testara.core.support.CommonHelper.isPrimitiveType;
import static io.github.ygrip.testara.core.support.CommonHelper.parseStringToObject;

/**
 * <p>Optimized TransformerService class.</p>
 * Enhanced version with improved memory management, efficient caching,
 * and optimized transformation logic for better performance.
 *
 * @author yunaz.ramadhan on 10/4/2019
 * @version $Id: $Id
 */
@Log4j2
public class TransformerService {
  // Configuration constants
  private final String DEFAULT_TEMPLATE_FOLDER;
  private final Configuration config;
  private final ObjectConverter parser;
  private Object baseObject;
  private TransformedTable processedTable;
  private Integer[] includedRows;
  private Integer[] excludedRows;
  private boolean processed;

  /**
   * <p>Constructor for TransformerService.</p>
   */
  public TransformerService() {
    this.parser = ObjectConverterLoader.instance();
    this.baseObject = new LinkedHashMap<>();
    this.includedRows = new Integer[] {};
    this.excludedRows = new Integer[] {};

    String templateFolder = null;
    try {
      templateFolder = TestFramework.context().configuration().get(DefaultProperties.class).getTemplateFolder();
    } catch (Exception ignored) {
      // Use default
    }
    this.DEFAULT_TEMPLATE_FOLDER = String.format("%s%s",
        System.getProperty("user.dir"),
        isBlank(templateFolder) ? "/src/test/resources/" : templateFolder);

    this.config = Configuration.defaultConfiguration()
        .addOptions(Option.DEFAULT_PATH_LEAF_TO_NULL)
        .addOptions(Option.SUPPRESS_EXCEPTIONS);

    this.processed = false;

    log.debug("TransformerService initialized with optimized caching");
  }

  /**
   * <p>Getter for the field <code>processedTable</code>.</p>
   *
   * @return a {@link TransformedTable} object.
   */
  public TransformedTable getProcessedTable() {
    return this.processedTable;
  }

  /**
   * Set and construct the base object from template provided
   *
   * @param templateName is the name of the template file
   * @return instance of TransformerService
   */
  public TransformerService fromTemplate(String templateName) {
    if (isBlank(templateName)) {
      setTemplate("{}");
      return this;
    }

    log.debug("#Processing : using template {}", templateName);

    final String DEFAULT_FORMAT = "json";
    final String path = String.format("%s%s.%s", DEFAULT_TEMPLATE_FOLDER, templateName, DEFAULT_FORMAT);
    try {
      String templateContent = FileHelper.readFile(path);
      setTemplate(templateContent);
      log.debug("Successfully loaded template from: {}", path);
    } catch (Exception e) {
      log.error("#ERROR : could not find specified template at : {}, error: {}", path, e.getMessage());
      setTemplate("{}");
    }
    return this;
  }

  /**
   * <p>Setter for the field <code>template</code>.</p>
   *
   * @param jsonText a {@link String} object.
   * @return a {@link TransformerService} object.
   */
  public TransformerService setTemplate(String jsonText) {
    if (jsonText == null || jsonText.trim().isEmpty()) {
      this.baseObject = parseStringToObject("{}");
    } else {
      this.baseObject = parseStringToObject(jsonText);
    }
    return this;
  }

  public TransformerService resetTemplate() {
    this.baseObject = new LinkedHashMap<>();
    return this;
  }

  /**
   * Get data from template with optimized processing
   */
  @SuppressWarnings("unchecked")
  private List<Map<Object, ?>> getDataFromTemplate() {
    List<Map<Object, ?>> merged = new ArrayList<>();
    List<Map<Object, ?>> data = new ArrayList<>();
    if (!isBlank(this.processedTable)) {
      data = this.processedTable.asMaps();
    }
    if (!isBlank(this.baseObject) && !isBlank(data)) {
      final String json = MapperHelper.toString(this.baseObject);
      for (Map<Object, ?> element : data) {
        DocumentContext document = JsonPath.using(config).parse(json);
        for (Object key : element.keySet()) {
          try {
            document.set(JsonPath.compile(String.valueOf(key)), element.get(key));
          } catch (Exception e) {
            log.debug("Failed to set JSON path for key '{}': {}", key, e.getMessage());
          }
        }

        Object parsed = parser.convert(document.json());
        if (CommonHelper.isCollection(parsed)) {
          merged = (List<Map<Object, ?>>) parsed;
        } else if (parsed instanceof HashMap) {
          merged.add((Map<Object, ?>) parsed);
        }
      }
    } else if (!isBlank(data) && isBlank(this.baseObject)) {
      merged = data;
    } else if (isBlank(data) && !isBlank(this.baseObject)) {
      Object parsed = parser.convert(this.baseObject);

      if (CommonHelper.isCollection(parsed)) {
        merged = (List<Map<Object, ?>>) parsed;
      } else if (this.baseObject instanceof HashMap) {
        merged.add((Map<Object, ?>) parsed);
      }
    }
    return merged;
  }

  /**
   * Set the DataTable as source data to transform with caching
   *
   * @param rawTable is the raw data / two-dimensional array of string [][]
   * @return instance of TransformerService
   */
  public TransformerService sourceData(List<List<String>> rawTable) {
    if (CommonHelper.isBlank(rawTable)) {
      this.processedTable = null;
      this.processed = false;
      return this;
    } else {
      this.processed = false;
      this.processedTable = processTable(rawTable);
      log.debug("Updated source data with {} rows", rawTable.size());
    }
    return this;
  }

  /**
   * Set the included rows from data table to be processed
   *
   * @param rows is arrays of integer indicating the rows
   * @return instance of TransformerService
   */
  public TransformerService includeRows(Integer... rows) {
    if (rows == null) {
      this.includedRows = new Integer[0];
    } else {
      this.includedRows = rows;
      log.debug("Set included rows: {}", Arrays.toString(rows));
    }
    return this;
  }

  /**
   * Set the excluded rows from data table to be ignored
   *
   * @param rows is arrays of integer indicating the row
   * @return instance of TransformerService
   */
  public TransformerService excludeRows(Integer... rows) {
    if (rows == null) {
      this.excludedRows = new Integer[0];
    } else {
      this.excludedRows = rows;
      log.debug("Set excluded rows: {}", Arrays.toString(rows));
    }
    return this;
  }

  /**
   * Method to transform the DataTable object to the specified field type
   *
   * @param field the field specified by user
   * @param <T>   is generic type of data
   * @return Generic data type
   * @throws Exception when there are failure during method execution
   */
  public <T> T to(Field field) throws Exception {
    if (field == null) {
      throw new IllegalArgumentException("Field cannot be null");
    }

    log.debug("#processing : transform data to Field {}", field.getName());
    T result = null;
    final JavaType fieldType = MapperHelper.getGenericType(field);
    try {
      result = to(fieldType);
      log.debug("Successfully transformed data to field type: {}", field.getType());
    } catch (Exception e) {
      log.error("#ERROR : when transforming to field {} ({}), error: {}", field, field.getType(), e.getMessage(), e);
      throw e; // Re-throw to maintain existing behavior
    }
    return result;
  }

  /**
   * Method to transform DataTable to list of respective target class type data
   *
   * @param targetClass is the generic class provided by user
   * @param <T>         is generic type of data
   * @return list of generic data type
   */
  public <T> List<T> toList(Class<T> targetClass) {
    if (targetClass == null) {
      log.error("Target class cannot be null");
      return new ArrayList<>();
    }

    log.debug("#processing : transform data to list of {}", targetClass);
    List<T> result = new ArrayList<>();
    List<Map<Object, ?>> merged = getDataFromTemplate();
    if (!isBlank(merged)) {
      try {
        for (Map<Object, ?> data : merged) {
          T item = MapperHelper.toObject(data, targetClass);
          if (item != null) {
            result.add(item);
          }
        }
        log.debug("Successfully transformed {} items to list of {}", result.size(), targetClass.getSimpleName());
      } catch (Exception e) {
        log.error("#ERROR : when transforming to list of {}, error: {}", targetClass, e.getMessage(), e);
      }
    }

    return result;
  }

  /**
   * Method to transform DataTable to list of respective target class type data
   *
   * @return list of generic data type
   */
  public List<List<Object>> toCells() {
    log.debug("#processing : transform data to multi array list");
    List<List<Object>> result = new ArrayList<>();
    if (!isBlank(this.processedTable)) {
      try {
        List<List<?>> datas = this.processedTable.asLists();
        for (List<?> items : datas) {
          List<Object> row = new ArrayList<>(items);
          result.add(row);
        }
      } catch (Exception e) {
        log.error("#ERROR : when transforming to multi array data, log : ", e);
      }
    }
    return result;
  }

  /**
   * Method to transform DataTable to respective target class type data
   *
   * @param targetClass is the generic class provided by user
   * @param <T>         is generic type of data
   * @return generic data type
   */
  public <T> T to(Class<T> targetClass) {
    if (targetClass == null) {
      log.error("Target class cannot be null");
      return null;
    }
    return to(MapperHelper.getGenericType(targetClass));
  }

  /**
   * Method to transform DataTable to respective type reference
   *
   * @param typeReference is the typereference provided by user
   * @param <T>           is generic type of data
   * @return generic data type
   */
  public <T> T to(TypeReference<T> typeReference) {
    if (typeReference == null) {
      log.error("Type reference cannot be null");
      return null;
    }
    return to(MapperHelper.getGenericType(typeReference));
  }

  /**
   * Method to transform DataTable to respective type reference
   *
   * @param javaType is the java type provided by user
   * @param <T>      is generic type of data
   * @return generic data type
   */
  public <T> T to(JavaType javaType) {
    try {
      validateInput(javaType);
      log.debug("#processing : transform data to type of {}", javaType);

      List<Map<Object, ?>> merged = getDataFromTemplate();

      // Use improved logic with better error handling
      return performTransformation(merged, javaType);
    } catch (Exception e) {
      log.error("#ERROR : when executing transform to type {}, log : ", javaType, e);
      return null;
    }
  }

  /**
   * Validates the input JavaType parameter.
   *
   * @param javaType the JavaType to validate
   * @throws IllegalArgumentException if the input is invalid
   */
  private void validateInput(JavaType javaType) throws IllegalArgumentException {
    if (javaType == null) {
      throw new IllegalArgumentException("JavaType cannot be null");
    }
    if (javaType.getRawClass() == null) {
      throw new IllegalArgumentException("JavaType raw class cannot be null");
    }
  }

  /**
   * Performs the actual transformation based on the target type.
   * This method replaces the complex nested conditional logic with cleaner approach.
   *
   * @param merged   the merged data from template and table
   * @param javaType the target Java type
   * @param <T>      the target type
   * @return the transformed object
   */
  private <T> T performTransformation(List<Map<Object, ?>> merged, JavaType javaType) {
    T result;

    if (Collection.class.isAssignableFrom(javaType.getRawClass()) || javaType.getRawClass().isArray()) {
      result = transformToCollection(merged, javaType);
    } else if (Map.class.isAssignableFrom(javaType.getRawClass())) {
      result = transformToMap(merged, javaType);
    } else if (isPrimitiveType(javaType.getRawClass())) {
      result = transformToPrimitive(javaType);
    } else {
      if (this.baseObject != null) {
        if (Collection.class.isAssignableFrom(this.baseObject.getClass())) {
          result = transformToCollection(merged, javaType);
        } else {
          result = transformToObject(merged, javaType);
        }
      } else {
        result = transformToObject(merged, javaType);
      }
    }

    return result;
  }

  /**
   * Transform data to Collection or Array type.
   */
  private <T> T transformToCollection(List<Map<Object, ?>> merged, JavaType javaType) {
    JavaType innerType = javaType.getContentType();
    if (innerType != null) {
      if (Collection.class.isAssignableFrom(innerType.getRawClass()) || innerType.getRawClass().isArray()
          || Map.class.isAssignableFrom(innerType.getRawClass())) {
        return MapperHelper.toObject(merged, javaType);
      } else if (isPrimitiveType(innerType.getRawClass()) || String.class.isAssignableFrom(innerType.getRawClass())
          || Long.class.isAssignableFrom(innerType.getRawClass())
          || Double.class.isAssignableFrom(innerType.getRawClass())
          || Float.class.isAssignableFrom(innerType.getRawClass())
          || Boolean.class.isAssignableFrom(innerType.getRawClass())
          || Byte.class.isAssignableFrom(innerType.getRawClass())
          || Character.class.isAssignableFrom(innerType.getRawClass())
          || Short.class.isAssignableFrom(innerType.getRawClass())
          || Number.class.isAssignableFrom(innerType.getRawClass())
          || Integer.class.isAssignableFrom(innerType.getRawClass())) {
        return transformFallbackCollection(merged, javaType);
      } else {
        return transformFallbackCollection(merged, javaType);
      }
    } else {
      return transformFallbackCollection(merged, javaType);
    }
  }

  private <T> T transformFallbackCollection(List<Map<Object, ?>> merged, JavaType javaType) {
    if (this.processedTable != null && this.processedTable.width() == 1) {
      List<Object> data = new ArrayList<>();
      data.add(this.processedTable.getColumnNames()[0]);
      if (this.processedTable.height() >= 1) {
        for (Object elem : this.processedTable.columns(0)) {
          if (!isBlank(elem, false)) {
            data.add(elem);
          }
        }
      }
      return MapperHelper.toObject(data, javaType);
    } else {
      return MapperHelper.toObject(merged, javaType);
    }
  }

  /**
   * Transform data to Map type.
   */
  private <T> T transformToMap(List<Map<Object, ?>> merged, JavaType javaType) {
    if (this.processedTable != null && this.processedTable.width() == 2 && (
        this.processedTable.getRow(0).contains("key") && this.processedTable.getRow(0).contains("value"))) {
      return MapperHelper.toObject(toMap(), javaType);
    } else if (merged != null && !merged.isEmpty()) {
      return MapperHelper.toObject(merged.get(merged.size() - 1), javaType);
    } else {
      return MapperHelper.toObject(new HashMap<>(), javaType);
    }
  }

  /**
   * Transform data to primitive type.
   */
  private <T> T transformToPrimitive(JavaType javaType) {
    if (this.processedTable != null && this.processedTable.height() > 0) {
      return MapperHelper.toObject(this.processedTable.getCell(this.processedTable.height() - 1, 0), javaType);
    } else {
      return null;
    }
  }

  /**
   * Transform data to Object type with optimized processing.
   */
  private <T> T transformToObject(List<Map<Object, ?>> merged, JavaType javaType) {
    if (merged != null && !merged.isEmpty()) {
      return MapperHelper.toObject(merged.get(merged.size() - 1), javaType);
    } else {
      return null;
    }
  }

  /**
   * Method to transform DataTable to map object, this will only use the column identified by user
   *
   * @param identifierKey   is the key column name
   * @param identifierValue is the value column name
   * @return map object
   */
  public Map<String, Object> toMap(Object identifierKey, Object identifierValue) {
    if (identifierKey == null) {
      log.error("Identifier key cannot be null");
      return new HashMap<>();
    }
    if (identifierValue == null) {
      log.error("Identifier value cannot be null");
      return new HashMap<>();
    }

    log.debug("#processing : transform data to map of object");
    Map<String, Object> result = new HashMap<>();

    try {
      List<Map<Object, ?>> data = getDataFromTemplate();
      if (data != null && !data.isEmpty() && data.get(0).containsKey(identifierKey) && data.get(0)
          .containsKey(identifierValue)) {
        for (Map<Object, ?> item : data) {
          Object key = item.getOrDefault(identifierKey, null);
          Object value = item.getOrDefault(identifierValue, null);
          result.put(key == null ? "" : String.valueOf(key), value);
        }
        log.debug("Successfully transformed {} entries to map", result.size());
      }
    } catch (Exception e) {
      log.error("#ERROR : when executing transform to Map, error: {}", e.getMessage(), e);
    }

    return result;
  }

  /**
   * Method to transform DataTable to map object, this will only use first two column in data table
   * as reference, others will be ignored
   *
   * @return map object
   */
  public Map<String, Object> toMap() {
    if (isBlank(this.processedTable)) {
      return new HashMap<>();
    }
    if (this.processedTable.width() > 2) {
      return to(new TypeReference<>() {
      });
    } else if (this.processedTable.width() == 2) {
      Object identifierKey = this.processedTable.getColumnNames()[0];
      Object identifierValue = this.processedTable.getColumnNames()[this.processedTable.width() - 1];
      return toMap(identifierKey, identifierValue);
    } else {
      return new HashMap<>();
    }
  }

  /**
   * Process table with caching and optimized processing
   */
  private TransformedTable processTable(List<List<String>> rawTable) {
    if (!this.processed) {
      if (!isBlank(rawTable)) {

        List<List<?>> cleanData;

        cleanData = IntStream.range(0, rawTable.size())
            .filter(i -> i == 0 || (processInclusion(i) && processExclusion(i)))
            .mapToObj(i -> rawTable.get(i).stream().map(parser::convertFromCache).collect(Collectors.toList()))
            .collect(Collectors.toList());

        this.processedTable = new TransformedTable(cleanData);
      }
    }
    this.processed = true;
    return this.processedTable;
  }

  private boolean processInclusion(int index) {
    return this.includedRows == null || this.includedRows.length == 0 || Arrays.asList(this.includedRows)
        .contains(index);
  }

  private boolean processExclusion(int index) {
    return this.excludedRows == null || this.excludedRows.length == 0 || !Arrays.asList(this.excludedRows)
        .contains(index);
  }

  public boolean hasData() {
    return !CommonHelper.isBlank(this.getProcessedTable()) || !CommonHelper.isBlank(this.baseObject);
  }
}
