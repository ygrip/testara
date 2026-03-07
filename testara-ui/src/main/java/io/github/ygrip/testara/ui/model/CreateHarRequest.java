package io.github.ygrip.testara.ui.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * <p>CreateHarRequest class.</p>
 *
 * @author yunaz.ramadhan on 8/16/2020
 * @version $Id: $Id
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateHarRequest {
  private boolean captureHeaders;
  private boolean captureCookies;
  private boolean captureContent;
  private boolean captureBinaryContent;
  private String initialPageRef;
  private String initialPageTitle;
}
