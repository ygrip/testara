package io.github.ygrip.testara.core.concurrency;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class ThreadExecutor {
  public static <T> T run(ThreadFactory threadFactory, Callable<T> task) {
    try (ExecutorService executorService = Executors.newThreadPerTaskExecutor(threadFactory)) {
      return executorService.submit(task).get();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
