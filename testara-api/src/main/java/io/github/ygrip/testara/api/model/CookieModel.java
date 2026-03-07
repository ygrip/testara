package io.github.ygrip.testara.api.model;

import lombok.Data;

import java.util.Date;

/**
 * <p>CookieModel class.</p>
 *
 * @author yunaz.ramadhan on 3/9/2021
 * @version $Id: $Id
 */
@Data
public class CookieModel {
  private String name;
  private String value;
  private String comment;
  private String path;
  private String domain;
  private boolean secured;
  private boolean httpOnly = false;
  private String sameSite;
  private Integer version;
  private Long maxAge;
  private Date expiryDate;
}
