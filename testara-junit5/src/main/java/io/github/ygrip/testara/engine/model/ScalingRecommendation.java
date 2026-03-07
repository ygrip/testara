package io.github.ygrip.testara.engine.model;

/**
 * Recommendation for scaling parallelism up, down, or maintaining current level
 */
public enum ScalingRecommendation {
  /**
   * Increase parallelism - more work than workers can handle
   */
  SCALE_UP,

  /**
   * Decrease parallelism - workers are mostly idle
   */
  SCALE_DOWN,

  /**
   * Maintain current parallelism - good balance
   */
  MAINTAIN;

  public boolean shouldScale() {
    return this != MAINTAIN;
  }
}
