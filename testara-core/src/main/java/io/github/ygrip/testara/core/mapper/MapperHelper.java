package io.github.ygrip.testara.core.mapper;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.dataformat.xml.JacksonXmlModule;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import lombok.extern.log4j.Log4j2;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * <p>MapperHelperImpl class with improved performance</p>
 *
 * @author yunaz.ramadhan on 10/4/2019
 * @version $Id: $Id
 */
@Log4j2
public class MapperHelper {
  private static final ReentrantLock mapperLock = new ReentrantLock();
  private static final ReentrantLock xmlMapperLock = new ReentrantLock();
  private static volatile ObjectMapper SHARED_OBJECT_MAPPER;
  private static volatile XmlMapper SHARED_XML_MAPPER;

  /**
   * <p>Constructor for MapperHelper.</p>
   * Hides so no other can initialize it.
   */
  private MapperHelper() {

  }

  /**
   * Get or create ObjectMapper lazily (thread-safe double-check locking)
   */
  public static ObjectMapper getObjectMapper() {
    ObjectMapper result = SHARED_OBJECT_MAPPER;
    if (result == null) {
      mapperLock.lock();
      try {
        if (SHARED_OBJECT_MAPPER == null) {
          SHARED_OBJECT_MAPPER = createOptimizedObjectMapper();
          log.debug("MapperHelper ObjectMapper initialized (memory-optimized, lazy loading)");
        }
        result = SHARED_OBJECT_MAPPER;
      } finally {
        mapperLock.unlock();
      }
    }
    return result;
  }

  /**
   * Get or create XmlMapper lazily (thread-safe double-check locking)
   * This defers initialization until first use, saving ~10-15 MB at startup
   */
  public static XmlMapper getXmlMapper() {
    XmlMapper result = SHARED_XML_MAPPER;
    if (result == null) {
      xmlMapperLock.lock();
      try {
        if (SHARED_XML_MAPPER == null) {
          SHARED_XML_MAPPER = createOptimizedXmlMapper();
          log.debug("MapperHelper XmlMapper initialized (memory-optimized, lazy loading)");
        }
        result = SHARED_XML_MAPPER;
      } finally {
        xmlMapperLock.unlock();
      }
    }
    return result;
  }

