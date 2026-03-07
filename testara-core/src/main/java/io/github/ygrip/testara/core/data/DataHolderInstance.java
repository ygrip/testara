package io.github.ygrip.testara.core.data;

import static java.util.Comparator.comparing;

import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.ref.SoftReference;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.ygrip.testara.core.context.TestComponent;
import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.core.converter.ObjectConverter;
import io.github.ygrip.testara.core.converter.ObjectConverterLoader;
import io.github.ygrip.testara.core.json.JacksonJsonPathSetter;
import io.github.ygrip.testara.core.json.JsonHelper;
import io.github.ygrip.testara.core.mapper.MapperHelper;
import io.github.ygrip.testara.core.model.DefaultData;
import io.github.ygrip.testara.core.model.RequestData;
import io.github.ygrip.testara.core.model.ResponseData;
import io.github.ygrip.testara.core.registry.RegistryScope;
import io.github.ygrip.testara.core.scan.ClassScanner;
import io.github.ygrip.testara.core.support.CommonHelper;
import io.github.ygrip.testara.core.support.JsonPathUtil;
import io.github.ygrip.testara.core.support.Stopwatch;
import io.github.ygrip.testara.core.time.DurationParser;
import io.github.ygrip.testara.core.transformer.TransformerService;

import lombok.extern.log4j.Log4j2;

/**
 * <p>DataHolderImpl class.</p>
 * Enhanced version with MethodHandle-based field access, reduced overhead, improved caching, and better performance.
 * Uses MethodHandle for accessing private, final, and static fields efficiently.
 *
 * @author yunaz.ramadhan on 12/7/2019
 * @version $Id: $Id
 */
@Log4j2
@TestComponent(scope = RegistryScope.TEST)
public class DataHolderInstance implements DataHolder {
  // ======== Static utilities (shared by all instances) ========
  private static final Pattern FIELD_PATTERN = Pattern.compile("^[a-zA-Z_$][a-zA-Z_$0-9]*$");
  private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
  private final AtomicBoolean loaded;
  private final ObjectConverter parser;

  // ======== Core data structures ========
  private final LinkedHashMap<Class<? extends DefaultData>, DefaultData> requestCatalog;
  private final LinkedHashMap<Class<? extends DefaultData>, DefaultData> responseCatalog;

  /**
   * SoftReference caches — cleared by GC when memory is low.
   * Key = unique field signature.
   */
  private final Map<String, SoftReference<MethodHandleAccessor>> fieldHandleCache = new ConcurrentHashMap<>();

  /**
   * <p>Constructor for DataHolderImpl.</p>
   */
  public DataHolderInstance() {
    this.loaded = new AtomicBoolean(false);
    this.requestCatalog = new LinkedHashMap<>();
    this.responseCatalog = new LinkedHashMap<>();
    this.parser = ObjectConverterLoader.instance();
  }

  /**
   * Check if MethodHandles.privateLookupIn is available (Java 9+)
   */
  private boolean hasPrivateLookupInSupport() {
    try {
      MethodHandles.class.getMethod("privateLookupIn", Class.class, MethodHandles.Lookup.class);
      return true;
    } catch (NoSuchMethodException e) {
      return false;
    }
  }

  private void loadAllDefaultData() throws ExecutionException, InterruptedException, TimeoutException {
    Stopwatch stopwatch = Stopwatch.start();

    ClassScanner scanner = TestFramework.context()
      .get(ClassScanner.class);

    // Use optimized scanning for improved performance
    List<Class<?>> requestDataClasses = scanner.scan("request-data", DefaultData.class, RequestData.class)
      .get(10, TimeUnit.SECONDS);
    List<Class<?>> responseDataClasses = scanner.scan("response-data", DefaultData.class, ResponseData.class)
      .get(10, TimeUnit.SECONDS);

    // Register data types with optimized batch processing
    this.requestCatalog.putAll(registerDataOptimized(RequestData.class, requestDataClasses));
    this.responseCatalog.putAll(registerDataOptimized(ResponseData.class, responseDataClasses));

    // Pre-populate MethodHandle cache for better performance
    prePopulateMethodHandleCache();
    log.info(
      "#Finish loading all data store, process took {}",
      DurationParser.formatDuration(stopwatch.stop()
        .elapsed(TimeUnit.NANOSECONDS))
    );
  }

