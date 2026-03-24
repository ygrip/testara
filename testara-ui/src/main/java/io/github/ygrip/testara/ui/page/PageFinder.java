package io.github.ygrip.testara.ui.page;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;

import com.google.common.base.Stopwatch;

import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.core.scan.ClassScanner;
import io.github.ygrip.testara.core.support.CommonHelper;
import io.github.ygrip.testara.ui.config.AbstractDriverProperties;
import io.github.ygrip.testara.ui.error.ElementNotFoundException;
import io.github.ygrip.testara.ui.error.PageNotFoundException;
import io.github.ygrip.testara.ui.model.DeviceType;
import io.github.ygrip.testara.ui.model.Locator;
import io.github.ygrip.testara.ui.model.Page;
import io.github.ygrip.testara.ui.model.Selector;
import io.github.ygrip.testara.ui.populator.ElementCatalog;
import lombok.Getter;
import lombok.Setter;

/**
 * <p>PageFinder interface.</p>
 *
 * @author yunaz.ramadhan on 12/23/2019
 * @version $Id: $Id
 */
public abstract class PageFinder<P extends PageContext<?>, E, B> {
  private final Map<DeviceType, Map<String, Class<? extends P>>> PAGES = new HashMap<>();
  private final Map<Class<? extends P>, ElementCatalog> CATALOGS = new ConcurrentHashMap<>();
  private DeviceType currentDeviceType;
  @Setter
  @Getter
  private boolean suppressLog;
  private P currentPage;

  @SuppressWarnings("unchecked")
  public <P extends PageContext<?>> P getCurrentPage() {
    return (P) currentPage;
  }

  @SuppressWarnings("unchecked")
  public <C extends PageContext<?>> void setCurrentPage(C page) {
    this.currentPage = (P) page;
  }

  public abstract Class<? extends AbstractDriverProperties> configType();

  public abstract Logger log();

  protected Map<DeviceType, Map<String, Class<? extends P>>> pages() {
    if (ObjectUtils.isEmpty(PAGES) || PAGES.isEmpty()) {
      PAGES.putAll(populatePageClasses());
    }
    return PAGES;
  }

  Map<String, Class<? extends P>> pagesOn(DeviceType deviceType) {
    return pages().get(deviceType);
  }

  /**
   * <p>getPages.</p>
   *
   * @param deviceType a {@link io.github.ygrip.testara.ui.model.DeviceType} object.
   * @return a {@link Map} object.
   */
  Map<Class<? extends P>, P> getPages(DeviceType deviceType) {
    final var pages = pages();
    Map<String, Class<? extends P>> pagesOnDevice = pages.getOrDefault(
      Optional.ofNullable(deviceType)
        .orElse(DeviceType.DEFAULT), new HashMap<>()
    );

    Map<Class<? extends P>, P> results = new HashMap<>();
    pagesOnDevice.forEach((key, value) -> {
      results.put(value, getPageInstance(value));
    });
    return results;
  }

  /**
   * <p>getPage.</p>
   *
   * @param pageName a {@link String} object.
   * @return a {@link P} object.
   * @throws Exception if any.
   */
  @SuppressWarnings("unchecked")
  public <T extends P> T getPage(String pageName) throws Exception {
    Map<String, Class<? extends P>> pages = pagesOn(getCurrentDeviceType());
    final var candidate = pages.get(pageName);
    if (ObjectUtils.isNotEmpty(candidate)) {
      return (T) getPageInstance(candidate);
    } else {
      throw new PageNotFoundException(String.format("Unable to locate page with name %s", pageName));
    }
  }

  public B getLocator(String element) {
    return getLocator(getCurrentPage(), element);
  }

  public abstract B getLocator(P page, String element);

  /**
   * Resolve a {@link Locator} to the engine-specific locator (e.g. By).
   */
  public abstract B getLocator(Locator locator);

  /**
   * <p>getElement.</p>
   *
   * @param page    a {@link String} object.
   * @param element a {@link String} object.
   * @return a {@link Supplier<E>} object.
   * @throws Exception if any.
   */
  public Supplier<E> getElement(String page, String element) throws Exception {
    P pageObject = getPage(page);

    return getElement(pageObject, element);
  }

