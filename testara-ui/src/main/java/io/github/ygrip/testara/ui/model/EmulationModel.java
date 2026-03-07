package io.github.ygrip.testara.ui.model;

import lombok.Data;

/**
 * <p>EmulationModel class.</p>
 *
 * @author yunaz.ramadhan on 4/9/2021
 * @version $Id: $Id
 */
@Data
public class EmulationModel {
  private String deviceName;
  private DeviceDimension dimension;
  private boolean adjustDimension;
}
