package io.github.ygrip.testara.core.json;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.ygrip.testara.core.error.JsonException;
import io.github.ygrip.testara.core.mapper.MapperHelper;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.JsonPathException;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.TypeRef;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;

import lombok.extern.log4j.Log4j2;

/**
 * @author yunaz.ramadhan on 10/4/2019
 * @version $Id: $Id
 */
@Log4j2
public final class JsonHelper {

  // Thread-safe configuration shared across all instances
  private static final Configuration SHARED_CONFIG = Configuration.builder()
    .jsonProvider(new JacksonJsonProvider())
    .mappingProvider(new JacksonMappingProvider())
    .options(Option.DEFAULT_PATH_LEAF_TO_NULL, Option.SUPPRESS_EXCEPTIONS)
    .build();

  // Thread-local DocumentContext to ensure thread safety
  private static final ThreadLocal<JsonPathHolder> threadLocalContext = new ThreadLocal<>();


  /**
   * <p>Constructor for JsonPathHelper.</p>
   */
  private JsonHelper() {
  }

  /**
   * Get compiled JsonPath from cache or create new one
   */
  private static JsonPath getCompiledPath(String path) {
    return JsonPath.compile(path);
  }

  public static JsonPathHolder instance() {
    JsonPathHolder instance = threadLocalContext.get();
    if (instance == null) {
      instance = new JsonPathHolder();
      threadLocalContext.set(instance);
    }
    return instance;
  }

  public static JsonPathChecker check(String json) {
    return new JsonPathChecker().check(json);
  }

  public static JsonPathChecker check(Object json) {
    return new JsonPathChecker().check(json);
  }

  /**
   * Clear the current context (useful for memory management)
   */
  public void clearContext() {
    threadLocalContext.remove();
    log.trace("Context cleared for current thread");
  }

  public static class JsonPathChecker {
    private DocumentContext context;

    JsonPathChecker() {

    }

    public JsonPathChecker check(String jsonString) {

      try {
        if (jsonString == null || jsonString.trim()
          .isEmpty()) {
          jsonString = "{}";
        }

        this.context = JsonPath.using(Configuration.defaultConfiguration())
          .parse(jsonString);

      } catch (Exception e) {
        // Create empty context as fallback
        this.context = JsonPath.using(Configuration.defaultConfiguration())
          .parse("{}");
      }
      return this;
    }

    public JsonPathChecker check(Object object) {

      try {
        if (object == null) {
          this.context = JsonPath.using(Configuration.defaultConfiguration())
            .parse("{}");
          return this;
        }

        // Direct parsing for better performance - avoid string conversion when possible
        if (object instanceof String) {
          this.context = JsonPath.using(Configuration.defaultConfiguration())
            .parse((String) object);
        } else {
          // Convert to JSON string only when necessary
          String jsonString = MapperHelper.toString(object);
          this.context = JsonPath.using(Configuration.defaultConfiguration())
            .parse(jsonString);
        }

        return this;

      } catch (Exception e) {
        this.context = JsonPath.using(Configuration.defaultConfiguration())
          .parse("{}");
        return this;
      }
    }

    /**
     * Check if a JsonPath exists in the current context
     *
     * @param path JsonPath to check
     * @return true if path exists, false otherwise
     */
    public boolean pathExists(String path) {
      try {
        JsonPath jsonPath = JsonPath.compile(path);
        context.read(jsonPath);
        return true;
      } catch (Exception e) {
        return false;
      }
    }
  }


  public static class JsonPathHolder implements AutoCloseable {
    private DocumentContext context;

    JsonPathHolder() {

    }

    JsonPathHolder supplyContext(DocumentContext context) {
      this.context = context;
      return this;
    }

    public JsonPathHolder parse(String jsonString) {

      log.trace("#Parsing JSON string with length: {}", jsonString != null ? jsonString.length() : 0);

      try {
        if (jsonString == null || jsonString.trim()
          .isEmpty()) {
          log.debug("Empty or null JSON string provided, creating empty object context");
          jsonString = "{}";
        }

        this.context = JsonPath.using(SHARED_CONFIG)
          .parse(jsonString);

        log.trace("Successfully parsed JSON string");

      } catch (Exception e) {
        log.debug("Failed to parse JSON string: {}", e.getMessage());
        // Create empty context as fallback
        this.context = JsonPath.using(SHARED_CONFIG)
          .parse("{}");
      }
      return this;
    }

    public JsonPathHolder parse(Object object) {

      log.trace("#Parsing object of type: {}",
        object != null ?
          object.getClass()
            .getSimpleName() :
          "null"
      );

      try {
        if (object == null) {
          log.debug("Null object provided, creating empty object context");
          this.context = JsonPath.using(SHARED_CONFIG)
            .parse("{}");
          return this;
        }

        // Direct parsing for better performance - avoid string conversion when possible
        if (object instanceof String) {
          this.context = JsonPath.using(SHARED_CONFIG)
            .parse((String) object);
        } else {
          // Convert to JSON string only when necessary
          String jsonString = MapperHelper.toString(object);
          this.context = JsonPath.using(SHARED_CONFIG)
            .parse(jsonString);
        }

        log.trace("Successfully parsed object");
        return this;

      } catch (Exception e) {
        log.debug("Failed to parse object: {}", e.getMessage());
        // Create empty context as fallback
        this.context = JsonPath.using(SHARED_CONFIG)
          .parse("{}");
        return this;
      }
    }