  /**
   * <p>getElementFromPage.</p>
   *
   * @param page    a {@link P} object.
   * @param element a {@link String} object.
   * @return a {@link E} object.
   * @throws Exception if any.
   */
  public abstract Supplier<E> getElementFromPage(P page, String element) throws Exception;

  /**
   * <p>getElementFromPage.</p>
   *
   * @param page    a {@link P} object.
   * @param locator a {@link Locator} object.
   * @return a {@link E} object.
   * @throws Exception if any.
   */
  public abstract Supplier<E> getElementFromPage(P page, Locator locator) throws Exception;

  public Supplier<E> getElement(P page, String element) throws Exception {
    if (StringUtils.isBlank(element)) {
      throw new IllegalArgumentException("Element name cannot be blank");
    }

    Supplier<E> result = null;

    // Try to find in specified page
    if (page != null) {
      result = getElementFromPage(page, element);
    } else {
      // Search across all pages
      List<P> candidates = getPagesContainingElement(element);
      for (P candidate : candidates) {
        result = getElementFromPage(candidate, element);
        if (result != null) {
          break;
        }
      }
    }

    // Try locator resolution
    if (result == null) {
      result = resolveTargetFromLocator(page, element);
    }

    if (result == null) {
      String errorMsg = page == null ? String.format("Element not found: %s", element) : String.format(
        "Element '%s' not found in page: %s",
        element,
        page.getClass()
          .getSimpleName()
      );
      if (!suppressLog) {
        log().error(errorMsg);
      }
      throw new ElementNotFoundException(errorMsg);
    }

    return result;
  }

  /**
   * <p>getElements.</p>
   *
   * @param page    a {@link String} object.
   * @param element a {@link String} object.
   * @return a {@link E} object.
   * @throws Exception if any.
   */
  public Supplier<List<E>> getElements(String page, String element) throws Exception {
    P pageObject = getPage(page);

    return getElements(pageObject, element);
  }

  /**
   * <p>getElementsFromPage.</p>
   *
   * @param page    a {@link P} object.
   * @param element a {@link String} object.
   * @return a {@link E} object.
   * @throws Exception if any.
   */
  public abstract Supplier<List<E>> getElementsFromPage(P page, String element) throws Exception;

  /**
   * <p>getElementsFromPage.</p>
   *
   * @param page    a {@link P} object.
   * @param locator a {@link Locator} object.
   * @return a {@link E} object.
   * @throws Exception if any.
   */
  public abstract Supplier<List<E>> getElementsFromPage(P page, Locator locator) throws Exception;

  public Supplier<List<E>> getElements(P page, String element) throws Exception {
    if (StringUtils.isBlank(element)) {
      throw new IllegalArgumentException("Element name cannot be blank");
    }

    Supplier<List<E>> result = ArrayList::new;

    // Try to find in specified page
    if (page != null) {
      result = getElementsFromPage(page, element);
    } else {
      // Search across all pages
      List<P> candidates = getPagesContainingElement(element);
      for (P candidate : candidates) {
        result = getElementsFromPage(candidate, element);
        if (ObjectUtils.isNotEmpty(result)) {
          break;
        }
      }
    }

    // Try locator resolution
    if (ObjectUtils.isEmpty(result)) {
      result = resolveTargetsFromLocator(page, element);
    }

    if (result == null) {
      String errorMsg = page == null ? String.format("Element not found: %s", element) : String.format(
        "Element '%s' not found in page: %s",
        element,
        page.getClass()
          .getSimpleName()
      );
      if (!suppressLog) {
        log().error(errorMsg);
      }
      throw new ElementNotFoundException(errorMsg);
    }

    return result;
  }

  /**
   * Resolve a {@link Locator} to the engine-specific locator (e.g. By).
   */
  public abstract Supplier<E> getElement(Locator locator) throws Exception;

  /**
   * Resolve a {@link Locator} to the engine-specific locator (e.g. By).
   */
  public abstract Supplier<List<E>> getElements(Locator locator) throws Exception;


  public abstract List<E> getElementsWithRoot(E parent, B locator);

  public abstract E getElementWithRoot(E parent, B locator);

  public abstract E getPrecedingSiblingElement(E parent, B locator);

  public E getPrecedingSiblingElement(E element) {
    return getPrecedingSiblingElement(element, null);
  }