  private void populateMethodHandle(LinkedHashMap<Class<? extends DefaultData>, DefaultData> data) {
    for (Class<? extends DefaultData> clazz : data.keySet()) {
      // Cache MethodHandles for all declared fields
      Field[] fields = clazz.getDeclaredFields();
      for (Field field : fields) {
        try {
          MethodHandleAccessor accessor = createMethodHandleAccessor(field);
          if (accessor != null) {
            String cacheKey = clazz.getName() + "." + field.getName();
            fieldHandleCache.put(cacheKey, new SoftReference<>(accessor));
          }
        } catch (Exception e) {
          log.debug(
            "Failed to create MethodHandle for field {}.{}: {}",
            clazz.getName(),
            field.getName(),
            e.getMessage()
          );
        }
      }
    }
  }

  /**
   * Pre-populate MethodHandle cache to avoid lookup overhead during runtime
   */
  private void prePopulateMethodHandleCache() {
    log.debug("Pre-populating MethodHandle cache...");
    int initialCacheSize = fieldHandleCache.size();
    populateMethodHandle(requestCatalog);
    populateMethodHandle(responseCatalog);

    log.debug("MethodHandle cache pre-populated with {} field accessors", fieldHandleCache.size() - initialCacheSize);
  }

  /**
   * Create MethodHandle accessor for a field, supporting private, final, and static fields
   * Uses a progressive fallback approach for maximum Java version compatibility
   */
  private MethodHandleAccessor createMethodHandleAccessor(Field field) throws IllegalAccessException {
    boolean isStatic = Modifier.isStatic(field.getModifiers());
    boolean isFinal = Modifier.isFinal(field.getModifiers());
    JavaType genericType = MapperHelper.getGenericType(field);
    Class<?> fieldType = field.getType();
    Class<?> declaringClass = field.getDeclaringClass();

    // Try multiple approaches in order of preference
    MethodHandle getter = null;
    MethodHandle setter = null;

    // Approach 1: Try standard lookup first
    try {
      getter = createGetter(declaringClass, field.getName(), fieldType, isStatic, LOOKUP);
      if (!isFinal) {
        setter = createSetter(declaringClass, field.getName(), fieldType, isStatic, LOOKUP);
      }
    } catch (Exception e) {
      log.debug(
        "Standard lookup failed for field {}.{}: {}",
        declaringClass.getName(),
        field.getName(),
        e.getMessage()
      );
    }

    // Approach 2: Try privateLookupIn for cross-package access (Java 9+)
    if (getter == null && hasPrivateLookupInSupport()) {
      try {
        MethodHandles.Lookup privateLookup = createPrivateLookup(declaringClass);
        getter = createGetter(declaringClass, field.getName(), fieldType, isStatic, privateLookup);
        if (!isFinal) {
          setter = createSetter(declaringClass, field.getName(), fieldType, isStatic, privateLookup);
        }
        log.debug(
          "Successfully created MethodHandle using privateLookupIn for {}.{}",
          declaringClass.getName(),
          field.getName()
        );
      } catch (Exception e) {
        log.debug(
          "PrivateLookupIn failed for field {}.{}: {}",
          declaringClass.getName(),
          field.getName(),
          e.getMessage()
        );
      }
    }

    // Approach 3: Fallback to reflection-based unreflect approach
    if (getter == null) {
      try {
        field.setAccessible(true);
        getter = LOOKUP.unreflectGetter(field);
        if (!isFinal) {
          setter = LOOKUP.unreflectSetter(field);
        }
        log.debug("Using unreflect approach for field {}.{}", declaringClass.getName(), field.getName());
      } catch (Exception e) {
        log.debug(
          "Unreflect approach failed for field {}.{}: {}",
          declaringClass.getName(),
          field.getName(),
          e.getMessage()
        );
        return null;
      }
    }

    // Handle final field setters with special care
    if (isFinal) {
      setter = createFinalFieldSetter(field);
    }

    return new MethodHandleAccessor(getter, setter, fieldType, genericType, isStatic, isFinal);
  }

