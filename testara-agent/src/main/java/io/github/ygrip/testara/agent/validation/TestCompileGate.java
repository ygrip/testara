package io.github.ygrip.testara.agent.validation;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Runs mvn test-compile (or ./mvnw test-compile) and returns a compact result.
 * Used by TestInitSkill after writing project files.
 */
public class TestCompileGate {

  private static final Logger LOG = Logger.getLogger(TestCompileGate.class.getName());
  private static final int DEFAULT_TIMEOUT_SECONDS = 120;

  public record Result(boolean passed, long durationMs, String summary, List<String> errors) {
    public String toLine() {
      String dur = durationMs / 1000 + "." + (durationMs % 1000) / 100 + "s";
      if (passed) return "compile: PASSED (" + dur + ")";
      String errSummary = errors.isEmpty() ? "" : "\n" + errors.stream()
          .limit(5).map(e -> "  - " + e).collect(Collectors.joining("\n"));
      return "compile: FAILED — " + errors.size() + " error(s) (" + dur + ")" + errSummary;
    }
  }

  public Result run(Path projectRoot, int timeoutSeconds) {
    long start = System.currentTimeMillis();
    Path mvnw = projectRoot.resolve("mvnw");
    String mvnExec = Files.exists(mvnw) ? mvnw.toAbsolutePath().toString() : "mvn";

    List<String> output = new ArrayList<>();
    int exitCode;
    try {
      ProcessBuilder pb = new ProcessBuilder(mvnExec, "test-compile", "-B", "--no-transfer-progress", "-q")
          .directory(projectRoot.toFile())
          .redirectErrorStream(true);
      Process proc = pb.start();
      try (BufferedReader br = new BufferedReader(
          new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
        String line;
        while ((line = br.readLine()) != null) output.add(line);
      }
      boolean finished = proc.waitFor(timeoutSeconds, TimeUnit.SECONDS);
      if (!finished) {
        proc.destroyForcibly();
        return new Result(false, System.currentTimeMillis() - start,
            "compile: TIMEOUT after " + timeoutSeconds + "s", List.of("Compilation timed out"));
      }
      exitCode = proc.exitValue();
    } catch (IOException | InterruptedException e) {
      if (e instanceof InterruptedException) Thread.currentThread().interrupt();
      return new Result(false, System.currentTimeMillis() - start,
          "compile: ERROR — " + e.getMessage(), List.of(e.getMessage()));
    }

    long duration = System.currentTimeMillis() - start;
    boolean passed = exitCode == 0;

    // Extract ERROR lines for the summary
    List<String> errors = output.stream()
        .filter(l -> l.startsWith("[ERROR]") || l.contains("error:") || l.contains("ERROR"))
        .filter(l -> !l.contains("BUILD") && !l.contains("Downloading"))
        .map(l -> l.replaceFirst("^\\[ERROR\\]\\s*", "").trim())
        .filter(l -> !l.isBlank())
        .distinct()
        .limit(10)
        .collect(Collectors.toList());

    return new Result(passed, duration, passed ? "compile: PASSED" : "compile: FAILED", errors);
  }

  public Result run(Path projectRoot) {
    return run(projectRoot, DEFAULT_TIMEOUT_SECONDS);
  }
}
