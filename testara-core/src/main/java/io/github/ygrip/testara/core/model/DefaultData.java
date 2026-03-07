package io.github.ygrip.testara.core.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.Map;

/**
 * <p>DefaultData class.</p>
 *
 * @author yunaz.ramadhan on 10/4/2019
 * @version $Id: $Id
 */
public class DefaultData {
  @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
  private Map<String, Object> _data;

  /**
   * <p>Constructor for DefaultData.</p>
   */
  public DefaultData() {
    this._data = new HashMap<>();
  }

  /**
   * <p>addDefaultData.</p>
   *
   * @param key a {@link String} object.
   * @param value a {@link Object} object.
   */
  public void addDefaultData(String key, Object value) {
    if (this._data == null) {
      this._data = new HashMap<>();
    }
    this._data.put(key, value);
  }

  /**
   * <p>get.</p>
   *
   * @return a {@link Map} object.
   */
  public Map<String, Object> get() {
    if (this._data == null) {
      this._data = new HashMap<>();
    }
    return this._data;
  }

  /**
   * <p>set.</p>
   *
   * @param data a {@link Map} object.
   */
  public void set(Map<String, Object> data) {
    this._data = data;
  }
}