    public <T> T read(String path) throws Exception {
      return readInternal(path, null, null);
    }

    public <T> T read(String path, Class<T> clazz) throws Exception {
      return readInternal(path, clazz, null);
    }

    public <T> T read(String path, TypeRef<T> typeRef) throws Exception {
      return readInternal(path, null, typeRef);
    }

    /**
     * Internal read method with optimized caching and error handling
     */
    private <T> T readInternal(String path, Class<T> clazz, TypeRef<T> typeRef) throws Exception {
      try {
        log.trace("#Reading path: {}", path);

        // Validate inputs
        if (path == null || path.trim()
          .isEmpty()) {
          throw new JsonException("JsonPath cannot be null or empty", path, "read", null);
        }

        DocumentContext context = this.context;
        if (context == null || context.json() == null) {
          log.debug("No context available for reading path: {}", path);
          return null;
        }

        // Use compiled JsonPath for better performance
        JsonPath compiledPath = getCompiledPath(path);

        try {
          if (clazz != null) {
            return context.read(compiledPath, clazz);
          } else if (typeRef != null) {
            return context.read(compiledPath, typeRef);
          } else {
            return context.read(compiledPath);
          }
        } catch (JsonPathException e) {
          log.debug("JsonPath evaluation failed for path '{}': {}", path, e.getMessage());
          return null; // Return null for missing paths instead of throwing
        }

      } catch (JsonException e) {
        throw e; // Re-throw our custom exceptions
      } catch (Exception e) {
        throw new JsonException("Failed to read JsonPath", path, "read", e);
      }
    }

    @SuppressWarnings("unchecked")
    public <T> T set(String path, Object value) throws Exception {
      try {
        log.trace(
          "#Setting path: {} with value of type: {}",
          path,
          value != null ?
            value.getClass()
              .getSimpleName() :
            "null"
        );

        // Validate inputs
        if (path == null || path.trim()
          .isEmpty()) {
          throw new JsonException("JsonPath cannot be null or empty", path, "set", null);
        }

        DocumentContext context = this.context;
        if (context == null) {
          log.debug("No context available, creating empty context for setting path: {}", path);
          context = JsonPath.using(SHARED_CONFIG)
            .parse("{}");
        }

        if (context.json() == null) {
          return (T) value;
        }

        try {
          return context.set(path, value)
            .json();
        } catch (JsonPathException e) {
          log.debug("JsonPath set operation failed for path '{}': {}", path, e.getMessage());
          throw new JsonException("Failed to set value at JsonPath", path, "set", e);
        }

      } catch (JsonException e) {
        throw e; // Re-throw our custom exceptions
      } catch (Exception e) {
        throw new JsonException("Failed to set JsonPath", path, "set", e);
      } finally {
        long endTime = System.nanoTime();
      }
    }

    /**
     * Bulk read operation for multiple JsonPaths
     *
     * @param paths List of JsonPath expressions to read
     * @return Map with JsonPath as key and result as value
     */
    public LinkedHashMap<String, Object> readBulk(List<String> paths) {
      LinkedHashMap<String, Object> results = new LinkedHashMap<>();

      if (paths == null || paths.isEmpty()) {
        return results;
      }

      DocumentContext context = this.context;
      if (context == null || context.json() == null) {
        log.debug("No context available for bulk read operation");
        return results;
      }

      for (String path : paths) {
        try {
          Object result = read(path);
          results.put(path, result);
        } catch (Exception e) {
          log.debug("Failed to read path '{}' in bulk operation: {}", path, e.getMessage());
          results.put(path, null);
        }
      }

      return results;
    }

    /**
     * Bulk set operation for multiple JsonPath/value pairs
     *
     * @param pathValueMap Map with JsonPath as key and value to set
     * @return Updated JSON object
     */
    public <T> T setBulk(Map<String, Object> pathValueMap) throws Exception {
      if (pathValueMap == null || pathValueMap.isEmpty()) {
        DocumentContext context = this.context;
        return context != null ? context.json() : null;
      }

      DocumentContext context = this.context;
      if (context == null) {
        context = JsonPath.using(SHARED_CONFIG)
          .parse("{}");
      }

      for (Map.Entry<String, Object> entry : pathValueMap.entrySet()) {
        try {
          context.set(entry.getKey(), entry.getValue());
        } catch (Exception e) {
          log.debug("Failed to set path '{}' in bulk operation: {}", entry.getKey(), e.getMessage());
          throw new JsonException("Bulk set operation failed", entry.getKey(), "setBulk", e);
        }
      }

      return context.json();
    }

    /**
     * Get the current JSON as string
     *
     * @return JSON string representation of current context
     */
    public String getCurrentJson() {
      DocumentContext context = this.context;
      if (context == null || context.json() == null) {
        return "{}";
      }

      try {
        return MapperHelper.toString(context.json());
      } catch (Exception e) {
        log.debug("Failed to convert context to JSON string: {}", e.getMessage());
        return "{}";
      }
    }

    @Override
    public void close() throws Exception {
      this.context = null;
      JsonHelper.threadLocalContext.remove();
    }
  }
}
