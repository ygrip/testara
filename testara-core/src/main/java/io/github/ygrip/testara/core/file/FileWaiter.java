package io.github.ygrip.testara.core.file;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FileWaiter {

  /**
   * Waits for all files in the given list to exist.
   * @param paths List of file paths to check
   * @param timeout Maximum wait time for each file
   * @param checkInterval Interval between checks
   * @return true if all files exist before timeout, false otherwise
   */
  public static boolean waitUntilAllExist(
      List<Path> paths,
      Duration timeout,
      Duration checkInterval
  ) {
    // virtual thread per file task
    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {

      List<CompletableFuture<Boolean>> futures = paths.stream()
          .map(path -> CompletableFuture.supplyAsync(() ->
              waitUntilExists(path, timeout, checkInterval), executor))
          .toList();

      // Wait for all tasks to complete
      CompletableFuture<Void> all = CompletableFuture.allOf(
          futures.toArray(CompletableFuture[]::new));

      try {
        all.join(); // blocks current virtual thread, safe
      } catch (CompletionException ex) {
        return false;
      }

      // Return true only if all files exist
      return futures.stream().allMatch(f -> f.getNow(false));

    } catch (Exception e) {
      return false;
    }
  }

  /**
   * Wait until the given file exists, checking repeatedly.
   */
  private static boolean waitUntilExists(Path path, Duration timeout, Duration interval) {
    long start = System.nanoTime();
    long timeoutNs = timeout.toNanos();
    while (System.nanoTime() - start < timeoutNs) {
      if (Files.exists(path)) {
        return true;
      }
      try {
        Thread.sleep(interval.toMillis()); // virtual-thread-safe blocking
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return false;
      }
    }
    return Files.exists(path); // final check
  }
}
