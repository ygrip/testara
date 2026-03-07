package io.github.ygrip.testara.ui.playwright.driver;

/**
 * Browser-specific stealth configuration to mitigate automated browser detection.
 * Each Playwright driver implements this with scripts and defaults tailored to its engine.
 */
public interface StealthProvider {

  /**
   * JavaScript init script injected into every page before any site scripts run.
   * Should mask automation-revealing properties for this specific browser engine.
   */
  String stealthInitScript();

  /**
   * Fallback user agent when none is configured in properties.
   * Should look like a realistic, non-automated browser string for this engine.
   */
  String defaultUserAgent();
}
