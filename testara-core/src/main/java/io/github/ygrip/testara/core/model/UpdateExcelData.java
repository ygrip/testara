package io.github.ygrip.testara.core.model;

import lombok.Data;

/**
 * <p>UpdateExcelData class.</p>
 *
 * @author yunaz.ramadhan on 6/26/2020
 * @version $Id: $Id
 */
@Data
public class UpdateExcelData {
  private Integer row;
  private String column;
  private Object data;
}
