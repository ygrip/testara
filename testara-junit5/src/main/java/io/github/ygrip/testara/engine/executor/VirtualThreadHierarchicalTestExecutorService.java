package io.github.ygrip.testara.engine.executor;

import io.github.ygrip.testara.engine.support.ExceptionHandler;
import lombok.extern.log4j.Log4j2;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.support.hierarchical.HierarchicalTestExecutorService;
import org.junit.platform.engine.support.hierarchical.Node;
import org.junit.platform.engine.support.hierarchical.ResourceLock;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Virtual-thread based hierarchical executor for JUnit 5 with full resource locking support.
 * <p><b>Features:</b></p>
 * <ul>
 *   <li>✅ <b>Unbounded Virtual Threads</b>: Avoids hierarchical deadlock (no semaphore limiting)</li>
 *   <li>✅ <b>ResourceLock Support</b>: Properly acquires/releases JUnit's resource locks</li>
 *   <li>✅ <b>ExecutionMode Support</b>: Respects CONCURRENT vs SAME_THREAD execution modes</li>
 *   <li>✅ <b>Same-Thread Optimization</b>: Uses ThreadLocal to avoid unnecessary thread creation</li>
 *   <li>✅ <b>Per-task Timeout Support</b>: Scheduled cancellation + interruption</li>
 *   <li>✅ <b>Graceful Shutdown</b>: Proper cleanup with shutdown hooks</li>
 *   <li>✅ <b>No Thread Leaks</b>: All threads properly terminated on close or interrupt</li>
 * </ul>
 * <p><b>Important Notes:</b></p>
 * <ul>
 *   <li>Virtual threads are designed to be lightweight (~1KB memory per thread)</li>
 *   <li>Bounded semaphores cause hierarchical deadlock - DO NOT USE with hierarchical tests</li>
 *   <li>Uses ReentrantLock instead of synchronized to avoid virtual thread pinning</li>
 *   <li>Shutdown hook ensures cleanup even on JVM termination</li>
 * </ul>
 *
 * @see HierarchicalTestExecutorService
 * @see Node.ExecutionMode
 */
@Log4j2
public class VirtualThreadHierarchicalTestExecutorService implements HierarchicalTestExecutorService {

  private final BoundedVirtualExecutor virtualExecutor;                       // unbounded virtual threads
  private final ExecutorService unboundedVirtualThread;                       // unbounded virtual threads
  private final ScheduledExecutorService timeoutScheduler;            // platform thread for timeout scheduling
  private final AtomicBoolean closed = new AtomicBoolean(false);
  private final ThreadLocal<Boolean> isExecutorThread = ThreadLocal.withInitial(() -> Boolean.FALSE);
  private final Thread shutdownHook;
  private final String threadNamePrefix;

  /**
   * Create virtual thread executor service (UNBOUNDED - recommended for avoiding hierarchical deadlock)
   *
   * @param threadNamePrefix Thread name prefix for virtual threads
   */
  public VirtualThreadHierarchicalTestExecutorService(String threadNamePrefix) {
    this(0, threadNamePrefix);
  }

  /**
   * Create virtual thread executor service with optional concurrency limiting.
   * <p><b>⚠️ WARNING:</b> Using maxConcurrency > 0 can cause hierarchical deadlock!
   * Only use if you understand the implications. For hierarchical test execution,
   * use the single-parameter constructor (unbounded).</p>
   *
   * @param maxConcurrency   Maximum concurrent tasks (0 = unbounded, recommended for hierarchical tests)
   * @param threadNamePrefix Thread name prefix for virtual threads
   */
  public VirtualThreadHierarchicalTestExecutorService(int maxConcurrency, String threadNamePrefix) {
    if (maxConcurrency < 0)
      throw new IllegalArgumentException("maxConcurrency must be >= 0 (0 = unbounded)");

    this.threadNamePrefix = threadNamePrefix;

    // Create unbounded virtual thread executor
    this.virtualExecutor = new BoundedVirtualExecutor(threadNamePrefix, maxConcurrency);
    this.unboundedVirtualThread =
        Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name(threadNamePrefix + "-unbounded-", 0).factory());

