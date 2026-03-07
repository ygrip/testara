package io.github.ygrip.testara.core.model;

import io.github.ygrip.testara.core.support.StringHelper;
import lombok.Builder;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.List;

/**
 * <p>PopulatedTag class.</p>
 *
 * @author yunaz.ramadhan on 3/28/2020
 * @version $Id: $Id
 */
@Data
@Builder
public class PopulatedTag {
  private String name;
  private List<String> aliases;

  /**
   * {@inheritDoc}
   */
  @Override
  public String toString() {
    LinkedHashMap<String, Object> mapped = new LinkedHashMap<>();
    mapped.put("name", name);
    mapped.put("aliases", aliases);
    return StringHelper.prettyPrint(mapped);
  }
}