  /**
   * Create a private lookup for accessing cross-package private members (Java 9+)
   */
  private MethodHandles.Lookup createPrivateLookup(Class<?> targetClass) throws Exception {
    // Use reflection to call MethodHandles.privateLookupIn
    return (MethodHandles.Lookup) MethodHandles.class.getMethod(
        "privateLookupIn",
        Class.class,
        MethodHandles.Lookup.class
      )
      .invoke(null, targetClass, LOOKUP);
  }

  /**
   * Create getter MethodHandle with error handling
   */
  private MethodHandle createGetter(Class<?> declaringClass, String fieldName, Class<?> fieldType, boolean isStatic,
    MethodHandles.Lookup lookupToUse) throws Exception {
    if (isStatic) {
      return lookupToUse.findStaticGetter(declaringClass, fieldName, fieldType);
    } else {
      return lookupToUse.findGetter(declaringClass, fieldName, fieldType);
    }
  }

  /**
   * Create setter MethodHandle with error handling
   */
  private MethodHandle createSetter(Class<?> declaringClass, String fieldName, Class<?> fieldType, boolean isStatic,
    MethodHandles.Lookup lookupToUse) throws Exception {
    if (isStatic) {
      return lookupToUse.findStaticSetter(declaringClass, fieldName, fieldType);
    } else {
      return lookupToUse.findSetter(declaringClass, fieldName, fieldType);
    }
  }

  /**
   * Create a setter MethodHandle for final fields with fallback approaches
   */
  private MethodHandle createFinalFieldSetter(Field field) {
    try {
      // For final fields, try unreflect with the original field
      field.setAccessible(true);
      return LOOKUP.unreflectSetter(field);
    } catch (Exception e) {
      log.debug(
        "Failed to create setter for final field {} using standard unreflect: {}",
        field.getName(),
        e.getMessage()
      );

      // Try with privateLookupIn if available (Java 9+)
      if (hasPrivateLookupInSupport()) {
        try {
          MethodHandles.Lookup privateLookup = createPrivateLookup(field.getDeclaringClass());
          return privateLookup.unreflectSetter(field);
        } catch (Exception ex) {
          log.debug(
            "Failed to create setter for final field {} using privateLookupIn: {}",
            field.getName(),
            ex.getMessage()
          );
        }
      }

      // Final fields might not be settable in newer Java versions
      log.debug(
        "Final field {} is not settable via MethodHandle - this is expected in secured environments",
        field.getName()
      );
      return null;
    }
  }

  /**
   * Optimized data registration with batch processing
   */
  @SuppressWarnings("unchecked")
  private LinkedHashMap<Class<? extends DefaultData>, DefaultData> registerDataOptimized(
    Class<? extends Annotation> annotationType, List<Class<?>> loaded) {

    LinkedHashMap<Class<? extends DefaultData>, DefaultData> result = new LinkedHashMap<>();

    // Process classes in parallel for better performance
    loaded.forEach(clazz -> {
      DefaultData instance;
      try {
        instance = getInstanceOptimized(clazz);
        if (instance != null) {
          result.put((Class<? extends DefaultData>) clazz, instance);
        }
      } catch (Exception err) {
        log.trace(
          "Failed to load instance for {}: {} , will fallback to create instance from factory",
          clazz.getName(),
          err.getMessage()
        );
        instance = (DefaultData) TestFramework.factory()
          .getInstance(clazz);
        result.put((Class<? extends DefaultData>) clazz, instance);
      }
    });

    log.info(
      "#Registering {} store, found {} class to store :\n{}",
      annotationType.getSimpleName(),
      result.size(),
      result.keySet()
    );

    // Sort the DefaultData instances based on the order (optimized)
    return sortDataByOrder(result, annotationType);
  }

  /**
   * Optimized instance creation with MethodHandle and caching
   */
  private DefaultData getInstanceOptimized(Class<?> clazz) {
    return (DefaultData) TestFramework.context()
      .get(clazz);
  }

