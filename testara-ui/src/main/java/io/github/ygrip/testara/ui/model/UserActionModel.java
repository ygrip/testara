package io.github.ygrip.testara.ui.model;

import lombok.Builder;
import lombok.Data;

/**
 * <p>UserActionModel class.</p>
 *
 * @author yunaz.ramadhan on 8/21/2020
 * @version $Id: $Id
 */
@Data
@Builder
public class UserActionModel {
  private Class<?> actionClass;
  private String identifier;
  private String methodName;
  private boolean isStatic;
  private boolean allowAnonymousCall;
  private Integer parameterSize;
  private String[] parameterNames;
  private Class<?>[] parameterTypes;
}