    // Single-threaded scheduler on platform thread for timeout handling
    // Daemon thread so it doesn't block test completion reporting to IDE
    this.timeoutScheduler = Executors.newSingleThreadScheduledExecutor(r -> Thread.ofPlatform()
        .name(threadNamePrefix + "-timeout-scheduler")
        .daemon(true)  // Daemon thread for faster shutdown and proper IDE reporting
        .factory()
        .newThread(r));

    // Register shutdown hook for graceful cleanup on JVM termination
    this.shutdownHook = new Thread(this::forceShutdown, threadNamePrefix + "-shutdown-hook");
    Runtime.getRuntime().addShutdownHook(shutdownHook);
    log.debug("VirtualThreadHierarchicalTestExecutorService initialized with BOUNDED concurrency (max={}). "
        + "This may cause hierarchical deadlock! Consider using unbounded mode.", maxConcurrency);
  }

  /**
   * Submit a TestTask for execution. Handles same-thread optimization, resource locking,
   * and execution mode (CONCURRENT vs SAME_THREAD).
   *
   * @param task The test task to execute
   * @return Future that completes when task finishes
   */
  @Override
  public Future<Void> submit(TestTask task) {
    return submit(task, null);
  }

  /**
   * Submit a TestTask with optional timeout.
   * <p>This method handles:</p>
   * <ul>
   *   <li>Same-thread optimization: if already on executor thread, executes immediately</li>
   *   <li>Resource locking: acquires JUnit resource locks before execution</li>
   *   <li>Execution mode: respects CONCURRENT vs SAME_THREAD</li>
   *   <li>Timeout: optional per-task timeout with cancellation</li>
   *   <li>Graceful shutdown: returns cancelled future if executor is closed</li>
   * </ul>
   *
   * @param task    The test task to execute
   * @param timeout Optional timeout (null = no timeout)
   * @return Future that completes when task finishes or times out
   */
  public Future<Void> submit(TestTask task, Duration timeout) {
    // Graceful shutdown handling: return a cancelled future instead of throwing
    if (closed.get()) {
      log.debug("Executor is closed, skipping task submission gracefully");
      CompletableFuture<Void> cancelled = new CompletableFuture<>();
      cancelled.cancel(false);
      return cancelled;
    }

    // Same-thread optimization: if we're already on an executor thread, execute immediately
    // This mimics ForkJoinPool's work-stealing behavior and prevents unnecessary thread creation
    if (isExecutorThread.get()) {
      try {
        // Check again for shutdown before executing
        if (closed.get()) {
          CompletableFuture<Void> cancelled = new CompletableFuture<>();
          cancelled.cancel(false);
          return cancelled;
        }
        executeTaskWithResourceLock(task);
        return CompletableFuture.completedFuture(null);
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
        log.debug("Task execution interrupted");
        CompletableFuture<Void> interrupted = new CompletableFuture<>();
        interrupted.cancel(true);
        return interrupted;
      } catch (Throwable t) {
        // Check if this is a shutdown-related exception
        if (isShutdownException(t)) {
          log.debug("Task execution cancelled due to shutdown: {}", t.getMessage());
          CompletableFuture<Void> cancelled = new CompletableFuture<>();
          cancelled.cancel(false);
          return cancelled;
        }
        CompletableFuture<Void> failed = new CompletableFuture<>();
        failed.completeExceptionally(ExceptionHandler.trimStackTrace(t));
        return failed;
      }
    }

    CompletableFuture<Void> result = new CompletableFuture<>();

    // Wrapper runnable that executes the task with resource locking and concurrency limiting
    Runnable work = () -> {
      try {
        // Check for shutdown at the start of task execution
        if (closed.get()) {
          result.cancel(false);
          return;
        }

        try {
          // Mark this thread as an executor thread for same-thread optimization
          isExecutorThread.set(true);

          // Execute the task with proper resource lock handling
          executeTaskWithResourceLock(task);

          result.complete(null); // Success
        } finally {
          isExecutorThread.set(false);
        }
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
        if (!result.isDone()) {
          result.cancel(true);
        }
      } catch (Throwable t) {
        // Check if this is a shutdown-related exception
        if (isShutdownException(t)) {
          log.debug("Task cancelled due to shutdown: {}", t.getMessage());
          if (!result.isDone()) {
            result.cancel(false);
          }
        } else if (!result.isDone()) {
          result.completeExceptionally(ExceptionHandler.trimStackTrace(t));
        }
      }
    };

    // Submit to virtual thread executor with graceful error handling
    CompletableFuture<Void> taskFuture;
    try {
      taskFuture = CompletableFuture.runAsync(work, getExecutor(task));
    } catch (RejectedExecutionException e) {
      log.debug("Task submission rejected (executor shutting down)");
      result.cancel(false);
      return result;
    }

    // Schedule timeout cancellation if timeout is specified
    ScheduledFuture<?> timeoutHandle = null;
    if (timeout != null && !timeout.isZero() && !timeout.isNegative() && !closed.get()) {
      try {
        timeoutHandle = timeoutScheduler.schedule(() -> {
          taskFuture.cancel(true); // Interrupt the running thread
          if (!result.isDone()) {
            result.completeExceptionally(new TimeoutException("TestTask timed out after " + timeout));
          }
        }, timeout.toMillis(), TimeUnit.MILLISECONDS);
      } catch (RejectedExecutionException e) {
        // Scheduler is shutting down, ignore timeout scheduling
        log.debug("Timeout scheduling rejected (scheduler shutting down)");
      }
    }

    // Propagate completion/cancellation from taskFuture to result
    final ScheduledFuture<?> finalTimeoutHandle = timeoutHandle;
    taskFuture.whenComplete((v, ex) -> {
      if (finalTimeoutHandle != null) {
        finalTimeoutHandle.cancel(false);
      }
      if (ex != null && !result.isDone()) {
        if (ex instanceof CancellationException || isShutdownException(ex)) {
          result.cancel(false);
        } else {
          Throwable cause = ex instanceof CompletionException ? ex.getCause() : ex;
          if (isShutdownException(cause)) {
            result.cancel(false);
          } else {
            result.completeExceptionally(ExceptionHandler.trimStackTrace(cause));
          }
        }
      } else if (!result.isDone()) {
        result.complete(null);
      }
    });

    return result;
  }

  /**
   * Execute a task with proper JUnit ResourceLock acquisition and release.
   * This ensures that tests with @ResourceLock annotations don't run concurrently
   * when they share the same resource.
   * <p>The JUnit Platform's ResourceLock mechanism ensures that tests annotated with
   * {@code @ResourceLock} will not run concurrently if they share the same resource key.</p>
   *
   * @param task The test task to execute
   * @throws Exception if task execution fails
   */
  private void executeTaskWithResourceLock(TestTask task) throws Exception {
    // Get the resource lock from the task (JUnit Platform API)
    // The ResourceLock represents all resources this task needs exclusive access to
    ResourceLock resourceLock = task.getResourceLock();

    // Acquire the lock (may block if another task holds the resource)
    // The acquire() method returns a ResourceLock that should be released via close()
    ResourceLock acquiredLock = null;

    try {
      acquiredLock = resourceLock.acquire();

      // Execute the test task with the lock held
      task.execute();

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Task execution interrupted while waiting for resource lock", ExceptionHandler.trimStackTrace(e));
    } finally {
      // Always release the lock, even if task fails
      if (acquiredLock != null) {
        try {
          acquiredLock.release();
        } catch (Exception e) {
          log.warn("Failed to release resource lock for task: {}", task, ExceptionHandler.trimStackTrace(e));
        }
      }
    }
  }

  /**
   * Execute a collection of test tasks, respecting their execution modes.
   * <p>This method separates tasks into:</p>
   * <ul>
   *   <li><b>CONCURRENT tasks</b>: Executed in parallel on virtual threads</li>
   *   <li><b>SAME_THREAD tasks</b>: Executed immediately on current thread (sequential)</li>
   * </ul>
   * <p>This mimics ForkJoinPool's behavior and ensures proper test hierarchy execution.</p>
   * <p>Handles graceful shutdown by skipping task execution when executor is closed.</p>
   *
   * @param testTasks Collection of test tasks to execute
   */
  @Override
  public void invokeAll(List<? extends TestTask> testTasks) {
    // Graceful shutdown handling: skip execution if closed
    if (closed.get()) {
      log.debug("Executor is closed, skipping invokeAll gracefully");
      return;
    }

    if (testTasks == null || testTasks.isEmpty()) {
      return;
    }

    // Separate tasks by execution mode
    List<CompletableFuture<Void>> concurrentFutures = new ArrayList<>();
    Deque<TestTask> sameThreadTasks = populateTestTasks(testTasks, Node.ExecutionMode.SAME_THREAD);
    Deque<TestTask> concurrentTasksInReverseOrder = populateTestTasks(testTasks, Node.ExecutionMode.CONCURRENT);

    for (TestTask task : concurrentTasksInReverseOrder) {
      // Check for shutdown before submitting each task
      if (closed.get()) {
        log.debug("Executor closing, cancelling remaining concurrent task submissions");
        break;
      }

      try {
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
          // Early exit if shutdown during execution
          if (closed.get()) {
            return;
          }

          try {
            try {
              // Mark this thread as an executor thread for same-thread optimization
              isExecutorThread.set(true);

              // Execute the task with proper resource lock handling
              executeTaskWithResourceLock(task);
            } finally {
              isExecutorThread.set(false);
            }
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            // Don't throw - just exit gracefully
            log.debug("Task interrupted during execution");
          } catch (Throwable t) {
            // Check if this is a shutdown-related exception
            if (isShutdownException(t)) {
              log.debug("Task cancelled due to shutdown: {}", t.getMessage());
            } else {
              throw new CompletionException(ExceptionHandler.trimStackTrace(t));
            }
          }
        }, getExecutor(task));
        concurrentFutures.add(future);
      } catch (RejectedExecutionException e) {
        log.debug("Task submission rejected (executor shutting down)");
        break;
      }
    }

    // Execute same-thread tasks immediately (sequential)
    for (TestTask task : sameThreadTasks) {
      // Check for shutdown before each task
      if (closed.get()) {
        log.debug("Executor closing, skipping remaining same-thread tasks");
        cancelAllFutures(new ArrayList<>(concurrentFutures));
        return;
      }

      try {
        try {
          // Mark this thread as an executor thread for same-thread optimization
          isExecutorThread.set(true);

          // Execute the task with proper resource lock handling
          executeTaskWithResourceLock(task);
        } finally {
          isExecutorThread.set(false);
        }
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
        log.debug("Same-thread task interrupted");
        cancelAllFutures(new ArrayList<>(concurrentFutures));
        return;
      } catch (Exception e) {
        // Check if this is a shutdown-related exception
        if (isShutdownException(e)) {
          log.debug("Same-thread task cancelled due to shutdown");
          cancelAllFutures(new ArrayList<>(concurrentFutures));
          return;
        }
        // Cancel all concurrent tasks if a same-thread task fails
        cancelAllFutures(new ArrayList<>(concurrentFutures));
        throw new RuntimeException("Same-thread task execution failed", ExceptionHandler.trimStackTrace(e));
      }
    }

    // Wait for all concurrent tasks to complete
    for (int i = 0; i < concurrentFutures.size(); i++) {
      // Check for shutdown while waiting
      if (closed.get()) {
        log.debug("Executor closing, cancelling remaining concurrent futures");
        for (int j = i; j < concurrentFutures.size(); j++) {
          concurrentFutures.get(j).cancel(true);
        }
        return;
      }

      CompletableFuture<Void> future = concurrentFutures.get(i);
      try {
        future.join(); // Join blocks until complete
      } catch (CancellationException e) {
        // Task was cancelled (possibly due to shutdown), continue gracefully
        log.debug("Concurrent task was cancelled");
      } catch (CompletionException e) {
        // Check if this is a shutdown-related exception
        Throwable cause = e.getCause() != null ? e.getCause() : e;
        if (isShutdownException(cause)) {
          log.debug("Concurrent task cancelled due to shutdown");
          // Cancel remaining tasks and exit gracefully
          for (int j = i + 1; j < concurrentFutures.size(); j++) {
            concurrentFutures.get(j).cancel(true);
          }
          return;
        }

        // If one task fails, cancel all remaining tasks
        for (CompletableFuture<Void> f : concurrentFutures) {
          if (!f.isDone()) {
            f.cancel(true);
          }
        }

        // Unwrap and rethrow the cause
        cause = ExceptionHandler.trimStackTrace(cause);
        if (cause instanceof RuntimeException) {
          throw (RuntimeException) cause;
        }
        throw new RuntimeException("Concurrent task execution failed", cause);
      }
    }
  }

  private boolean isTestTask(Object task) {
    if (task == null)
      return false;
    try {
      Class<?> nodeClass = task.getClass();

      // Try common method names used in various JUnit versions:
      Object testDescriptor = null;
      try {
        Field field = nodeClass.getDeclaredField("testDescriptor");
        field.setAccessible(true);
        testDescriptor = field.get(task);
      } catch (Exception ignored) {

      }

      if (testDescriptor == null)
        return false;

      if (testDescriptor instanceof TestDescriptor td) {
        return td.isTest();
      }

      // If testDescriptor isn't directly TestDescriptor (very unlikely),
      // try reflective call to getType()
      Method getType = testDescriptor.getClass().getMethod("getType");
      Object typeEnum = getType.invoke(testDescriptor);
      if (typeEnum != null) {
        // Compare by name to avoid classloader mismatch
        return typeEnum.toString().equals("TEST");
      }
    } catch (ReflectiveOperationException ignored) {
    }
    return false;
  }

  private Deque<TestTask> populateTestTasks(List<? extends TestTask> testTasks, Node.ExecutionMode executionMode) {
    Deque<TestTask> result = new LinkedList<>();
    for (TestTask task : testTasks) {
      if (task.getExecutionMode() == executionMode) {
        result.add(task);
      }
    }
    return result;
  }

  /**
   * Cancel all futures in the list.
   *
   * @param futures List of futures to cancel
   */
  private void cancelAllFutures(List<Future<Void>> futures) {
    for (Future<Void> f : futures) {
      if (!f.isDone()) {
        f.cancel(true); // Interrupt running tasks
      }
    }
  }

  private ExecutorService getExecutor(TestTask task) {
//    boolean isTest = isTestTask(task);
//    if (isTest) {
//      return virtualExecutor;
//    } else {
//      return unboundedVirtualThread;
//    }
    return virtualExecutor;
  }

  /**
   * Gracefully close the executor service.
   * <p>This method:</p>
   * <ul>
   *   <li>Shuts down the virtual thread executor</li>
   *   <li>Shuts down the timeout scheduler</li>
   *   <li>Waits for graceful termination (up to 30 seconds for executor, 5 for scheduler)</li>
   *   <li>Forces shutdown if graceful termination fails</li>
   *   <li>Removes the shutdown hook</li>
   * </ul>
   */
  @Override
  public void close() {
    if (closed.compareAndSet(false, true)) {
      log.trace("Closing VirtualThreadHierarchicalTestExecutorService: {}", threadNamePrefix);

      try {
        // Remove shutdown hook (we're closing gracefully now)
        try {
          Runtime.getRuntime().removeShutdownHook(shutdownHook);
        } catch (IllegalStateException e) {
          // Already shutting down, that's fine
        }

        // Shutdown virtual executor
        virtualExecutor.shutdown();
        unboundedVirtualThread.shutdown();
        log.debug("Virtual executor shutdown initiated");

        // Reduce wait time to 5 seconds for faster IDE reporting
        // Virtual threads should terminate almost immediately
        boolean executorTerminated = virtualExecutor.awaitTermination(5, TimeUnit.SECONDS);
        if (!executorTerminated) {
          log.warn("Virtual executor did not terminate gracefully within 5s, forcing shutdown");
          virtualExecutor.shutdownNow();
          virtualExecutor.awaitTermination(2, TimeUnit.SECONDS);
        }

        // Reduce wait time to 5 seconds for faster IDE reporting
        // Virtual threads should terminate almost immediately
        boolean unboundedExecutorTerminated = unboundedVirtualThread.awaitTermination(5, TimeUnit.SECONDS);
        if (!executorTerminated) {
          log.warn("Virtual executor did not terminate gracefully within 5s, forcing shutdown");
          unboundedVirtualThread.shutdownNow();
          unboundedVirtualThread.awaitTermination(2, TimeUnit.SECONDS);
        }

        // Shutdown timeout scheduler
        timeoutScheduler.shutdown();
        log.debug("Timeout scheduler shutdown initiated");

        // Timeout scheduler is daemon, so minimal wait
        boolean schedulerTerminated = timeoutScheduler.awaitTermination(1, TimeUnit.SECONDS);
        if (!schedulerTerminated) {
          log.debug("Timeout scheduler (daemon) did not terminate within 1s, forcing shutdown");
          timeoutScheduler.shutdownNow();
          // No additional wait for daemon thread
        }

        // Clean up ThreadLocal
        isExecutorThread.remove();

        log.trace("VirtualThreadHierarchicalTestExecutorService closed successfully: {}", threadNamePrefix);

      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        log.error("Interrupted while closing executor service", e);

        // Force shutdown on interruption
        virtualExecutor.shutdownNow();
        unboundedVirtualThread.shutdownNow();
        timeoutScheduler.shutdownNow();
      }
    }
  }

  /**
   * Force shutdown of the executor service.
   * This is called by the shutdown hook on JVM termination.
   */
  private void forceShutdown() {
    if (closed.compareAndSet(false, true)) {
      log.warn("Force shutting down VirtualThreadHierarchicalTestExecutorService due to JVM termination");

      try {
        // Forcefully terminate all executors
        List<Runnable> pendingVirtualTasks = virtualExecutor.shutdownNow();
        List<Runnable> pendingUnboundedVirtualTasks = unboundedVirtualThread.shutdownNow();
        List<Runnable> pendingScheduledTasks = timeoutScheduler.shutdownNow();

        log.trace("Force shutdown complete. Pending virtual tasks: {}, pending scheduled tasks: {}",
            pendingVirtualTasks.size(),
            pendingScheduledTasks.size());

        // Try to wait a bit for termination
        virtualExecutor.awaitTermination(5, TimeUnit.SECONDS);
        unboundedVirtualThread.awaitTermination(5, TimeUnit.SECONDS);
        timeoutScheduler.awaitTermination(2, TimeUnit.SECONDS);

      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        log.error("Interrupted during force shutdown", e);
      } catch (Exception e) {
        log.error("Error during force shutdown", e);
      }
    }
  }

  /**
   * Ensure the executor is still open.
   * This method is now lenient and just logs a warning instead of throwing.
   *
   * @return true if open, false if closed
   */
  private boolean ensureOpen() {
    if (closed.get()) {
      log.debug("Executor is closed");
      return false;
    }
    return true;
  }

  /**
   * Check if the executor is closed.
   *
   * @return true if closed, false otherwise
   */
  public boolean isClosed() {
    return closed.get();
  }

  /**
   * Check if the current thread is an executor thread.
   *
   * @return true if current thread is managed by this executor
   */
  public boolean isCurrentThreadExecutorThread() {
    return isExecutorThread.get();
  }

  /**
   * Check if an exception is related to shutdown/interruption.
   * These exceptions should be handled gracefully during test stop.
   *
   * @param t The throwable to check
   * @return true if this is a shutdown-related exception
   */
  private boolean isShutdownException(Throwable t) {
    if (t == null) {
      return false;
    }

    // Direct shutdown-related exceptions
    if (t instanceof RejectedExecutionException) {
      return true;
    }
    if (t instanceof InterruptedException) {
      return true;
    }
    if (t instanceof CancellationException) {
      return true;
    }

    // Check for NIO channel interruption (happens during classpath scanning)
    String className = t.getClass().getName();
    if (className.contains("ClosedByInterruptException") ||
        className.contains("ClosedChannelException") ||
        className.contains("AsynchronousCloseException")) {
      return true;
    }

    // Check message for common shutdown indicators
    String message = t.getMessage();
    if (message != null) {
      String lowerMessage = message.toLowerCase();
      if (lowerMessage.contains("executor is closed") ||
          lowerMessage.contains("shutdown") ||
          lowerMessage.contains("interrupted") ||
          lowerMessage.contains("rejected")) {
        return true;
      }
    }

    // Check cause recursively
    Throwable cause = t.getCause();
    if (cause != null && cause != t) {
      return isShutdownException(cause);
    }

    return false;
  }
}
