package io.github.ygrip.testara.agent.mcp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.ygrip.testara.agent.skill.TestRunSkill;

/**
 * Test runs started by this MCP server: at most one active run per project root, the last
 * {@value #MAX_FINISHED} finished runs kept in memory, and a small metadata file per run under
 * {@code <projectRoot>/.testara-agent/runs/<runId>.json} written on start and on completion.
 */
final class RunRegistry {

  private static final Logger LOG = Logger.getLogger(RunRegistry.class.getName());
  static final int MAX_FINISHED = 20;
  static final String RUNS_DIR = ".testara-agent/runs";
  private static final long CANCEL_WAIT_SECONDS = 30;
  private static final Pattern RUN_ID = Pattern.compile("[A-Za-z0-9-]+");

  private final ObjectMapper mapper;
  private final Map<String, Run> runs = new LinkedHashMap<>();

  RunRegistry(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  /**
   * What is persisted per run. {@code finishedAt} and {@code exitCode} are {@code null} while it runs;
   * {@code exitCode} is the build's exit code ({@code -1} on timeout or cancellation).
   */
  record RunMetadata(String runId, String command, String logFile, String startedAt, String state,
      String finishedAt, Integer exitCode) {}

  /** A tracked run; {@link #done()} completes once its final state has been recorded. */
  static final class Run {

    private final Path projectRoot;
    private final TestRunSkill.RunHandle handle;
    private final CompletableFuture<Void> done = new CompletableFuture<>();
    private volatile Instant finishedAt;

    private Run(Path projectRoot, TestRunSkill.RunHandle handle) {
      this.projectRoot = projectRoot;
      this.handle = handle;
    }

    TestRunSkill.RunHandle handle() {
      return handle;
    }

    CompletableFuture<Void> done() {
      return done;
    }

    boolean active() {
      return !done.isDone();
    }

    /** Finish time once recorded, else {@code null}. */
    Instant finishedAt() {
      return finishedAt;
    }
  }

  /**
   * The starter's handle with its tracked {@code run} ({@code null} when no build was launched), or
   * only the id of the project's run that is still active.
   */
  record Started(TestRunSkill.RunHandle handle, Run run, String activeRunId) {}

  /**
   * Start a run for {@code projectRoot} unless one is already active there. The starter must return
   * quickly (it launches the build, it does not wait for it); only a {@link TestRunSkill.RunHandle#started()
   * started} handle is tracked.
   */
  synchronized Started start(Path projectRoot, Supplier<TestRunSkill.RunHandle> starter) {
    Path root = projectRoot.toAbsolutePath().normalize();
    for (Run run : runs.values()) {
      if (run.projectRoot.equals(root) && run.active()) {
        return new Started(null, null, run.handle.runId());
      }
    }
    TestRunSkill.RunHandle handle = starter.get();
    if (!handle.started()) {
      return new Started(handle, null, null);
    }
    Run run = new Run(root, handle);
    runs.put(handle.runId(), run);
    persist(run);
    handle.result().whenComplete((output, failure) -> finish(run, failure));
    return new Started(handle, run, null);
  }

  private void finish(Run run, Throwable failure) {
    if (failure != null) {
      LOG.warning("Test run " + run.handle.runId() + " failed: " + failure.getMessage());
    }
    run.finishedAt = Instant.now();
    persist(run);
    run.done.complete(null);
    evictFinished();
  }

  synchronized Run find(String runId) {
    return runs.get(runId);
  }

  /** The metadata recorded for a run this server no longer tracks, or {@code null} when there is none. */
  RunMetadata readMetadata(Path projectRoot, String runId) {
    if (!RUN_ID.matcher(runId).matches()) {
      return null;
    }
    Path file = metadataFile(projectRoot.toAbsolutePath().normalize(), runId);
    if (!Files.isRegularFile(file)) {
      return null;
    }
    try {
      return mapper.readValue(file.toFile(), RunMetadata.class);
    } catch (IOException e) {
      LOG.warning("Cannot read run metadata " + file + ": " + e.getMessage());
      return null;
    }
  }

  RunMetadata metadata(Run run) {
    TestRunSkill.RunHandle handle = run.handle;
    String finishedAt = null;
    if (run.finishedAt != null) {
      finishedAt = run.finishedAt.toString();
    }
    return new RunMetadata(handle.runId(), handle.command(), handle.logFile().toString(),
        handle.startedAt().toString(), handle.status(), finishedAt, handle.exitCode());
  }

  /**
   * Cancel {@code run} and wait for its final state to be recorded; a finished run is left as is.
   * Returns false when the state was not recorded in time.
   */
  boolean cancel(Run run) throws InterruptedException {
    run.handle.cancel();
    try {
      run.done.get(CANCEL_WAIT_SECONDS, TimeUnit.SECONDS);
      return true;
    } catch (TimeoutException e) {
      LOG.warning("Test run " + run.handle.runId() + " did not finish within " + CANCEL_WAIT_SECONDS
          + "s of being cancelled");
      return false;
    } catch (ExecutionException e) {
      throw new IllegalStateException("done never completes exceptionally", e);
    }
  }

  /** Cancel every active run (server shutdown); waits for each run's final state to be recorded. */
  void cancelAll() {
    List<Run> active = new ArrayList<>();
    synchronized (this) {
      for (Run run : runs.values()) {
        if (run.active()) {
          active.add(run);
        }
      }
    }
    for (Run run : active) {
      try {
        cancel(run);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        LOG.warning("Interrupted while cancelling test run " + run.handle.runId());
        return;
      }
    }
  }

  private synchronized void evictFinished() {
    int finished = 0;
    for (Run run : runs.values()) {
      if (!run.active()) {
        finished++;
      }
    }
    Iterator<Run> iterator = runs.values().iterator();
    while (finished > MAX_FINISHED && iterator.hasNext()) {
      Run run = iterator.next();
      if (!run.active()) {
        iterator.remove();
        finished--;
      }
    }
  }

  private void persist(Run run) {
    Path file = metadataFile(run.projectRoot, run.handle.runId());
    try {
      Files.createDirectories(file.getParent());
      mapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), metadata(run));
    } catch (IOException e) {
      LOG.warning("Cannot write run metadata " + file + ": " + e.getMessage());
    }
  }

  private static Path metadataFile(Path projectRoot, String runId) {
    return projectRoot.resolve(RUNS_DIR).resolve(runId + ".json");
  }
}
