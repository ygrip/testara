package io.github.ygrip.testara.ui.model;

import com.browserup.harreader.model.HttpStatus;

import lombok.Builder;
import lombok.Data;

/**
 * <p>CreateWhitelistedRequest class.</p>
 *
 * @author yunaz.ramadhan on 8/16/2020
 * @version $Id: $Id
 */
@Data
@Builder
public class CreateWhitelistedRequest {
  private String regex;
  private HttpStatus status;
}