  public abstract List<E> getPrecedingSiblingElements(E parent, B locator);

  public List<E> getPrecedingSiblingElements(E element) {
    return getPrecedingSiblingElements(element, null);
  }

  public abstract E getFollowingSiblingElement(E parent, B locator);

  public E getFollowingSiblingElement(E element) {
    return getFollowingSiblingElement(element, null);
  }

  public abstract List<E> getFollowingSiblingElements(E parent, B locator);

  public List<E> getFollowingSiblingElements(E element) {
    return getFollowingSiblingElements(element, null);
  }

  public abstract List<E> getSiblings(E parent, B locator);

  public List<E> getSiblings(E element) {
    return getSiblings(element, null);
  }

  public abstract E getChildNode(E parent, B locator, int childIndex);

  public E getChildNode(E element, int childIndex) {
    return getChildNode(element, null, childIndex);
  }

  // ==================== LOCATOR RESOLUTION ====================

  private Map.Entry<String, Selector> parseElementSelector(String element) {
    String[] parts = element.split(":");
    Selector selector;

    if (parts.length > 1) {
      selector = CommonHelper.searchEnum(
        Selector.class,
        parts[0].trim()
          .replace("-", "")
      );
      element = String.join(":", Arrays.copyOfRange(parts, 1, parts.length));
    } else {
      selector = null;
      element = String.join(":", parts);
    }

    return new AbstractMap.SimpleEntry<>(element, selector);
  }

  protected Locator resolveLocator(String element) {
    Map.Entry<String, Selector> locators = parseElementSelector(element);

    if (locators.getValue() == null) {
      return Locator.css(locators.getKey());
    }
    return Locator.of(locators.getValue(), locators.getKey());
  }

  private Supplier<E> resolveTargetFromLocator(P page, String element) {
    final var selector = parseElementSelector(element);

    if (selector.getValue() == null) {
      return null;
    }

    return usingSelector(page, selector.getKey(), selector.getValue());
  }

  private Supplier<List<E>> resolveTargetsFromLocator(P page, String element) {
    final var selector = parseElementSelector(element);

    if (selector.getValue() == null) {
      return null;
    }

    return usingSelectors(page, selector.getKey(), selector.getValue());
  }

  private Supplier<E> usingSelector(P page, String element, Selector selector) {
    final var finalSelector = Optional.ofNullable(selector)
      .orElse(Selector.CSS);
    try {
      return switch (finalSelector) {
        case ID -> getElementFromPage(page, Locator.id(element));
        case CSS -> getElementFromPage(page, Locator.css(element));
        case XPATH -> getElementFromPage(page, Locator.xpath(element));
        case CLASS -> getElementFromPage(page, Locator.className(element));
        case TAG -> getElementFromPage(page, Locator.tagName(element));
        case LINKTEXT -> getElementFromPage(page, Locator.linkText(element));
        case PARTIALLINK -> getElementFromPage(page, Locator.partialLink(element));
        case NAME -> getElementFromPage(page, Locator.name(element));
        default -> getElementFromPage(page, Locator.parse(element));
      };
    } catch (Exception e) {
      if (!isSuppressLog()) {
        log().error("Failed using selector {} for '{}': {}", selector, element, e.getMessage());
      }
      return null;
    }
  }

  private Supplier<List<E>> usingSelectors(P page, String element, Selector selector) {
    final var finalSelector = Optional.ofNullable(selector)
      .orElse(Selector.CSS);
    try {
      return switch (finalSelector) {
        case ID -> getElementsFromPage(page, Locator.id(element));
        case CSS -> getElementsFromPage(page, Locator.css(element));
        case XPATH -> getElementsFromPage(page, Locator.xpath(element));
        case CLASS -> getElementsFromPage(page, Locator.className(element));
        case TAG -> getElementsFromPage(page, Locator.tagName(element));
        case LINKTEXT -> getElementsFromPage(page, Locator.linkText(element));
        case PARTIALLINK -> getElementsFromPage(page, Locator.partialLink(element));
        case NAME -> getElementsFromPage(page, Locator.name(element));
        default -> getElementsFromPage(page, Locator.parse(element));
      };
    } catch (Exception e) {
      if (!isSuppressLog()) {
        log().error("Failed using selector {} for '{}': {}", selector, element, e.getMessage());
      }
      return null;
    }
  }

