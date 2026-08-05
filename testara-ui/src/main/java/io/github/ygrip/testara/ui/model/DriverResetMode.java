package io.github.ygrip.testara.ui.model;

/**
 * When a worker thread's WebDriver/browser session(s) get quit and recreated.
 * <ul>
 *   <li>{@code NEVER} - drivers persist for the life of the worker thread; only quit when the
 *       whole run ends.</li>
 *   <li>{@code ON_EACH_SCENARIO} - drivers are quit and recreated after every scenario.</li>
 *   <li>{@code ON_EACH_SUITE} - drivers persist across scenarios from the same feature file,
 *       and are quit when the next scenario picked up by this thread belongs to a different
 *       feature.</li>
 * </ul>
 */
public enum DriverResetMode {
  NEVER, ON_EACH_SCENARIO, ON_EACH_SUITE
}
