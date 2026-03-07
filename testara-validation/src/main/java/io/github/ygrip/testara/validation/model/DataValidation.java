package io.github.ygrip.testara.validation.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * <p>DataValidation class.</p>
 *
 * @author yunaz.ramadhan on 12/9/2019
 * @version $Id: $Id
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataValidation {
  private Object actual;
  private String validation;
  private Object expectation;
}