  /**
   * <p>getElement.</p>
   *
   * @param element a {@link String} object.
   * @return a {@link E} object.
   * @throws Exception if any.
   */
  public Supplier<E> getElement(String element) throws Exception {
    return getElement(getCurrentPage(), element);
  }

  /**
   * <p>getElements.</p>
   *
   * @param element a {@link String} object.
   * @return a {@link E} object.
   * @throws Exception if any.
   */
  public Supplier<List<E>> getElements(String element) throws Exception {
    return getElements(getCurrentPage(), element);
  }

  /**
   * <p>getPage.</p>
   *
   * @param pageObject a {@link Class} object.
   * @return a T object.
   * @throws Exception if any.
   */
  @SuppressWarnings("unchecked")
  public <T> T getPage(Class<T> pageObject) throws Exception {
    final var pages = pagesOn(getCurrentDeviceType());
    final var candidate = pages.values()
      .stream()
      .filter(page -> page.equals(pageObject))
      .findFirst();

    if (candidate.isPresent()) {
      return (T) getPageInstance(candidate.get());
    } else {
      throw new PageNotFoundException(String.format("Unable to locate page with name %s", pageObject.getSimpleName()));
    }
  }

  /**
   * <p>setDeviceType.</p>
   *
   * @param deviceType a {@link io.github.ygrip.testara.ui.model.DeviceType} object.
   */
  public void setDeviceType(DeviceType deviceType) {
    this.currentDeviceType = deviceType;
  }

  DeviceType getCurrentDeviceType() {
    return currentDeviceType;
  }

  @SuppressWarnings("unchecked")
  Class<P> getPageType() {
    Class<?> clazz = getClass();
    while (!Modifier.isAbstract(clazz.getSuperclass()
      .getModifiers())) {
      clazz = clazz.getSuperclass();
    }
    Class<?> finalClazz = clazz;
    return (Class<P>) resolveParameterType(CommonHelper.getParameterizedType(finalClazz, 0));
  }

  private Class<?> resolveParameterType(Type type) {
    if (type instanceof ParameterizedType) {
      return ((ParameterizedType) type).getRawType()
        .getClass();
    } else {
      return (Class<?>) type;
    }
  }

  private AbstractDriverProperties getConfig() {
    return TestFramework.configuration()
      .get(configType());
  }

  <P> P getPageInstance(Class<? extends P> type) {
    try {
      return TestFramework.context()
        .get(type);
    } catch (Exception ignored) {
      return TestFramework.factory()
        .getInstance(type);
    }
  }

  // ==================== PAGE CLASS DISCOVERY ====================

  @SuppressWarnings("unchecked")
  private Map<DeviceType, Map<String, Class<? extends P>>> populatePageClasses() {
    Map<DeviceType, Map<String, Class<? extends P>>> results = new HashMap<>();
    Stopwatch stopwatch = Stopwatch.createStarted();

    try {
      ClassScanner scanner = TestFramework.context()
        .get(ClassScanner.class);
      final var pageType = getPageType();
      final var locations = getConfig().getPageScanLocations();
      List<Class<?>> loaded = scanner.scanOnPackages(pageType, Page.class, locations)
        .get(30, TimeUnit.SECONDS);

      log().debug("Scanned {} page classes", loaded.size());

      for (Class<?> clazz : loaded) {
        if (clazz == null) {
          continue;
        }
        final var metadata = clazz.getAnnotation(Page.class);
        if (metadata == null) {
          continue;
        }
        final var name = metadata.name();
        if (StringUtils.isBlank(name)) {
          log().warn("Skipping page class {}: @Page name is blank", clazz.getName());
          continue;
        }
        final var platforms = metadata.platforms();

        for (var platform : platforms) {
          var pages = results.getOrDefault(platform, new HashMap<>());
          pages.put(name, (Class<? extends P>) clazz);
          results.put(platform, pages);
        }
      }

      long elapsed = stopwatch.stop()
        .elapsed(TimeUnit.MILLISECONDS);
      StringBuilder details = new StringBuilder();
      details.append("════════════════════════════════════════════════════════════════\n");
      details.append(String.format("             %s Initialized                     \n", getClass().getSimpleName()));
      details.append("════════════════════════════════════════════════════════════════\n");
      for (var platform : results.keySet()) {
        final var pages = results.get(platform);
        details.append(String.format(" [%s] | %s pages :\n", platform.name(), pages.size()));
        details.append("════════════════════════════════════════════════════════════════\n");
        pages.forEach((key, value) -> {
          details.append(String.format("\t |> %s : %s\n", key, value.getCanonicalName()));
        });
        details.append("════════════════════════════════════════════════════════════════\n");
      }
      log().debug("Done populated pages in {}ms, details : \n{}", elapsed, details.toString());

    } catch (TimeoutException e) {
      log().error("Timeout scanning for pages", e);
    } catch (Exception e) {
      Thread.currentThread()
        .interrupt();
      log().error("Failed to scan for pages: {}", e.getMessage(), e);
    }

    return results;
  }

