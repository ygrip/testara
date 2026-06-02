package io.github.ygrip.testara.ui.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

/**
 * Engine-agnostic element locator (CSS-first, plan §8). Use static factories to build.
 */
public final class Locator {
  private final Selector strategy;
  private final String value;
  private final Map<String, Object> parameters;
  private final boolean template;
  private final boolean catalogReference;

  private Locator(Selector strategy, String value) {
    this.strategy = strategy;
    this.value = value;
    this.parameters = Collections.emptyMap();
    this.template = LocatorTemplate.hasTemplate(value);
    this.catalogReference = false;
  }

  private Locator(Selector strategy, String value, Map<String, Object> parameters) {
    this.strategy = strategy;
    this.value = value;
    this.parameters = Map.copyOf(parameters);
    this.template = LocatorTemplate.hasTemplate(value);
    this.catalogReference = false;
  }

  private Locator(Selector strategy, String value, Map<String, Object> parameters, boolean catalogReference) {
    this.strategy = strategy;
    this.value = value;
    this.parameters = Map.copyOf(parameters);
    this.template = LocatorTemplate.hasTemplate(value);
    this.catalogReference = catalogReference;
  }

  public Selector getStrategy() {
    return strategy;
  }

  public String getValue() {
    return value;
  }

  /** CSS selector (default). */
  public static Locator css(String cssSelector) {
    return new Locator(Selector.CSS, cssSelector);
  }

  /** XPath expression. */
  public static Locator xpath(String xpathExpression) {
    return new Locator(Selector.XPATH, xpathExpression);
  }

  /** Id. */
  public static Locator id(String id) {
    return new Locator(Selector.ID, id);
  }

  public static Locator className(String className) {
    return new Locator(Selector.CLASS, className);
  }

  public static Locator tagName(String tagName) {
    return new Locator(Selector.TAG, tagName);
  }

  public static Locator linkText(String linkText) {
    return new Locator(Selector.LINKTEXT, linkText);
  }

  public static Locator partialLink(String partialLink) {
    return new Locator(Selector.PARTIALLINK, partialLink);
  }

  public static Locator name(String name) {
    return new Locator(Selector.NAME, name);
  }

  /** By strategy and value. */
  public static Locator of(Selector strategy, String value) {
    return new Locator(strategy, value);
  }

  public Locator with(String name, Object value) {
    Map<String, Object> next = new LinkedHashMap<>(this.parameters);
    next.put(name, value);
    return new Locator(this.strategy, this.value, next, this.catalogReference);
  }

  public Locator with(Map<String, ?> values) {
    Map<String, Object> next = new LinkedHashMap<>(this.parameters);
    next.putAll(values);
    return new Locator(this.strategy, this.value, next, this.catalogReference);
  }

  public Locator format(Object... values) {
    return new Locator(this.strategy, String.format(this.value, values), this.parameters);
  }

  public String resolvedValue() {
    return LocatorTemplate.render(this.value, this.strategy, this.parameters);
  }

  public boolean hasParameters() {
    return this.template;
  }

  public boolean isCatalogReference() {
    return catalogReference;
  }

  public Set<String> parameterNames() {
    return LocatorTemplate.parameterNames(this.value);
  }

  public Map<String, Object> getParameters() {
    return parameters;
  }

  /** Catalog element reference by name — not a CSS/XPath selector. */
  public static Locator reference(String name) {
    return new Locator(null, name, Collections.emptyMap(), true);
  }

  /**
   * Parse a string like "css:#submit", "id:username", "xpath://button".
   * Defaults to CSS if no prefix.
   */
  public static Locator parse(String locator) {
    if (StringUtils.isBlank(locator)) {
      throw new IllegalArgumentException("locator cannot be blank");
    }
    String s = locator.trim();
    int colon = s.indexOf(':');
    if (colon <= 0) {
      return css(s);
    }
    String prefix = s.substring(0, colon).trim().toLowerCase();
    String value = s.substring(colon + 1).trim();
    return switch (prefix) {
      case "id" -> id(value);
      case "xpath" -> xpath(value);
      case "css" -> css(value);
      case "name" -> of(Selector.NAME, value);
      case "class" -> of(Selector.CLASS, value);
      case "tag" -> of(Selector.TAG, value);
      case "link" -> of(Selector.LINKTEXT, value);
      case "partial-link" -> of(Selector.PARTIALLINK, value);
      case "accessibility" -> of(Selector.ACCESSIBILITY, value);
      case "android-ui-automator" -> of(Selector.ANDROID_UI_AUTOMATOR, value);
      case "ios-class-chain" -> of(Selector.IOS_CLASS_CHAIN, value);
      default -> css(locator);
    };
  }

  @Override
  public String toString(){
    return String.format("%s:%s", getStrategy(), getValue());
  }
}
