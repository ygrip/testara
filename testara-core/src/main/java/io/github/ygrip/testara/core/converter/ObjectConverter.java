package io.github.ygrip.testara.core.converter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

public interface ObjectConverter {
  <T> T convert(Object input);

  <T> T convertFromCache(Object input);

  default int priority() {
    return 0;
  }

  /**
   * Optimized LRU Cache implementation with thread safety
   */
  class OptimizedLRUCache<K, V> {
    private final int maxSize;
    private final ConcurrentMap<K, V> cache;
    private final ConcurrentMap<K, Long> accessOrder;
    private final AtomicLong accessCounter;

    public OptimizedLRUCache(int maxSize) {
      this.maxSize = maxSize;
      this.cache = new ConcurrentHashMap<>(maxSize);
      this.accessOrder = new ConcurrentHashMap<>(maxSize);
      this.accessCounter = new AtomicLong(0);
    }

    public V get(K key) {
      V value = cache.get(key);
      if (value != null) {
        accessOrder.put(key, accessCounter.incrementAndGet());
      }
      return value;
    }

    public void put(K key, V value) {
      if (value != null) {
        if (cache.size() >= maxSize) {
          evictLRU();
        }
        cache.put(key, value);
        accessOrder.put(key, accessCounter.incrementAndGet());
      }
    }

    public void clear() {
      cache.clear();
      accessOrder.clear();
      accessCounter.set(0);
    }

    public int size() {
      return cache.size();
    }

    private void evictLRU() {
      if (accessOrder.isEmpty()) {
        return;
      }

      K lruKey = accessOrder.entrySet().stream().min(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);

      if (lruKey != null) {
        cache.remove(lruKey);
        accessOrder.remove(lruKey);
      }
    }
  }
}