  // ==================== ELEMENT CATALOG ====================

  @SuppressWarnings("unchecked")
  protected ElementCatalog buildPageCatalog(P page) {
    if (page == null) {
      log().error("Cannot build catalog for null page");
      return new ElementCatalog();
    }

    Class<? extends P> pageClass = (Class<? extends P>) page.getClass();
    if (pageClass == null) {
      log().error("page.getClass() returned null for {}", page);
      return new ElementCatalog();
    }

    if (CATALOGS.containsKey(pageClass)) {
      return CATALOGS.get(pageClass);
    }

    Stopwatch stopwatch = Stopwatch.createStarted();
    ElementCatalog result = new ElementCatalog();
    List<Field> fields = CommonHelper.getFieldsUpTo(pageClass, getPageType());

    for (Field field : fields) {
      try {
        field.setAccessible(true);
        Object value = field.get(page);

        resolveElementStrategy(result).apply(field, value);

      } catch (IllegalAccessException e) {
        log().error("Access error for field {} in {}: {}", field.getName(), pageClass.getName(), e.getMessage());
      } catch (Exception e) {
        log().debug("Failed to process field {} in {}: {}", field.getName(), pageClass.getSimpleName(), e.getMessage());
      }
    }

    CATALOGS.put(pageClass, result);
    long elapsed = stopwatch.stop()
      .elapsed(TimeUnit.MILLISECONDS);
    log().debug("Built catalog for {} in {}ms", pageClass.getSimpleName(), elapsed);

    return result;
  }

  protected abstract BiFunction<Field, Object, ElementCatalog> resolveElementStrategy(ElementCatalog catalog);

  protected List<String> generateAliases(String name) {
    if (StringUtils.isBlank(name))
      return new ArrayList<>();

    Set<String> aliases = new LinkedHashSet<>();

    // camelCase to space-separated
    String spaceSeparated = name.replaceAll("([a-z])([A-Z])", "$1 $2")
      .toLowerCase();
    aliases.add(spaceSeparated);

    // underscore to space
    aliases.add(name.replace("_", " ")
      .toLowerCase()
      .trim());

    // lowercase original
    aliases.add(name.toLowerCase());

    // Remove duplicates and original name
    aliases.remove(name);

    return new ArrayList<>(aliases);
  }

  // ==================== UTILITY METHODS ====================

  private List<P> getPagesContainingElement(String element) {
    if (StringUtils.isBlank(element)) {
      throw new ElementNotFoundException("Cannot search for blank element");
    }

    List<P> result = new ArrayList<>();
    DeviceType platform = getCurrentDeviceType();
    Map<String, Class<? extends P>> pages = pages().get(platform);
    if (pages == null || pages.isEmpty()) {
      return result;
    }

    for (Class<? extends P> pageClass : pages.values()) {
      if (pageClass == null) {
        continue;
      }
      ElementCatalog catalog = CATALOGS.get(pageClass);
      if (catalog != null && catalog.hasElement(element)) {
        try {
          P instance = getPage(pageClass);
          result.add(instance);
        } catch (Exception e) {
          log().error(
            "Failed to instantiate {} while searching for '{}': {}",
            pageClass.getSimpleName(),
            element,
            e.getMessage()
          );
        }
      }
    }

    if (!result.isEmpty()) {
      log().info("Found {} pages containing element '{}'", result.size(), element);
    }
    return result;
  }

}
