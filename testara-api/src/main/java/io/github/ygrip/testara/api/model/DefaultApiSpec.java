package io.github.ygrip.testara.api.model;

import lombok.Data;

import java.util.Map;

/**
 * <p>DefaultApiSpec class.</p>
 *
 * @author yunaz.ramadhan on 10/4/2019
 * @version $Id: $Id
 */
@Data
public class DefaultApiSpec {
  private Map<String, Object> header;
  private Map<String, Object> parameter;
  private Map<String, Object> form_param;
}