  /**
   * Create optimized ObjectMapper with performance-focused configuration
   */
  private static ObjectMapper createOptimizedObjectMapper() {
    ObjectMapper mapper = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.ALWAYS)
        .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
        // Optimized serialization settings
        .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
        .configure(SerializationFeature.FLUSH_AFTER_WRITE_VALUE, false) // Better performance
        .configure(SerializationFeature.INDENT_OUTPUT, false) // Faster processing, enable only when needed
        // Optimized deserialization settings
        .configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false)
        .configure(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES, false)
        .configure(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES, false)
        .configure(DeserializationFeature.FAIL_ON_UNRESOLVED_OBJECT_IDS, false)
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    // Extension point
    ServiceLoader<JacksonModuleContributor> loader = ServiceLoader.load(JacksonModuleContributor.class);

    for (JacksonModuleContributor contributor : loader) {
      contributor.contribute(mapper);
    }

    return mapper;
  }

  /**
   * Create optimized XmlMapper with performance-focused configuration
   */
  private static XmlMapper createOptimizedXmlMapper() {
    JacksonXmlModule xmlModule = new JacksonXmlModule();
    xmlModule.setDefaultUseWrapper(false);

    XmlMapper mapper = new XmlMapper(xmlModule);
    mapper.setSerializationInclusion(JsonInclude.Include.ALWAYS);
    mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
    // Optimized settings for XML processing
    mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    mapper.configure(SerializationFeature.FLUSH_AFTER_WRITE_VALUE, false);
    mapper.configure(SerializationFeature.INDENT_OUTPUT, false); // Faster XML processing
    mapper.configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false);
    mapper.configure(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES, false);
    mapper.configure(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES, false);
    mapper.configure(DeserializationFeature.FAIL_ON_UNRESOLVED_OBJECT_IDS, false);
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    // Extension point
    ServiceLoader<JacksonModuleContributor> loader = ServiceLoader.load(JacksonModuleContributor.class);

    for (JacksonModuleContributor contributor : loader) {
      contributor.contribute(mapper);
    }

    return mapper;
  }

  public static TypeFactory getTypeFactory() {
    return getObjectMapper().getTypeFactory();
  }

  public static JavaType getGenericType(Field field) {
    return getObjectMapper().getTypeFactory().constructType(field.getGenericType());
  }

  public static JavaType getGenericType(Class<?> clazz) {
    return getObjectMapper().getTypeFactory().constructType(clazz);
  }

  public static JavaType getGenericType(TypeReference<?> typeReference) {
    return getObjectMapper().getTypeFactory().constructType(typeReference);
  }

  public static JavaType getGenericType(Type type) {
    return getObjectMapper().getTypeFactory().constructType(type);
  }

  public static String toString(Object obj) {

    try {
      if (obj == null) {
        return null;
      }

      if (obj instanceof String) {
        return (String) obj;
      }

      log.trace("#processing : mapping object to json string, type: {}", obj.getClass().getSimpleName());
      return getObjectMapper().writeValueAsString(obj);

    } catch (JsonProcessingException e) {
      log.debug("#ERROR processing toString() for object of type {}: {}",
          obj.getClass().getSimpleName(),
          e.getMessage());
      return null;
    }
  }

  @SuppressWarnings("unchecked")
  public static <T> T toObject(Object obj, Type type) {

    try {
      if (obj == null) {
        return null;
      }

      log.trace("#processing : mapping object to type {}", type);

      JavaType javaType = getObjectMapper().getTypeFactory().constructType(type);

      // Handle String target type efficiently
      if (String.class.isAssignableFrom(javaType.getRawClass())) {
        return (T) toString(obj);
      }

      // Direct conversion for compatible types (avoid string conversion)
      if (obj instanceof Map || obj instanceof List || obj.getClass().equals(javaType.getRawClass())) {
        try {
          return getObjectMapper().convertValue(obj, javaType);
        } catch (IllegalArgumentException e) {
          log.debug("Direct conversion failed, falling back to string conversion: {}", e.getMessage());
        }
      }

      // Fallback to string conversion
      String jsonString = toString(obj);
      if (jsonString == null) {
        return null;
      }

      return getObjectMapper().readValue(jsonString, javaType);

    } catch (IOException e) {
      log.debug("#ERROR processing toObject() for type {}: {}", type, e.getMessage());
      return null;
    } finally {
      long endTime = System.nanoTime();
    }
  }

  @SuppressWarnings("unchecked")
  public static <T> T toObject(Object obj, Class<T> tClass) {

    try {
      if (obj == null) {
        return null;
      }

      log.trace("#processing : mapping object to class {}", tClass.getSimpleName());

      // Handle String target type efficiently
      if (tClass.equals(String.class)) {
        return (T) toString(obj);
      }

      // Direct conversion for compatible types (avoid string conversion)
      if (obj instanceof Map || obj instanceof List || obj.getClass().equals(tClass)) {
        try {
          return getObjectMapper().convertValue(obj, tClass);
        } catch (IllegalArgumentException e) {
          log.debug("Direct conversion failed, falling back to string conversion: {}", e.getMessage());
        }
      }

      // Fallback to string conversion
      String jsonString = toString(obj);
      if (jsonString == null) {
        return null;
      }

      return getObjectMapper().readValue(jsonString, tClass);

    } catch (IOException e) {
      log.debug("#ERROR processing toObject() for class {}: {}", tClass.getSimpleName(), e.getMessage());
      return null;
    }
  }

  @SuppressWarnings("unchecked")
  public static <T> T toObject(Object obj, JavaType javaType) {

    try {
      if (obj == null) {
        return null;
      }

      log.trace("#processing : mapping object to javaType {}", javaType);

      // Handle String target type efficiently
      if (String.class.isAssignableFrom(javaType.getRawClass())) {
        return (T) toString(obj);
      }

      // Direct conversion for compatible types (avoid string conversion)
      if (obj instanceof Map || obj instanceof List || obj.getClass().equals(javaType.getRawClass())) {
        try {
          return getObjectMapper().convertValue(obj, javaType);
        } catch (IllegalArgumentException e) {
          log.debug("Direct conversion failed, falling back to string conversion: {}", e.getMessage());
        }
      }

      // Fallback to string conversion
      String jsonString = toString(obj);
      if (jsonString == null) {
        return null;
      }

      return getObjectMapper().readValue(jsonString, javaType);

    } catch (IOException e) {
      log.debug("#ERROR processing toObject() for javaType {}: {}", javaType, e.getMessage());
      return null;
    }
  }

  @SuppressWarnings("unchecked")
  public static <T> T toObject(Object obj, TypeReference<T> reference) {

    try {
      if (obj == null) {
        return null;
      }

      log.trace("#processing : mapping object to type reference {}", reference);

      JavaType javaType = getGenericType(reference);

      // Handle String target type efficiently
      if (String.class.isAssignableFrom(javaType.getRawClass())) {
        return (T) toString(obj);
      }

      // Direct conversion for compatible types (avoid string conversion)
      if (obj instanceof Map || obj instanceof List) {
        try {
          return getObjectMapper().convertValue(obj, reference);
        } catch (IllegalArgumentException e) {
          log.debug("Direct conversion failed, falling back to string conversion: {}", e.getMessage());
        }
      }

      // Fallback to string conversion
      String jsonString = toString(obj);
      if (jsonString == null) {
        return null;
      }

      return getObjectMapper().readValue(jsonString, reference);

    } catch (IOException e) {
      log.debug("#ERROR processing toObject() for type reference {}: {}", reference, e.getMessage());
      return null;
    }
  }

  public static String xmlToJsonNodeString(String xmlString) {

    try {
      if (xmlString == null || xmlString.trim().isEmpty()) {
        return null;
      }

      log.trace("#processing : mapping xml string to json string, length: {}", xmlString.length());
      return toString(getXmlMapper().readValue(xmlString, LinkedHashMap.class));

    } catch (IOException e) {
      log.debug("#ERROR processing xmlToJsonString() for xml length {}: {}", xmlString.length(), e.getMessage());
      return null;
    }
  }

  public static String xmlToJsonArrayNodeString(String xmlString) {

    try {
      if (xmlString == null || xmlString.trim().isEmpty()) {
        return null;
      }

      log.trace("#processing : mapping xml string to json array string, length: {}", xmlString.length());
      return toString(getXmlMapper().readValue(xmlString, List.class));

    } catch (IOException e) {
      log.debug("#ERROR processing xmlToJsonArrayString() for xml length {}: {}", xmlString.length(), e.getMessage());
      return null;
    }
  }

  public static String toXmlString(Object obj) {
    try {
      if (obj == null) {
        return null;
      }

      log.trace("#processing : mapping object to xml string, type: {}", obj.getClass().getSimpleName());
      return getXmlMapper().writeValueAsString(obj);

    } catch (JsonProcessingException e) {
      log.debug("#ERROR processing toXmlString() for object type {}: {}",
          obj.getClass().getSimpleName(),
          e.getMessage());
      return null;
    }
  }

  public static String xmlToJsonNodeString(File file) {
    try {
      if (file == null || !file.exists()) {
        return null;
      }

      log.trace("#processing : mapping xml file to json string, file: {}", file.getAbsolutePath());
      return toString(getXmlMapper().readValue(file, LinkedHashMap.class));

    } catch (IOException e) {
      log.debug("#ERROR processing xmlToJsonString() for file {}: {}", file.getAbsolutePath(), e.getMessage());
      return null;
    }
  }

  public static String xmlToJsonArrayNodeString(File file) {
    try {
      if (file == null || !file.exists()) {
        return null;
      }

      log.trace("#processing : mapping xml file to json array string, file: {}", file.getAbsolutePath());
      return toString(getXmlMapper().readValue(file, List.class));

    } catch (IOException e) {
      log.debug("#ERROR processing xmlToJsonArrayString() for file {}: {}", file.getAbsolutePath(), e.getMessage());
      return null;
    }
  }

  public static <T> T xmlToObject(File file, Type type) {

    try {
      if (file == null || !file.exists()) {
        return null;
      }

      log.trace("#processing : mapping xml file to object type {}, file: {}", type, file.getAbsolutePath());
      return getXmlMapper().readValue(file, getXmlMapper().getTypeFactory().constructType(type));

    } catch (IOException e) {
      log.debug("#ERROR processing xmlToObject() for file {} and type {}: {}",
          file.getAbsolutePath(),
          type,
          e.getMessage());
      return null;
    }
  }

  public static <T> T xmlToObject(String xmlString, Type type) {

    try {
      if (xmlString == null || xmlString.trim().isEmpty()) {
        return null;
      }

      log.trace("#processing : mapping xml string to object type {}, length: {}", type, xmlString.length());
      return getXmlMapper().readValue(xmlString, getXmlMapper().getTypeFactory().constructType(type));

    } catch (IOException e) {
      log.debug("#ERROR processing xmlToObject() for xml length {} and type {}: {}",
          xmlString.length(),
          type,
          e.getMessage());
      return null;
    }
  }

  public static <T> T xmlToObject(File file, Class<T> tClass) {
    try {
      if (file == null || !file.exists()) {
        return null;
      }

      log.trace("#processing : mapping xml file to object class {}, file: {}",
          tClass.getSimpleName(),
          file.getAbsolutePath());
      return getXmlMapper().readValue(file, tClass);

    } catch (IOException e) {
      log.debug("#ERROR processing xmlToObject() for file {} and class {}: {}",
          file.getAbsolutePath(),
          tClass.getSimpleName(),
          e.getMessage());
      return null;
    }
  }

  @SuppressWarnings("unchecked")
  public static <T> T xmlToObject(String xmlString, Class<T> tClass) {

    try {
      if (xmlString == null || xmlString.trim().isEmpty()) {
        return null;
      }

      log.trace("#processing : mapping xml string to object class {}, length: {}",
          tClass.getSimpleName(),
          xmlString.length());

      // Handle String target type efficiently
      if (tClass.equals(String.class)) {
        return (T) xmlString;
      }

      return getXmlMapper().readValue(xmlString, tClass);

    } catch (IOException e) {
      log.debug("#ERROR processing xmlToObject() for xml length {} and class {}: {}",
          xmlString.length(),
          tClass.getSimpleName(),
          e.getMessage());
      return null;
    }
  }

  public static <T> T xmlToObject(File file, TypeReference<T> typeReference) {

    try {
      if (file == null || !file.exists()) {
        return null;
      }

      log.trace("#processing : mapping xml file to object type reference, file: {}", file.getAbsolutePath());
      return getXmlMapper().readValue(file, typeReference);

    } catch (IOException e) {
      log.debug("#ERROR processing xmlToObject() for file {} and type reference: {}",
          file.getAbsolutePath(),
          e.getMessage());
      return null;
    }
  }

  @SuppressWarnings("unchecked")
  public static <T> T xmlToObject(String xmlString, TypeReference<T> typeReference) {

    try {
      if (xmlString == null || xmlString.trim().isEmpty()) {
        return null;
      }

      log.trace("#processing : mapping xml string to object type reference, length: {}", xmlString.length());

      JavaType javaType = getGenericType(typeReference);

      // Handle String target type efficiently
      if (String.class.isAssignableFrom(javaType.getRawClass())) {
        return (T) xmlString;
      }

      return getXmlMapper().readValue(xmlString, typeReference);

    } catch (IOException e) {
      log.debug("#ERROR processing xmlToObject() for xml length {} and type reference: {}",
          xmlString.length(),
          e.getMessage());
      return null;
    }
  }

  /**
   * Bulk conversion method for better performance when converting multiple objects
   *
   * @param objects     List of objects to convert
   * @param targetClass Target class for conversion
   * @return Map with original object as key and converted object as value
   */
  public static <T> Map<Object, T> toBulkObject(List<Object> objects, Class<T> targetClass) {
    Map<Object, T> results = new ConcurrentHashMap<>();

    if (objects == null || objects.isEmpty()) {
      return results;
    }

    for (Object obj : objects) {
      try {
        T converted = toObject(obj, targetClass);
        if (converted != null) {
          results.put(obj, converted);
        }
      } catch (Exception e) {
        log.debug("Failed to convert object in bulk operation: {}", e.getMessage());
      }
    }

    return results;
  }
}
