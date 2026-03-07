package io.github.ygrip.testara.elastic.model;

import lombok.Data;

import java.util.List;

/**
 * <p>ElasticSearchModel class.</p>
 *
 * @author yunaz.ramadhan on 4/19/2021
 * @version $Id: $Id
 */
@Data
public class ElasticSearchModel {
  private List<String> hosts;
  private String username;
  private String password;
  private boolean secured = false;
  private boolean requireAuthentication;
}
