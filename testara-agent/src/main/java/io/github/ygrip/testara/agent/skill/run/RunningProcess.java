package io.github.ygrip.testara.agent.skill.run;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * A build process started by {@link ProcessRunner#start}: wait for it with a timeout, or cancel it
 * from another thread. Timeout, interrupt and cancellation all terminate the whole process tree.
 */
public final class RunningProcess {

  private static final Logger LOG = Logger.getLogger(RunningProcess.class.getName());
  private static final long KILL_WAIT_SECONDS = 5;

  private final Process process;
  private final long startMillis;
  private volatile boolean cancelled;

  RunningProcess(Process process, long startMillis) {
    this.process = process;
    this.startMillis = startMillis;
  }

  /**
   * Wait for the process to exit. The process tree is killed when the timeout elapses or the calling
   * thread is interrupted; an interrupt is rethrown after the tree has been terminated.
   */
  public ProcessRunner.Outcome await(Duration timeout) throws InterruptedException {
    boolean finished = false;
    try {
      finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
    } finally {
      if (!finished) {
        destroyTree();
      }
    }
    long duration = System.currentTimeMillis() - startMillis;
    if (cancelled) {
      return new ProcessRunner.Outcome(-1, false, duration, true);
    }
    if (!finished) {
      return new ProcessRunner.Outcome(-1, true, duration);
    }
    return new ProcessRunner.Outcome(process.exitValue(), false, duration);
  }

  /** Kill the process tree; a pending {@link #await} then returns a cancelled outcome. */
  public void cancel() {
    cancelled = true;
    destroyTree();
  }

  public boolean isAlive() {
    return process.isAlive();
  }

  /** Kill descendants first (they are re-parented, and invisible, once the root dies), then the root. */
  private void destroyTree() {
    List<ProcessHandle> descendants = process.descendants().toList();
    descendants.forEach(ProcessHandle::destroyForcibly);
    process.destroyForcibly();
    try {
      if (!process.waitFor(KILL_WAIT_SECONDS, TimeUnit.SECONDS)) {
        LOG.warning("Build process " + process.pid() + " did not exit after being killed");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      LOG.warning("Interrupted while waiting for killed build process " + process.pid() + " to exit");
    }
  }
}
