package io.github.ygrip.testara.ui.driver;

import io.github.ygrip.testara.ui.config.AbstractDriverProperties;
import io.github.ygrip.testara.ui.model.DeviceType;
import io.github.ygrip.testara.ui.page.PageContext;
import io.github.ygrip.testara.ui.page.PageFinder;

/**
 * Single session contract: capabilities + lifecycle + optional raw driver.
 * Use {@link #capability(Class)} for engine-agnostic actions (Screenplay-style).
 * Use {@link #instance()} only for engine internals or migration; tests should not reference it.
 */
public interface DriverSession<D> extends AutoCloseable {

  /**
   * Obtain a capability implementation. Unsupported types throw {@link UnsupportedOperationException}.
   */
  <T> T capability(Class<T> type);

  Class<? extends AbstractDriverProperties> configType();

  <F extends PageFinder<?, ?, ?>> F finder();

  @Override
  void close();

  boolean isActive();

  /**
   * Raw driver. For engine internals only; prefer capability() in tests.
   */
  D instance();

  DeviceType platform();

  default <T> T instanceOf(Class<T> type) {
    return type.cast(instance());
  }

  default String sessionName() {
    try {
      return DriverSessionManager.inThisTestThread()
        .getDriverName(this);
    } catch (Exception err) {
      return null;
    }
  }

  /**
   * Bind the driver to this session. Used by engine factories.
   */
  DriverSession<D> using(D driver);

  /**
   * Bind the platform to this session.
   */
  DriverSession<D> on(DeviceType platform);

  default boolean isOn(PageContext<?> page){
    final var current = page.isCurrentPage();
    if(current){
      finder().setCurrentPage(page);
    }
    return current;
  }
}
