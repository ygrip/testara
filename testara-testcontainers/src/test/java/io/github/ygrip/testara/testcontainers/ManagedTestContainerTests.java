package io.github.ygrip.testara.testcontainers;

import io.github.ygrip.testara.core.context.ResourceShutdownRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.lifecycle.Startable;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManagedTestContainerTests {

  @BeforeEach
  void setUp() {
    ResourceShutdownRegistry.reset();
  }

  @AfterEach
  void tearDown() {
    ResourceShutdownRegistry.shutdownAll();
    ResourceShutdownRegistry.reset();
  }

  @Test
  void getOrStartStartsOneContainerOnly() {
    AtomicInteger creations = new AtomicInteger();
    ManagedTestContainer<FakeStartable> resource = ManagedTestContainer.of(
        "one",
        () -> new FakeStartable(creations.incrementAndGet(), false)
    );

    FakeStartable first = resource.getOrStart();
    FakeStartable second = resource.getOrStart();

    assertSame(first, second);
    assertEquals(1, creations.get());
    assertEquals(1, first.starts.get());
    assertTrue(resource.isStarted());
  }

  @Test
  void getOrStartIsSafeAcrossConcurrentCallers() throws Exception {
    AtomicInteger creations = new AtomicInteger();
    ManagedTestContainer<FakeStartable> resource = ManagedTestContainer.of(
        "parallel",
        () -> new FakeStartable(creations.incrementAndGet(), false)
    );

    try (ExecutorService executor = Executors.newFixedThreadPool(8)) {
      List<Future<FakeStartable>> futures = java.util.stream.IntStream.range(0, 32)
          .mapToObj(index -> executor.submit(resource::getOrStart))
          .toList();

      FakeStartable expected = futures.getFirst().get();
      for (Future<FakeStartable> future : futures) {
        assertSame(expected, future.get());
      }
    }

    assertEquals(1, creations.get());
  }

  @Test
  void frameworkShutdownStopsTheManagedContainer() {
    ManagedTestContainer<FakeStartable> resource = ManagedTestContainer.of(
        "shutdown",
        () -> new FakeStartable(1, false)
    );
    FakeStartable started = resource.getOrStart();

    ResourceShutdownRegistry.shutdownAll();

    assertEquals(1, started.stops.get());
    assertFalse(resource.isStarted());
    assertTrue(resource.current().isEmpty());
  }

  @Test
  void failedStartIsNotCachedAndCanBeRetried() {
    AtomicInteger creations = new AtomicInteger();
    ManagedTestContainer<FakeStartable> resource = ManagedTestContainer.of(
        "retry",
        () -> {
          int creation = creations.incrementAndGet();
          return new FakeStartable(creation, creation == 1);
        }
    );

    assertThrows(IllegalStateException.class, resource::getOrStart);
    assertFalse(resource.isStarted());

    FakeStartable retry = resource.getOrStart();
    assertEquals(2, retry.id);
    assertEquals(2, creations.get());
  }

  @Test
  void stopIsIdempotentAndAllowsFreshStart() {
    AtomicInteger creations = new AtomicInteger();
    ManagedTestContainer<FakeStartable> resource = ManagedTestContainer.of(
        "restart",
        () -> new FakeStartable(creations.incrementAndGet(), false)
    );

    FakeStartable first = resource.getOrStart();
    resource.stop();
    resource.stop();
    FakeStartable second = resource.getOrStart();

    assertEquals(1, first.stops.get());
    assertNotSame(first, second);
    assertEquals(2, creations.get());
  }

  private static final class FakeStartable implements Startable {
    private final int id;
    private final boolean failOnStart;
    private final AtomicInteger starts = new AtomicInteger();
    private final AtomicInteger stops = new AtomicInteger();

    private FakeStartable(int id, boolean failOnStart) {
      this.id = id;
      this.failOnStart = failOnStart;
    }

    @Override
    public void start() {
      starts.incrementAndGet();
      if (failOnStart) {
        throw new IllegalStateException("boom");
      }
    }

    @Override
    public void stop() {
      stops.incrementAndGet();
    }
  }
}
