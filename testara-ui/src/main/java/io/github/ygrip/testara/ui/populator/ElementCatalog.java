package io.github.ygrip.testara.ui.populator;

import java.lang.reflect.Field;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ObjectUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import io.github.ygrip.testara.core.mapper.MapperHelper;

import lombok.Getter;

/**
 * <p>ElementCatalog class.</p>
 *
 * @author yunaz.ramadhan on 12/12/2021
 * @version $Id: $Id
 */
public class ElementCatalog {
  private final Map<JavaType, Map<String, Element<?>>> catalogs;
  private Finder finder;

  /**
   * <p>Constructor for ElementCatalog.</p>
   */
  public ElementCatalog() {
    catalogs = new IdentityHashMap<>();
  }

  /**
   * <p>addElement.</p>
   *
   * @param identifier a {@link String} object.
   * @param target     a T object.
   * @param <T>        a T object.
   * @return a {@link Element} object.
   */
  public <T> Optional<Element<T>> addElement(String identifier, T target) {
    if (target != null) {
      JavaType type = MapperHelper.getGenericType(target.getClass());
      if (type == null) {
        return Optional.empty();
      }
      Map<String, Element<?>> catalog = catalogs.getOrDefault(type, new HashMap<>());
      Element<T> element = new Element<>(identifier, target);
      catalog.put(identifier, element);
      this.catalogs.put(type, catalog);
      return Optional.of(element);
    }
    return Optional.empty();
  }

  /**
   * <p>addElement.</p>
   *
   * @param identifier a {@link String} object.
   * @param type       a T object.
   * @param <T>        a T object.
   * @return a {@link Element} object.
   */
  public <T> Optional<Element<T>> addLazyElement(String identifier, Class<T> type, Field field) {
    if (field != null && type != null) {
      JavaType javaType = MapperHelper.getGenericType(type);
      if (javaType == null) {
        return Optional.empty();
      }
      Map<String, Element<?>> catalog = catalogs.getOrDefault(javaType, new HashMap<>());
      Element<T> element = new Element<>(identifier, field);
      catalog.put(identifier, element);
      this.catalogs.put(javaType, catalog);
      return Optional.of(element);
    }
    return Optional.empty();
  }

  /**
   * <p>addElement.</p>
   *
   * @param identifier a {@link String} object.
   * @param type       a T object.
   * @param <T>        a T object.
   * @return a {@link Element} object.
   */
  public <T> Optional<Element<T>> addLazyElement(String identifier, TypeReference<T> type, Field field) {
    if (field != null && type != null) {
      JavaType javaType = MapperHelper.getGenericType(type);
      if (javaType == null) {
        return Optional.empty();
      }
      Map<String, Element<?>> catalog = catalogs.getOrDefault(javaType, new HashMap<>());
      Element<T> element = new Element<>(identifier, field);
      catalog.put(identifier, element);
      this.catalogs.put(javaType, catalog);
      return Optional.of(element);
    }
    return Optional.empty();
  }

  /**
   * <p>addElement.</p>
   *
   * @param identifier a {@link String} object.
   * @param type       a T object.
   * @param <T>        a T object.
   * @return a {@link Element} object.
   */
  public <T> Optional<Element<T>> addLazyElement(String identifier, JavaType type, Field field) {
    if (field != null && type != null) {
      Map<String, Element<?>> catalog = catalogs.getOrDefault(type, new HashMap<>());
      Element<T> element = new Element<>(identifier, field);
      catalog.put(identifier, element);
      this.catalogs.put(type, catalog);
      return Optional.of(element);
    }
    return Optional.empty();
  }

  public boolean hasElement(String query) {
    AtomicBoolean result = new AtomicBoolean(false);
    for (Map<String, Element<?>> elements : catalogs.values()) {
      for (Element<?> element : elements.values()) {
        if (element.isMatch(query)) {
          result.set(true);
          break;
        }
      }
      if (result.get()) {
        break;
      }
    }
    return result.get();
  }

  private boolean assignableType(JavaType input, JavaType expected) {
    if (input == null || expected == null) {
      return false;
    }
    return input.isTypeOrSubTypeOf(expected.getRawClass());
  }

  /**
   * <p>findElement.</p>
   *
   * @param type  a {@link JavaType} object.
   * @param query a {@link String} object.
   * @param <T>   a T object.
   * @return a T object.
   */
  @SuppressWarnings("unchecked")
  private <T> T findElement(JavaType type, String query, Object instance) {
    if (type == null || query == null) {
      return null;
    }
    List<JavaType> targetTypes = catalogs.keySet()
      .stream()
      .filter(key -> key != null && assignableType(key, type))
      .toList();
    AtomicReference<T> result = new AtomicReference<>();
    for (JavaType key : targetTypes) {
      Map<String, Element<?>> catalog = catalogs.getOrDefault(key, new HashMap<>());
      if (!catalog.isEmpty()) {
        Element<?> element = catalog.getOrDefault(query, null);
        if (ObjectUtils.isEmpty(element)) {
          Optional<Map.Entry<String, Element<?>>> candidate = catalog.entrySet()
            .stream()
            .filter(value -> value.getValue()
              .isMatch(query))
            .findAny();
          result.set((T) candidate.map(stringElementEntry -> resolveElement(
              key,
              stringElementEntry.getValue(),
              instance
            ))
            .orElse(null));
        } else {
          result.set(resolveElement(key, element, instance));
        }
      }

      if (ObjectUtils.isNotEmpty(result.get())) {
        break;
      }
    }

    return result.get();
  }

