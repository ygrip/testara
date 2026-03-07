package io.github.ygrip.testara.api.data;

import org.apache.commons.lang3.ObjectUtils;

import io.github.ygrip.testara.api.model.CommonResponseModel;
import io.github.ygrip.testara.core.context.TestComponent;
import io.github.ygrip.testara.core.model.DefaultData;
import io.github.ygrip.testara.core.model.ResponseData;
import io.github.ygrip.testara.core.registry.RegistryScope;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * <p>ApiResponseData class.</p>
 *
 * @author yunaz.ramadhan on 10/4/2019
 * @version $Id: $Id
 */
@Data
@ResponseData(order = 1)
@EqualsAndHashCode(callSuper = true)
@TestComponent(scope = RegistryScope.TEST)
public class ApiResponseData extends DefaultData {
  private CommonResponseModel data = CommonResponseModel.builder()
    .build();
  private boolean success;
  private int statusCode;
  private String errorCode;
  private String errorMessage;

  public void clearData() {
    if (ObjectUtils.isNotEmpty(data)) {
      this.data.clearData();
    }
    this.errorCode = null;
    this.errorMessage = null;
    this.statusCode = 0;
    this.success = false;
  }
}
