package io.github.ygrip.testara.engine.executor;

import lombok.extern.log4j.Log4j2;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * A bounded virtual thread executor that limits concurrency using a semaphore.
 * <p>
 * Features:
 * <ul>
 *   <li>Uses virtual threads for lightweight concurrency</li>
 *   <li>Limits concurrent execution via semaphore</li>
 *   <li>Falls back to inline execution when no permits available (prevents deadlock)</li>
 *   <li>Graceful shutdown handling - doesn't throw during shutdown</li>
 * </ul>
 */
@Log4j2
public class BoundedVirtualExecutor extends AbstractExecutorService {
  private final ExecutorService backing;
  private final Semaphore permits;
  private volatile boolean shutdown = false;

  public BoundedVirtualExecutor(String prefix, int maxParallelism) {
    // Use the configured parallelism directly - respect user's explicit setting
    // If maxParallelism is 4, we should have exactly 4 concurrent tasks max
    int effectiveParallelism = Math.max(1, maxParallelism);
    this.permits = new Semaphore(effectiveParallelism);
    this.backing = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name(prefix + "-", 0).factory());
    log.debug("BoundedVirtualExecutor created with parallelism={}, prefix={}", effectiveParallelism, prefix);
  }

  @Override
  public void execute(Runnable command) {
    Objects.requireNonNull(command);

    // Graceful shutdown handling: skip execution if shutdown
    if (shutdown) {
      log.debug("Executor is shutdown, skipping task execution gracefully");
      // Don't throw - just return silently for graceful shutdown
      return;
    }

    // try fast path: use a permit and run on a dedicated virtual thread
    if (permits.tryAcquire()) {
      try {
        backing.execute(() -> {
          try {
            // Check for shutdown at execution start
            if (shutdown) {
              return;
            }
            command.run();
          } catch (Throwable t) {
            // Check if this is a shutdown-related exception
            if (isShutdownException(t)) {
              log.debug("Task cancelled due to shutdown: {}", t.getMessage());
            } else {
              throw t;
            }
          } finally {
            permits.release();
          }
        });
      } catch (RejectedExecutionException rex) {
        // executor rejected — release permit and fallback to inline
        permits.release();

        // Only run inline if not shutting down
        if (!shutdown) {
          try {
            command.run();
          } catch (Throwable t) {
            if (!isShutdownException(t)) {
              throw t;
            }
            log.debug("Inline task cancelled due to shutdown: {}", t.getMessage());
          }
        }
      }
    } else {
      // NO permit available — run inline in the caller thread,
      // preventing deadlock with hierarchical submissions.
      // Only run if not shutting down
      if (!shutdown) {
        try {
          command.run();
        } catch (Throwable t) {
          if (!isShutdownException(t)) {
            throw t;
          }
          log.debug("Inline task cancelled due to shutdown: {}", t.getMessage());
        }
      }
    }
  }

  @Override
  public void shutdown() {
    shutdown = true;
    backing.shutdown();
    log.debug("BoundedVirtualExecutor shutdown initiated");
  }

  @Override
  public List<Runnable> shutdownNow() {
    shutdown = true;
    List<Runnable> pending = backing.shutdownNow();
    log.debug("BoundedVirtualExecutor shutdown now, {} pending tasks", pending.size());
    return pending;
  }

  @Override
  public boolean isShutdown() {
    return shutdown;
  }

  @Override
  public boolean isTerminated() {
    return backing.isTerminated();
  }

  @Override
  public boolean awaitTermination(long t, TimeUnit u) throws InterruptedException {
    return backing.awaitTermination(t, u);
  }

  /**
   * Check if an exception is related to shutdown/interruption.
   *
   * @param t The throwable to check
   * @return true if this is a shutdown-related exception
   */
  private boolean isShutdownException(Throwable t) {
    if (t == null) {
      return false;
    }

    if (t instanceof RejectedExecutionException ||
        t instanceof InterruptedException) {
      return true;
    }

    String className = t.getClass().getName();
    if (className.contains("ClosedByInterruptException") ||
        className.contains("ClosedChannelException") ||
        className.contains("CancellationException")) {
      return true;
    }

    String message = t.getMessage();
    if (message != null) {
      String lowerMessage = message.toLowerCase();
      if (lowerMessage.contains("shutdown") ||
          lowerMessage.contains("interrupted") ||
          lowerMessage.contains("closed")) {
        return true;
      }
    }

    Throwable cause = t.getCause();
    if (cause != null && cause != t) {
      return isShutdownException(cause);
    }

    return false;
  }
}