  /**
   * Optimized sorting with better performance
   */
  private LinkedHashMap<Class<? extends DefaultData>, DefaultData> sortDataByOrder(
    LinkedHashMap<Class<? extends DefaultData>, DefaultData> data, Class<? extends Annotation> annotationType) {

    if (data.isEmpty()) {
      return data;
    }

    // Create sorted list
    List<Map.Entry<Class<? extends DefaultData>, DefaultData>> sorted = new LinkedList<>(data.entrySet());

    // Sort based on annotation order
    if (annotationType.isAssignableFrom(RequestData.class)) {
      sorted.sort(comparing(o -> o.getKey()
        .getAnnotation(RequestData.class)
        .order()));
    } else {
      sorted.sort(comparing(o -> o.getKey()
        .getAnnotation(ResponseData.class)
        .order()));
    }

    Collections.reverse(sorted);

    return sorted.stream()
      .collect(
        () -> new LinkedHashMap<>(sorted.size()),
        (map, entry) -> map.put(entry.getKey(), entry.getValue()),
        Map::putAll
      );
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public LinkedHashMap<Class<? extends DefaultData>, DefaultData> getRequests() {
    return getCatalog(RequestData.class);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public LinkedHashMap<Class<? extends DefaultData>, DefaultData> getResponses() {
    return getCatalog(ResponseData.class);
  }

  private LinkedHashMap<Class<? extends DefaultData>, DefaultData> getCatalog(Class<? extends Annotation> annotaion) {
    if (loaded.compareAndSet(false, true)) {
      try {
        loadAllDefaultData();
      } catch (ExecutionException | InterruptedException | TimeoutException e) {
        Thread.currentThread()
          .interrupt();
        throw new RuntimeException(e);
      }
    }
    if (annotaion == RequestData.class) {
      return requestCatalog;
    } else if (annotaion == ResponseData.class) {
      return responseCatalog;
    }
    return null;
  }

  /**
   * Optimized data value retrieval with MethodHandle caching
   */
  private Map.Entry<DefaultData, Object> getPairedDataValueOptimized(Class<? extends Annotation> annotation,
    String fieldName, DefaultData fallback) {

    fieldName = fieldName.trim();
    if (fieldName.isEmpty()) {
      log.warn("Path should not be empty");
      return new AbstractMap.SimpleEntry<>(fallback, null);
    }

    Matcher matcher = FIELD_PATTERN.matcher(fieldName);
    boolean isValidVariableName = matcher.find();
    Object value = null;
    DefaultData result = fallback;

    LinkedHashMap<Class<? extends DefaultData>, DefaultData> mappedData = getCatalog(annotation);

    for (Class<? extends DefaultData> key : mappedData.keySet()) {
      if (isValidVariableName) {
        // Use cached MethodHandle lookup for better performance
        MethodHandleAccessor accessor = getMethodHandleAccessorFromCache(key, fieldName);
        if (accessor != null) {
          try {
            result = mappedData.get(key);
            value = accessor.get(result);
            break;
          } catch (Throwable e) {
            log.debug("Failed to access field {} using MethodHandle: {}", fieldName, e.getMessage());
          }
        } else {
          value = getInstanceOptimized(key).get()
            .getOrDefault(fieldName, null);
          if (value != null) {
            result = mappedData.get(key);
            break;
          }
        }
      } else {
        // Handle JSON path scenario
        value = handleJsonPath(fieldName, key, mappedData);
        if (value != null) {
          result = mappedData.get(key);
          break;
        }
      }
    }

    return new AbstractMap.SimpleEntry<>(result, value);
  }

  /**
   * Get MethodHandle accessor from cache with optimized lookup
   */
  private MethodHandleAccessor getMethodHandleAccessorFromCache(Class<? extends DefaultData> clazz, String fieldName) {
    String cacheKey = clazz.getName() + "." + fieldName;
    SoftReference<MethodHandleAccessor> ref = fieldHandleCache.get(cacheKey);
    MethodHandleAccessor handle = (ref != null) ? ref.get() : null;

    if (handle == null) {
      try {
        Field declaredField = clazz.getDeclaredField(fieldName);
        handle = createMethodHandleAccessor(declaredField);
        fieldHandleCache.put(cacheKey, new SoftReference<>(handle));
      } catch (Exception ignored) {

      }
    }

    return handle;
  }

  /**
   * Handle JSON path with optimized processing using MethodHandle
   */
  private Object handleJsonPath(String fieldName, Class<? extends DefaultData> key,
    Map<Class<? extends DefaultData>, DefaultData> mappedData) {

    final var fullPath = Optional.ofNullable(fieldName)
      .filter(JsonPathUtil::isValidJsonPath)
      .orElseGet(() -> JsonPathUtil.toValidJsonPath(fieldName));
    String firstPath = getFirstPath(fullPath);
    if (StringUtils.isBlank(firstPath)) {
      return null;
    }

    Object value = null;

    // Try MethodHandle-based JSON path first
    MethodHandleAccessor accessor = getMethodHandleAccessorFromCache(key, firstPath);
    if (accessor != null) {
      try (JsonHelper.JsonPathHolder jsonPath = JsonHelper.instance()) {
        Object fieldValue = accessor.get(mappedData.get(key));
        value = jsonPath.parse(Collections.singletonMap(firstPath, fieldValue))
          .read(fullPath);
      } catch (Throwable e) {
        log.debug("Failed to process JSON path {} using MethodHandle: {}", fullPath, e.getMessage());
      }
    }

    // Fallback to data map
    if (value == null) {
      DefaultData dataInstance = mappedData.get(key);
      if (dataInstance.get()
        .containsKey(firstPath)) {
        try (JsonHelper.JsonPathHolder jsonPath = JsonHelper.instance()) {
          value = jsonPath.parse(Stream.of(firstPath)
              .filter(dataInstance.get()::containsKey)
              .collect(Collectors.toMap(Function.identity(), dataInstance.get()::get)))
            .read(fullPath);
        } catch (Exception e) {
          log.debug("Failed to process fallback JSON path {}: {}", fullPath, e.getMessage());
        }
      }
    }

    return value;
  }

  private String getFirstPath(String jsonPath) {
    Optional<String> result = Arrays.stream(jsonPath.split("['$\\[\\].]"))
      .filter(node -> !node.trim()
        .isEmpty())
      .findFirst();
    return result.orElse("");
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Map.Entry<DefaultData, Object> getRequest(String fieldName) {
    DefaultData fallback = getRequest(DefaultRequestData.class);
    return getPairedDataValueOptimized(RequestData.class, fieldName, fallback);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Map.Entry<DefaultData, Object> getResponse(String fieldName) {
    DefaultData fallback = getResponse(DefaultResponseData.class);
    return getPairedDataValueOptimized(ResponseData.class, fieldName, fallback);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @SuppressWarnings("unchecked")
  public <T> T getResponse(Class<T> clazz) {
    return (T) getResponses().getOrDefault(clazz, null);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @SuppressWarnings("unchecked")
  public <T> T getRequest(Class<T> clazz) {
    return (T) getRequests().getOrDefault(clazz, null);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void reset() {
    resetRequestsData();
    resetResponsesData();
    clearCaches();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void resetResponsesData() {
    if (loaded.get()) {
      Map<Class<? extends DefaultData>, DefaultData> responses = getResponses();
      responses.values()
        .forEach(this::resetAllFieldsOptimized);
      log.debug("Cleared all response data");
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void resetRequestsData() {
    if (loaded.get()) {
      Map<Class<? extends DefaultData>, DefaultData> requests = getRequests();
      requests.values()
        .forEach(this::resetAllFieldsOptimized);
      log.debug("Cleared all request data");
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void resetRequestDataOnClass(Class<? extends DefaultData> clazz) {
    DefaultData instance = getRequest(clazz);
    resetAllFieldsOptimized(instance);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void resetResponseDataOnClass(Class<? extends DefaultData> clazz) {
    DefaultData instance = getResponse(clazz);
    resetAllFieldsOptimized(instance);
  }

  /**
   * Optimized field reset with cached MethodHandle access
   */
  private void resetAllFieldsOptimized(DefaultData instance) {
    if (instance == null) {
      return;
    }

    log.debug("Reset all fields on class {}", instance.getClass());

    try {
      // Use cached MethodHandles for better performance
      String classPrefix = instance.getClass()
        .getName() + ".";
      fieldHandleCache.entrySet()
        .stream()
        .filter(entry -> entry.getKey()
          .startsWith(classPrefix))
        .forEach(entry -> {
          SoftReference<MethodHandleAccessor> accessor = entry.getValue();
          clearField(instance, accessor.get());
        });

      // Reset the data map
      instance.set(new HashMap<>());
    } catch (Exception e) {
      log.debug(
        "Failed to reset fields for {}: {}",
        instance.getClass()
          .getName(),
        e.getMessage()
      );
    }
  }

  private void clearField(DefaultData instance, MethodHandleAccessor accessor) {
    try {
      if (accessor != null && accessor.setter != null) {
        if (accessor.fieldType()
          .isPrimitive()) {
          if (accessor.fieldType()
            .equals(boolean.class)) {
            accessor.set(instance, false);
          } else if (accessor.fieldType()
            .equals(char.class)) {
            accessor.set(instance, '\u0000');
          } else if (accessor.fieldType()
            .equals(long.class)) {
            accessor.set(instance, 0L);
          } else if (accessor.fieldType()
            .equals(byte.class) || accessor.fieldType()
            .equals(short.class) || accessor.fieldType()
            .equals(int.class)) {
            accessor.set(instance, 0);
          } else if (accessor.fieldType()
            .equals(float.class) || accessor.fieldType()
            .equals(double.class)) {
            accessor.set(instance, 0.0);
          }
        } else if (!accessor.isFinal()) {
          accessor.set(instance, null);
        }
      }

    } catch (Throwable err) {
      log.trace(
        "Fail to reset {} on {}",
        accessor.fieldType()
          .getSimpleName(),
        instance.getClass()
          .getSimpleName()
      );
    }
  }

  /**
   * Optimized field reset for a specific path using MethodHandle
   */
  private void resetFieldOptimized(Class<? extends Annotation> annotation, String path) {
    path = path.trim();
    if (path.isEmpty()) {
      log.warn("Path should not be empty, action skipped");
      return;
    }

    Matcher matcher = FIELD_PATTERN.matcher(path);
    boolean isValidVariableName = matcher.find();
    LinkedHashMap<Class<? extends DefaultData>, DefaultData> mappedData = getCatalog(annotation);

    log.debug("Reset {} with path {}", annotation.getSimpleName(), path);

    for (Class<? extends DefaultData> key : mappedData.keySet()) {
      DefaultData instance = mappedData.get(key);

      if (isValidVariableName) {
        MethodHandleAccessor accessor = getMethodHandleAccessorFromCache(key, path);
        if (accessor != null) {
          clearField(instance, accessor);
        }
        instance.get()
          .remove(path);
      } else {
        String firstPath = getFirstPath(path);
        try (JsonHelper.JsonPathHolder jsonPath = JsonHelper.instance()) {
          instance = jsonPath.parse(instance)
            .set(path, null);
        } catch (Exception e) {
          log.debug("Failed to reset JSON path {}: {}", path, e.getMessage());
        }

        if (instance.get()
          .containsKey(firstPath)) {
          try (JsonHelper.JsonPathHolder jsonPath = JsonHelper.instance()) {
            instance.set(jsonPath.parse(instance.get())
              .set(path, null));
          } catch (Exception e) {
            log.debug("Failed to reset fallback JSON path {}: {}", path, e.getMessage());
          }
        }
      }
    }
  }

  /**
   * Optimized field set for a specific path using MethodHandle
   */
  private void setFieldOptimized(Class<? extends Annotation> annotation, String path, Object value) {
    path = path.trim();
    String finalPath = path;
    String fullPath = Optional.of(finalPath)
      .filter(JsonPathUtil::isValidJsonPath)
      .orElseGet(() -> JsonPathUtil.toValidJsonPath(finalPath));
    if (fullPath.isEmpty()) {
      log.warn("Path should not be empty, action skipped");
      return;
    }

    Matcher matcher = FIELD_PATTERN.matcher(path);
    boolean isValidVariableName = matcher.find();
    LinkedHashMap<Class<? extends DefaultData>, DefaultData> mappedData = getCatalog(annotation);

    int assigned = 0;
    Object finalValue = null;
    if (ObjectUtils.isNotEmpty(mappedData)) {

      log.debug("Set value of {} with path {}", annotation.getSimpleName(), path);


      for (Class<? extends DefaultData> key : mappedData.keySet()) {
        DefaultData instance = mappedData.get(key);

        if (isValidVariableName) {
          MethodHandleAccessor accessor = getMethodHandleAccessorFromCache(key, path);
          if (accessor != null) {
            try {
              if (value instanceof TransformerService transformerService) {
                finalValue = transformerService.to(accessor.genericType());
              } else {
                finalValue = parser.convert(value);
              }
              accessor.set(instance, MapperHelper.toObject(finalValue, accessor.genericType()));
              instance.get()
                .remove(path);
              assigned++;
              break;
            } catch (Throwable ignored) {
              log.debug("Failed to set value path {}, fallback to default value", path);
            }
          }
        }

        if (assigned == 0) {
          if (value instanceof TransformerService transformerService) {
            if (transformerService.hasData()) {
              if (!CommonHelper.isBlank(transformerService.getProcessedTable())) {
                if (transformerService.getProcessedTable()
                  .width() < 2) {
                  finalValue =
                    transformerService.to(MapperHelper.getGenericType(new TypeReference<LinkedHashMap<String, Object>>() {
                    }));
                } else if (transformerService.getProcessedTable()
                  .width() == 2) {
                  finalValue = transformerService.toMap("key", "value");
                }
                if (CommonHelper.isBlank(finalValue)) {
                  if (transformerService.getProcessedTable()
                    .height() > 1) {
                    finalValue = transformerService.toList(LinkedHashMap.class);
                  } else {
                    finalValue =
                      transformerService.to(MapperHelper.getGenericType(new TypeReference<LinkedHashMap<String, Object>>() {
                      }));
                  }
                }
                if (CommonHelper.isBlank(finalValue)) {
                  finalValue = transformerService.toCells();
                }
              } else {
                finalValue = transformerService.to(Object.class);
              }
            }
          } else {
            finalValue = parser.convert(value);
          }
          String firstPath = getFirstPath(fullPath);
          try{
            boolean pathExists = JsonHelper.check(instance)
              .pathExists(fullPath);
            if (pathExists) {
              // Try MethodHandle-based JSON path first
              MethodHandleAccessor accessor = getMethodHandleAccessorFromCache(key, firstPath);
              if (accessor != null) {
                Object fieldValue = accessor.get(instance);
                ObjectNode objectTree = MapperHelper.getObjectMapper()
                  .valueToTree(Collections.singletonMap(firstPath, fieldValue));
                JacksonJsonPathSetter.set(
                  objectTree,
                  fullPath,
                  MapperHelper.getObjectMapper()
                    .convertValue(finalValue, JsonNode.class)
                );
                accessor.set(instance, MapperHelper.getObjectMapper().treeToValue(objectTree.get(firstPath), accessor.genericType()));
                assigned++;
              }
            }
          } catch (Throwable err) {
            log.debug("Failed to set JSON path {}: {}", fullPath, err.getMessage());
          }
          if (assigned == 0) {
            instance.addDefaultData(firstPath, finalValue);
            break;
          }
        }
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void resetRequestData(String path) {
    resetFieldOptimized(RequestData.class, path);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void resetResponseData(String path) {
    resetFieldOptimized(ResponseData.class, path);
  }

  @Override
  public void setRequest(String path, Object data) {
    setFieldOptimized(RequestData.class, path, data);
  }

  @Override
  public void setResponse(String path, Object data) {
    setFieldOptimized(ResponseData.class, path, data);
  }

  /**
   * Clear all caches - useful for testing or memory cleanup
   */
  public void clearCaches() {
    fieldHandleCache.clear();
    log.debug("All caches cleared");
  }


  /**
   * Wrapper class for MethodHandle field access operations
   */
  private record MethodHandleAccessor(MethodHandle getter, MethodHandle setter, Class<?> fieldType,
                                      JavaType genericType, boolean isStatic, boolean isFinal) {

    public Object get(Object instance) throws Throwable {
      return isStatic ? getter.invoke() : getter.invoke(instance);
    }

    public void set(Object instance, Object value) throws Throwable {
      if (setter == null) {
        if (isFinal) {
          log.debug("Cannot set final field - setter not available (this is expected in secured environments)");
          return;
        } else {
          throw new UnsupportedOperationException("Setter not available for this field");
        }
      }

      if (isFinal) {
        log.debug("Attempting to set final field - this may fail depending on JVM security settings");
      }

      if (isStatic) {
        setter.invoke(value);
      } else {
        setter.invoke(instance, value);
      }
    }
  }
}
