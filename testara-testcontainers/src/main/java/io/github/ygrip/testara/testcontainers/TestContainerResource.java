package io.github.ygrip.testara.testcontainers;

import org.testcontainers.lifecycle.Startable;

import java.util.Optional;

/**
 * Lifecycle contract for a Testcontainers resource shared within a Testara test run.
 *
 * @param <T> Testcontainers resource type
 */
public interface TestContainerResource<T extends Startable> extends AutoCloseable {

  /**
   * Return the current resource or start it lazily when it has not been started yet.
   */
  T getOrStart();

  /**
   * Return the currently managed resource without starting a new one.
   */
  Optional<T> current();

  /**
   * Whether this resource currently owns a successfully started container.
   */
  boolean isStarted();

  /**
   * Stop the currently managed resource. Repeated calls are safe.
   */
  void stop();

  @Override
  default void close() {
    stop();
  }
}
