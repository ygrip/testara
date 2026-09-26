package io.github.ygrip.testara.agent.validation;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

import io.github.ygrip.testara.agent.safety.TestExecutionGuard;
import io.github.ygrip.testara.agent.skill.run.ProcessRunner;

/**
 * Runs mvn test-compile (or the project's Maven wrapper) and returns a compact result.
 * Used by TestInitSkill after writing project files. Output goes to
 * {@code target/testara-agent-logs/compile-*.log}; the gate honours
 * {@code TESTARA_AGENT_RUN_ENABLED=false} by skipping the build.
 */
public class TestCompileGate {

  private static final int DEFAULT_TIMEOUT_SECONDS = 120;
  /** Start of the {@link Result#toLine()} line of a failed compile; read by the skills' exit codes. */
  private static final String FAILED_LINE = "compile: FAILED";

  public record Result(boolean passed, long durationMs, String summary, List<String> errors, boolean skipped) {

    public Result(boolean passed, long durationMs, String summary, List<String> errors) {
      this(passed, durationMs, summary, errors, false);
    }

    public String toLine() {
      String dur = durationMs / 1000 + "." + (durationMs % 1000) / 100 + "s";
      if (skipped) return summary;
      if (passed) return "compile: PASSED (" + dur + ")";
      String errSummary = errors.isEmpty() ? "" : "\n" + errors.stream()
          .limit(5).map(e -> "  - " + e).collect(Collectors.joining("\n"));
      return FAILED_LINE + " — " + errors.size() + " error(s) (" + dur + ")" + errSummary;
    }
  }

  public Result run(Path projectRoot, int timeoutSeconds) {
    if (!TestExecutionGuard.isRunEnabled()) {
      return new Result(false, 0, "compile: SKIPPED — " + TestExecutionGuard.RUN_ENABLED_ENV
          + "=false blocks agent-launched builds", List.of(), true);
    }
    long start = System.currentTimeMillis();
    List<String> command = List.of(ProcessRunner.mavenLauncher(projectRoot),
        "test-compile", "-B", "--no-transfer-progress", "-q");
    Path logFile = projectRoot.resolve("target/testara-agent-logs/compile-"
        + DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").format(LocalDateTime.now()) + ".log");

    ProcessRunner.Outcome outcome;
    List<String> output;
    try {
      outcome = ProcessRunner.run(command, projectRoot, logFile, Duration.ofSeconds(timeoutSeconds));
      output = ProcessRunner.readLogLines(logFile);
    } catch (IOException e) {
      return new Result(false, System.currentTimeMillis() - start,
          "compile: ERROR — " + e.getMessage(), List.of(String.valueOf(e.getMessage())));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return new Result(false, System.currentTimeMillis() - start,
          "compile: INTERRUPTED", List.of("Compilation interrupted; build process terminated"));
    }
    if (outcome.timedOut()) {
      return new Result(false, outcome.durationMs(),
          "compile: TIMEOUT after " + timeoutSeconds + "s", List.of("Compilation timed out (log: " + logFile + ")"));
    }

    boolean passed = outcome.exitCode() == 0;

    // Extract ERROR lines for the summary
    List<String> errors = output.stream()
        .filter(l -> l.startsWith("[ERROR]") || l.contains("error:") || l.contains("ERROR"))
        .filter(l -> !l.contains("BUILD") && !l.contains("Downloading"))
        .map(l -> l.replaceFirst("^\\[ERROR\\]\\s*", "").trim())
        .filter(l -> !l.isBlank())
        .distinct()
        .limit(10)
        .collect(Collectors.toList());

    String summary = "compile: PASSED";
    if (!passed) {
      summary = FAILED_LINE;
    }
    return new Result(passed, outcome.durationMs(), summary, errors);
  }

  public Result run(Path projectRoot) {
    return run(projectRoot, DEFAULT_TIMEOUT_SECONDS);
  }

  /** True when a skill output carries the line of a failed compile gate. */
  public static boolean reportsFailure(String output) {
    return output.contains(FAILED_LINE);
  }
}
