package io.github.ygrip.testara.testcontainers;

import io.github.ygrip.testara.core.context.ResourceShutdownRegistry;
import org.testcontainers.lifecycle.Startable;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Thread-safe lazy lifecycle wrapper for a Testcontainers resource.
 *
 * <p>The wrapper owns at most one successfully started resource at a time. It is intended to be
 * kept in a Testara GLOBAL-scoped component, a static test fixture, or another run-scoped owner.
 * The managed resource is registered with {@link ResourceShutdownRegistry}, so Testara stops it at
 * the end of the test run even when the caller does not invoke {@link #stop()} explicitly.</p>
 *
 * <p>This class does not enable Testcontainers' cross-process reusable-container feature. Reuse is
 * limited to callers sharing this wrapper inside the current JVM/test run.</p>
 *
 * @param <T> Testcontainers resource type
 */
public final class ManagedTestContainer<T extends Startable> implements TestContainerResource<T> {

  private static final String SHUTDOWN_PREFIX = "testcontainer:";

  private final String name;
  private final Supplier<? extends T> factory;
  private final Object lifecycleLock = new Object();
  private final String shutdownKey;

  private volatile T resource;

  private ManagedTestContainer(String name, Supplier<? extends T> factory) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("name must not be blank");
    }
    this.name = name.trim();
    this.factory = Objects.requireNonNull(factory, "factory must not be null");
    this.shutdownKey = SHUTDOWN_PREFIX + this.name + ":"
        + Integer.toHexString(System.identityHashCode(this));
  }

  public static <T extends Startable> ManagedTestContainer<T> of(
      String name,
      Supplier<? extends T> factory
  ) {
    return new ManagedTestContainer<>(name, factory);
  }

  public String name() {
    return name;
  }

  @Override
  public T getOrStart() {
    T current = resource;
    if (current != null) {
      return current;
    }

    synchronized (lifecycleLock) {
      if (resource != null) {
        return resource;
      }

      T created = Objects.requireNonNull(
          factory.get(),
          "Testcontainer factory returned null for " + name
      );
      created.start();
      resource = created;
      ResourceShutdownRegistry.register(shutdownKey, this::stop);
      return created;
    }
  }

  @Override
  public Optional<T> current() {
    return Optional.ofNullable(resource);
  }

  @Override
  public boolean isStarted() {
    return resource != null;
  }

  @Override
  public void stop() {
    T toStop;
    synchronized (lifecycleLock) {
      toStop = resource;
      if (toStop == null) {
        return;
      }
      resource = null;
      ResourceShutdownRegistry.unregister(shutdownKey);
    }
    toStop.stop();
  }
}
