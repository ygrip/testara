package io.github.ygrip.testara.api.model;

import lombok.Data;

/**
 * <p>ResponseMappingModel class.</p>
 *
 * @author yunaz.ramadhan on 10/4/2019
 * @version $Id: $Id
 */
@Data
public class ResponseMappingModel {
  private String success = ".success";
  private String errorCode = ".errorCode";
  private String errorMessage = ".errorMessage";
}
