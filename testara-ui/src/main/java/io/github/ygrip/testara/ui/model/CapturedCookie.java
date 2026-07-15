package io.github.ygrip.testara.ui.model;

import java.util.Date;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CapturedCookie {
  private String name;
  private String value;
  private String comment;
  private Date expiryDate;
  private String domain;
  private String path;
  private boolean secured;
  private boolean httpOnly;
  private String sameSite;
  private Long maxAge;
  private int version;
}
