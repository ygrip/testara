package io.github.ygrip.testara.ui.model;

/**
 * Screenshot optimization presets used for automatically attached screenshots.
 */
public enum ScreenshotQuality {
  HIGH(0.90f, 2560),
  STANDARD(0.60f, 1920),
  LOW(0.40f, 1280);

  private final float jpegQuality;
  private final int maxLongEdge;

  ScreenshotQuality(float jpegQuality, int maxLongEdge) {
    this.jpegQuality = jpegQuality;
    this.maxLongEdge = maxLongEdge;
  }

  public float jpegQuality() {
    return jpegQuality;
  }

  public int maxLongEdge() {
    return maxLongEdge;
  }
}