  @SuppressWarnings("unchecked")
  private <T> T resolveElement(JavaType type, Element<?> element, Object instance) {
    if (element.isLazy()) {
      return (T) element.getLazyElement(instance);
    } else {
      return (T) element.getElement();
    }
  }

  /**
   * <p>findBy.</p>
   *
   * @param clazz a {@link Class} object.
   * @return a {@link Finder} object.
   */
  public Finder findBy(Class<?> clazz) {
    if (clazz == null) {
      throw new IllegalArgumentException("findBy(Class) does not accept null");
    }
    this.finder = new Finder(clazz);
    return this.finder;
  }

  /**
   * <p>findBy.</p>
   *
   * @param type a {@link JavaType} object.
   * @return a {@link Finder} object.
   */
  public Finder findBy(JavaType type) {
    if (type == null) {
      throw new IllegalArgumentException("findBy(JavaType) does not accept null");
    }
    this.finder = new Finder(type);
    return this.finder;
  }

  /**
   * <p>findBy.</p>
   *
   * @param typeReference a {@link TypeReference} object.
   * @return a {@link Finder} object.
   */
  public Finder findBy(TypeReference<?> typeReference) {
    if (typeReference == null) {
      throw new IllegalArgumentException("findBy(TypeReference) does not accept null");
    }
    this.finder = new Finder(typeReference);
    return this.finder;
  }

  /**
   * Thread-safe: iterates over a snapshot of keys so concurrent findBy() on same catalog cannot cause CME.
   *
   * @param instance     page instance
   * @param keysSnapshot copy of keys to iterate (not the mutable list)
   * @param query        element query
   * @return entry or null
   */
  protected Map.Entry<JavaType, Object> getResult(Object instance, List<JavaType> keysSnapshot, String query) {
    if (keysSnapshot == null || keysSnapshot.isEmpty() || query == null) {
      return null;
    }
    Object result = null;
    JavaType type = null;
    for (JavaType key : keysSnapshot) {
      Object temp = findElement(key, query, instance);
      if (temp != null) {
        result = temp;
        type = key;
        break;
      }
    }
    return type == null ? null : new AbstractMap.SimpleEntry<>(type, result);
  }

  /**
   * @deprecated use {@link #getResult(Object, List, String)} for thread-safe lookup
   */
  protected Map.Entry<JavaType, Object> getResult(Object instance) {
    if (this.finder == null) {
      return null;
    }
    List<JavaType> keysSnapshot = new ArrayList<>(this.finder.getKeys());
    return getResult(instance, keysSnapshot, this.finder.getQuery());
  }


  public static class Element<T> {
    private final String identifier;
    private final T element;
    private final Field field;
    @Getter
    private final boolean lazy;
    private List<String> aliases;

    public Element(String identifier, T element) {
      this.identifier = identifier.trim()
        .toLowerCase();
      this.aliases = new ArrayList<>();
      this.element = element;
      this.field = null;
      this.lazy = false;
    }

    public Element(String identifier, Field field) {
      this.identifier = identifier.trim()
        .toLowerCase();
      this.aliases = new ArrayList<>();
      this.element = null;
      this.field = field;
      this.lazy = true;
    }

    public void addAlias(String alias) {
      this.aliases.add(alias);
    }

    public void setAliases(List<String> aliases) {
      if (aliases != null) {
        this.aliases = aliases.stream()
          .filter(alias -> !alias.equals(this.identifier))
          .collect(Collectors.toList());
      }
    }

    public boolean isMatch(String query) {
      if (query == null) {
        return false;
      }
      String normalized = query.trim().toLowerCase();
      return this.identifier.equals(normalized) || this.aliases.contains(normalized);
    }

    public T getElement() {
      return isLazy() ? null : this.element;
    }

    @SuppressWarnings("unchecked")
    public T getLazyElement(Object instance) {
      try {
        return isLazy() ? (T) field.get(instance) : null;
      } catch (IllegalAccessException e) {
        return null;
      }
    }
  }


  public class Finder {
    List<JavaType> keys;
    String query;

    public Finder(Class<?> key) {
      keys = new ArrayList<>();
      JavaType jt = MapperHelper.getGenericType(key);
      if (jt != null) {
        keys.add(jt);
      }
    }

    public Finder(TypeReference<?> key) {
      keys = new ArrayList<>();
      JavaType jt = MapperHelper.getGenericType(key);
      if (jt != null) {
        keys.add(jt);
      }
    }

    public Finder(JavaType javaType) {
      keys = new ArrayList<>();
      keys.add(javaType);
    }

    protected String getQuery() {
      return this.query;
    }

    protected List<JavaType> getKeys() {
      return this.keys;
    }

    public Finder orBy(Class<?> key) {
      if (key != null) {
        JavaType jt = MapperHelper.getGenericType(key);
        if (jt != null) {
          keys.add(jt);
        }
      }
      return this;
    }

    public Finder orBy(TypeReference<?> key) {
      if (key != null) {
        JavaType jt = MapperHelper.getGenericType(key);
        if (jt != null) {
          keys.add(jt);
        }
      }
      return this;
    }

    public Finder orBy(JavaType key) {
      if (key != null) {
        keys.add(key);
      }
      return this;
    }

    public Finder withQuery(String query) {
      this.query = query;
      return this;
    }

    public Map.Entry<JavaType, Object> getResult(Object instance) {
      if (this.query == null || this.getKeys().isEmpty()) {
        return null;
      }
      List<JavaType> keysSnapshot = new ArrayList<>(this.keys);
      return ElementCatalog.this.getResult(instance, keysSnapshot, this.query);
    }
  }
}
