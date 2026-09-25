package io.github.ygrip.testara.agent.skill.run;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Runs build-tool processes (Maven/Gradle) for the agent: OS-aware launcher resolution, output
 * redirected to a log file (so a chatty build can never block on a full pipe), a hard timeout, and
 * termination of the whole process tree (forked test JVMs, browsers, drivers) on timeout or
 * interrupt.
 */
public final class ProcessRunner {

  private static final Logger LOG = Logger.getLogger(ProcessRunner.class.getName());
  private static final long KILL_WAIT_SECONDS = 5;

  /**
   * @param exitCode   process exit code, or {@code -1} when it timed out
   * @param timedOut   whether the timeout elapsed and the process tree was killed
   * @param durationMs wall-clock duration
   */
  public record Outcome(int exitCode, boolean timedOut, long durationMs) {}

  private ProcessRunner() { /* utility */ }

  /** Maven launcher: the project's wrapper when present, else {@code mvn} from PATH. */
  public static String mavenLauncher(Path projectRoot) {
    return mavenLauncher(projectRoot, isWindows());
  }

  static String mavenLauncher(Path projectRoot, boolean windows) {
    if (windows) {
      return launcher(projectRoot, "mvnw.cmd", "mvn.cmd");
    }
    return launcher(projectRoot, "mvnw", "mvn");
  }

  /** Gradle launcher: the project's wrapper when present, else {@code gradle} from PATH. */
  public static String gradleLauncher(Path projectRoot) {
    return gradleLauncher(projectRoot, isWindows());
  }

  static String gradleLauncher(Path projectRoot, boolean windows) {
    if (windows) {
      return launcher(projectRoot, "gradlew.bat", "gradle.bat");
    }
    return launcher(projectRoot, "gradlew", "gradle");
  }

  private static String launcher(Path projectRoot, String wrapper, String fallback) {
    Path wrapperFile = projectRoot.resolve(wrapper);
    if (Files.isRegularFile(wrapperFile)) {
      return wrapperFile.toAbsolutePath().toString();
    }
    return fallback;
  }

  private static boolean isWindows() {
    return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
  }

  /**
   * Run {@code argv} (no shell) in {@code workingDir}, writing stdout and stderr to {@code logFile}.
   * The process tree is killed when the timeout elapses or the calling thread is interrupted; an
   * interrupt is rethrown after the tree has been terminated.
   */
  public static Outcome run(List<String> argv, Path workingDir, Path logFile, Duration timeout)
      throws IOException, InterruptedException {
    Files.createDirectories(logFile.toAbsolutePath().getParent());
    long start = System.currentTimeMillis();
    Process process = new ProcessBuilder(argv)
        .directory(workingDir.toFile())
        .redirectErrorStream(true)
        .redirectOutput(logFile.toFile())
        .start();
    boolean finished = false;
    try {
      finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
    } finally {
      if (!finished) {
        destroyTree(process);
      }
    }
    long duration = System.currentTimeMillis() - start;
    if (!finished) {
      return new Outcome(-1, true, duration);
    }
    return new Outcome(process.exitValue(), false, duration);
  }

  /** Kill descendants first (they are re-parented, and invisible, once the root dies), then the root. */
  static void destroyTree(Process process) {
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

  /** Read a captured log leniently: malformed or non-UTF-8 bytes are replaced instead of failing. */
  public static List<String> readLogLines(Path logFile) throws IOException {
    if (!Files.exists(logFile)) {
      return List.of();
    }
    return new String(Files.readAllBytes(logFile), StandardCharsets.UTF_8).lines().toList();
  }
}
