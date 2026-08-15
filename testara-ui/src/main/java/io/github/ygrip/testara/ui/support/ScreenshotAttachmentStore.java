package io.github.ygrip.testara.ui.support;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import io.github.ygrip.testara.ui.model.CapturedScreenshot;
import io.github.ygrip.testara.ui.model.ScreenshotQuality;
import lombok.extern.log4j.Log4j2;

/**
 * Stores screenshot attachments outside Cucumber's JSON payload while image
 * resizing and encoding run on a small bounded worker pool.
 *
 * <p>Browser capture itself remains synchronous so the screenshot still
 * represents the exact step state. Only CPU/file processing is deferred.</p>
 */
@Log4j2
public final class ScreenshotAttachmentStore {
  public static final Path DEFAULT_DIRECTORY = Path.of("target", "testara-screenshots");
  private static final int MAX_QUEUED_SCREENSHOTS = 64;
  private static final int WORKER_COUNT = Math.max(1, Math.min(2, Runtime.getRuntime().availableProcessors()));
  private static final ScreenshotAttachmentStore INSTANCE = new ScreenshotAttachmentStore(
    DEFAULT_DIRECTORY,
    defaultExecutor()
  );

  private final Path directory;
  private final Executor executor;
  private final ConcurrentHashMap<String, Queue<CompletableFuture<Void>>> pending = new ConcurrentHashMap<>();

  public static ScreenshotAttachmentStore instance() {
    return INSTANCE;
  }

  ScreenshotAttachmentStore(Path directory, Executor executor) {
    this.directory = Objects.requireNonNull(directory, "directory").toAbsolutePath().normalize();
    this.executor = Objects.requireNonNull(executor, "executor");
  }

  /**
   * Queue an image for optimization and file storage, returning a lightweight
   * identifier that can safely be attached to the active Cucumber step.
   */
  public Reference store(
    String groupId,
    CapturedScreenshot screenshot,
    ScreenshotQuality quality
  ) {
    if (screenshot == null || screenshot.bytes() == null || screenshot.bytes().length == 0) {
      throw new IllegalArgumentException("Screenshot bytes must not be empty");
    }

    String key = Objects.toString(groupId, "default");
    String id = UUID.randomUUID().toString();
    Path destination = directory.resolve(id + ".image");
    CompletableFuture<Void> task = CompletableFuture.runAsync(
      () -> writeOptimized(destination, screenshot, quality),
      executor
    );
    pending.computeIfAbsent(key, ignored -> new ConcurrentLinkedQueue<>()).add(task);
    return new Reference(id);
  }

  /** Wait until all screenshot processing queued for a scenario has drained. */
  public void await(String groupId) {
    String key = Objects.toString(groupId, "default");
    Queue<CompletableFuture<Void>> tasks = pending.remove(key);
    if (tasks == null || tasks.isEmpty()) {
      return;
    }
    CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new)).join();
  }

  private void writeOptimized(
    Path destination,
    CapturedScreenshot screenshot,
    ScreenshotQuality quality
  ) {
    Path temporary = destination.resolveSibling(destination.getFileName() + ".tmp");
    try {
      Screenshots.OptimizedScreenshot optimized = Screenshots.optimize(
        screenshot.bytes(),
        screenshot.mimeType(),
        quality
      );
      Files.createDirectories(directory);
      Files.write(temporary, optimized.bytes());
      Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
    } catch (Exception e) {
      log.warn("Failed to persist screenshot attachment {}: {}", destination.getFileName(), e.getMessage());
      try {
        Files.deleteIfExists(temporary);
      } catch (Exception ignored) {
        // Best effort cleanup only.
      }
    }
  }

  private static Executor defaultExecutor() {
    ThreadFactory threadFactory = runnable -> {
      Thread thread = new Thread(runnable, "testara-screenshot-worker");
      thread.setDaemon(true);
      return thread;
    };
    return new ThreadPoolExecutor(
      WORKER_COUNT,
      WORKER_COUNT,
      30L,
      TimeUnit.SECONDS,
      new ArrayBlockingQueue<>(MAX_QUEUED_SCREENSHOTS),
      threadFactory,
      new ThreadPoolExecutor.CallerRunsPolicy()
    );
  }

  public record Reference(String id) {}
}
