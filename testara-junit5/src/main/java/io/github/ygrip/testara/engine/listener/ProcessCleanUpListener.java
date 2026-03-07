package io.github.ygrip.testara.engine.listener;

import lombok.extern.log4j.Log4j2;
import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

@Log4j2
public class ProcessCleanUpListener implements LauncherSessionListener {
  @Override
  public void launcherSessionOpened(LauncherSession session) {
    log.debug("Test session started");
  }

  @Override
  public void launcherSessionClosed(LauncherSession session) {
    log.debug("Test session finished — cleaning up resources...");

    // Example cleanup logic
    shutdownForkJoinPoolSafely();
    detectLeakedThreads();
  }

  private void shutdownForkJoinPoolSafely() {
    ForkJoinPool.commonPool().shutdown();
    try {
      if (!ForkJoinPool.commonPool().awaitTermination(1, TimeUnit.SECONDS)) {
        ForkJoinPool.commonPool().shutdownNow();
        log.warn("Forced shutdown of common ForkJoinPool");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private void detectLeakedThreads() {
    Thread.getAllStackTraces().keySet().stream()
        .filter(t -> !t.isDaemon())
        .filter(t -> t.getThreadGroup() != null && !"system".equalsIgnoreCase(t.getThreadGroup().getName()))
        .filter(Thread::isAlive)
        .forEach(t -> log.warn("⚠️ Detected live non-daemon thread: {}", t.getName()));
  }
}
