package io.github.ygrip.testara.core.factory;

import io.github.ygrip.testara.core.context.Inject;
import io.github.ygrip.testara.core.context.TestFramework;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.reflect.FieldUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Post processor that injects dependencies into fields annotated with {@link Inject}.
 * 
 * This processor scans all fields of a newly created instance and injects dependencies
 * from the TestContext for any field marked with @Inject.
 * 
 * Example usage:
 * <pre>
 * public class MyService {
 *   @Inject
 *   private OtherService otherService;  // Will be injected automatically
 *   
 *   @Inject
 *   private ConfigProperties config;    // Will be injected automatically
 * }
 * </pre>
 * 
 * The processor is automatically loaded via ServiceLoader and applied to all instances
 * created through the framework's dependency injection mechanism.
 * 
 * @author yunaz.ramadhan
 */
@Log4j2
public class InjectFieldPostProcessor implements InstancePostProcessor {

  // Cache to track which classes have injectable fields to avoid repeated reflection
  private static final Map<Class<?>, List<Field>> INJECTABLE_FIELDS_CACHE = new ConcurrentHashMap<>();
  
  // Track instances being injected to prevent circular injection
  private static final ThreadLocal<Set<Object>> INJECTING =
      ThreadLocal.withInitial(HashSet::new);

  @Override
  public int priority() {
    // Run after most processors but before method interception
    // Field injection should happen early so interceptors see the complete object
    return 50;
  }

  @Override
  public boolean supports(Class<?> type) {
    // Check if the type has any @Inject fields
    return hasInjectableFields(type);
  }

  @Override
  public <T> T postProcess(T instance, Class<T> instanceType) {
    if (instance == null) {
      return null;
    }
    
    // Prevent circular injection - if we're already injecting this instance, skip
    if (INJECTING.get().contains(instance)) {
      return instance;
    }
    
    INJECTING.get().add(instance);
    try {
      injectFields(instance);
      return instance;
    } finally {
      INJECTING.get().remove(instance);
    }
  }

  /**
   * Inject dependencies into all @Inject annotated fields of the instance.
   */
  private void injectFields(Object instance) {
    Class<?> type = instance.getClass();
    
    // Handle ByteBuddy proxies - get the actual superclass
    if (type.getName().contains("$ByteBuddy$")) {
      type = type.getSuperclass();
    }
    
    List<Field> injectableFields = getInjectableFields(type);
    
    for (Field field : injectableFields) {
      try {
        injectField(instance, field);
      } catch (Exception e) {
        log.warn("Failed to inject field '{}' in {}: {}", 
            field.getName(), 
            type.getSimpleName(), 
            e.getMessage());
        log.debug("Injection failure details", e);
      }
    }
  }

  /**
   * Inject a single field with a dependency from the TestContext.
   */
  private void injectField(Object instance, Field field) throws IllegalAccessException {
    // Check if field already has a value - don't overwrite
    Object existingValue = FieldUtils.readField(field, instance, true);
    if (existingValue != null) {
      log.trace("Skipping injection for field '{}' - already has value", field.getName());
      return;
    }
    
    Class<?> fieldType = field.getType();
    
    try {
      // Get the dependency from TestContext
      Object dependency = TestFramework.context().get(fieldType);
      
      if (dependency != null) {
        FieldUtils.writeField(field, instance, dependency, true);
        log.debug("Injected {} into {}.{}", 
            fieldType.getSimpleName(), 
            instance.getClass().getSimpleName(), 
            field.getName());
      } else {
        log.warn("Could not resolve dependency for field '{}' of type {}", 
            field.getName(), fieldType.getName());
      }
    } catch (IllegalStateException e) {
      // TestContext not initialized - this can happen during early bootstrap
      log.debug("TestContext not available for injection of field '{}': {}", 
          field.getName(), e.getMessage());
    }
  }

  /**
   * Check if a class has any fields annotated with @Inject.
   */
  private boolean hasInjectableFields(Class<?> type) {
    return !getInjectableFields(type).isEmpty();
  }

  /**
   * Get all fields annotated with @Inject, including inherited fields.
   * Results are cached for performance.
   */
  private List<Field> getInjectableFields(Class<?> type) {
    return INJECTABLE_FIELDS_CACHE.computeIfAbsent(type, this::findInjectableFields);
  }

  /**
   * Find all @Inject annotated fields in the class hierarchy.
   */
  private List<Field> findInjectableFields(Class<?> type) {
    return FieldUtils.getAllFieldsList(type).stream()
        .filter(field -> field.isAnnotationPresent(Inject.class))
        .filter(field -> !Modifier.isStatic(field.getModifiers()))
        .filter(field -> !Modifier.isFinal(field.getModifiers()))
        .toList();
  }

  @Override
  public void shutdown() {
    INJECTABLE_FIELDS_CACHE.clear();
    log.debug("InjectFieldPostProcessor shutdown - cache cleared");
  }
  
  /**
   * Clear the injectable fields cache.
   * Useful for testing or when classes are reloaded.
   */
  public static void clearCache() {
    INJECTABLE_FIELDS_CACHE.clear();
  }
}
