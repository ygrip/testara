package io.github.ygrip.testara.core.factory;

import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Registry for managing and applying InstancePostProcessors.
 * Supports both ServiceLoader-based auto-discovery and manual registration.
 * 
 * Post processors are applied in priority order (highest priority first).
 */
@Log4j2
public final class PostProcessorRegistry {

  private static final PostProcessorRegistry INSTANCE = new PostProcessorRegistry();
  
  private final List<InstancePostProcessor> processors = new CopyOnWriteArrayList<>();
  private volatile boolean initialized = false;

  private PostProcessorRegistry() {
    // Singleton
  }

  /**
   * Get the global PostProcessorRegistry instance.
   */
  public static PostProcessorRegistry instance() {
    return INSTANCE;
  }

  /**
   * Initialize the registry by loading processors via ServiceLoader.
   * Safe to call multiple times - will only load once.
   */
  public void initialize() {
    if (initialized) {
      return;
    }
    
    synchronized (this) {
      if (initialized) {
        return;
      }
      
      loadFromServiceLoader();
      sortByPriority();
      
      // Initialize all processors
      for (InstancePostProcessor processor : processors) {
        try {
          processor.initialize();
          log.debug("Initialized post processor: {}", processor.getClass().getSimpleName());
        } catch (Exception e) {
          log.warn("Failed to initialize post processor: {} - {}", 
              processor.getClass().getSimpleName(), e.getMessage());
        }
      }
      
      initialized = true;
      log.debug("PostProcessorRegistry initialized with {} processors", processors.size());
    }
  }

  /**
   * Manually register a post processor.
   * Use this for programmatic registration (e.g., from Spring configuration).
   * 
   * @param processor the processor to register
   */
  public void register(InstancePostProcessor processor) {
    if (!processors.contains(processor)) {
      processors.add(processor);
      sortByPriority();
      
      if (initialized) {
        // If already initialized, initialize the new processor immediately
        try {
          processor.initialize();
          log.debug("Registered and initialized post processor: {}", 
              processor.getClass().getSimpleName());
        } catch (Exception e) {
          log.warn("Failed to initialize post processor: {} - {}", 
              processor.getClass().getSimpleName(), e.getMessage());
        }
      }
    }
  }

  /**
   * Apply all registered post processors to the given instance.
   * Processors are applied in priority order.
   * 
   * @param instance     the instance to process
   * @param instanceType the original requested type
   * @param <T>          the instance type
   * @return the processed instance
   */
  public <T> T applyPostProcessors(T instance, Class<T> instanceType) {
    if (!initialized) {
      initialize();
    }
    
    if (processors.isEmpty()) {
      return instance;
    }
    
    T result = instance;
    for (InstancePostProcessor processor : processors) {
      try {
        if (processor.supports(instanceType)) {
          T processed = processor.postProcess(result, instanceType);
          if (processed != null) {
            result = processed;
          }
        }
      } catch (Exception e) {
        log.warn("Post processor {} failed for type {}: {}", 
            processor.getClass().getSimpleName(), 
            instanceType.getSimpleName(), 
            e.getMessage());
        // Continue with the current result
      }
    }
    
    return result;
  }

  /**
   * Check if any post processor supports the given type.
   * Useful for optimization - skip post-processing entirely if no processor cares.
   * 
   * @param type the type to check
   * @return true if at least one processor supports this type
   */
  public boolean hasProcessorFor(Class<?> type) {
    if (!initialized) {
      initialize();
    }
    
    for (InstancePostProcessor processor : processors) {
      if (processor.supports(type)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Get the number of registered processors.
   */
  public int size() {
    return processors.size();
  }

  /**
   * Shutdown all processors and clear the registry.
   * Call this during application shutdown.
   */
  public void shutdown() {
    for (InstancePostProcessor processor : processors) {
      try {
        processor.shutdown();
      } catch (Exception e) {
        log.warn("Error shutting down post processor: {} - {}", 
            processor.getClass().getSimpleName(), e.getMessage());
      }
    }
    processors.clear();
    initialized = false;
    log.debug("PostProcessorRegistry shutdown complete");
  }

  /**
   * Clear all registered processors without calling shutdown on them.
   * Primarily intended for testing to ensure test isolation.
   * Does NOT call processor.shutdown() - use {@link #shutdown()} for graceful cleanup.
   */
  public void clear() {
    processors.clear();
    initialized = false;
    log.debug("PostProcessorRegistry cleared");
  }

  private void loadFromServiceLoader() {
    try {
      ServiceLoader<InstancePostProcessor> loader = ServiceLoader.load(InstancePostProcessor.class);
      for (InstancePostProcessor processor : loader) {
        if (!processors.contains(processor)) {
          processors.add(processor);
          log.debug("Loaded post processor via ServiceLoader: {}", 
              processor.getClass().getName());
        }
      }
    } catch (Exception e) {
      log.debug("No InstancePostProcessor implementations found via ServiceLoader: {}", 
          e.getMessage());
    }
  }

  private void sortByPriority() {
    List<InstancePostProcessor> sorted = new ArrayList<>(processors);
    sorted.sort(Comparator.comparingInt(InstancePostProcessor::priority).reversed());
    processors.clear();
    processors.addAll(sorted);
  }
}

