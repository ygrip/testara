package io.github.ygrip.testara.ui.model;

import lombok.Builder;
import lombok.Data;

/**
 * <p>StartNewPageRequest class.</p>
 *
 * @author yunaz.ramadhan on 8/16/2020
 * @version $Id: $Id
 */
@Data
@Builder
public class StartNewPageRequest {
  private String pageRef;
  private String pageTitle;
}
