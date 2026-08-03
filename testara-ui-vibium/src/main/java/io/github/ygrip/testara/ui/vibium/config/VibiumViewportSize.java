package io.github.ygrip.testara.ui.vibium.config;

import lombok.Data;

/**
 * Per-device viewport dimensions for Vibium sessions. Bindable through
 * {@link VibiumDriverProperties#getViewport()}; not yet applied to a live session (Phase 2/3
 * wires this into {@code Page.setViewport(ViewportSize)}).
 */
@Data
public class VibiumViewportSize {
  private int width;
  private int height;
}
