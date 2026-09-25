package io.github.ygrip.testara.agent.skill;

import io.github.ygrip.testara.agent.index.BuildTool;
import io.github.ygrip.testara.agent.index.TestaraProjectProfile;
import io.github.ygrip.testara.agent.parser.CucumberReportParser;
import io.github.ygrip.testara.agent.safety.TestExecutionGuard;
import io.github.ygrip.testara.agent.skill.run.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Skill: resolve natural-language test intent → Cucumber tag expression → safe Maven or Gradle
 * execution, with a verdict that combines the process exit code and the report written by this run.
 *
 * <p>Options: {@code dryRun}, {@code execute}, {@code rerunFailed}, {@code module} (relative path such
 * as {@code modules/api}, or {@code :artifactId}), {@code format} ({@code json} for
 * {@link TestRunReport#toJson()}), {@code timeoutMinutes} (default {@value #DEFAULT_TIMEOUT_MINUTES}),
 * {@code gradleTask} (Gradle only, default {@value GradleCommandBuilder#DEFAULT_TASK}).
 */
public class TestRunSkill implements AgentSkill<String, String> {

  private static final Logger LOG = Logger.getLogger(TestRunSkill.class.getName());
  private static final String RERUN_FILE_PATH = "target/rerun/rerun.txt";
  private static final List<String> RERUN_FILE_CANDIDATES = List.of(RERUN_FILE_PATH, "build/rerun/rerun.txt");
  private static final List<String> MAVEN_REPORTS = List.of("target/destination/cucumber.json", "target/cucumber.json");
  private static final List<String> GRADLE_REPORTS = List.of("build/destination/cucumber.json", "build/cucumber.json",
      "target/destination/cucumber.json", "target/cucumber.json");
  private static final int REPORT_SEARCH_DEPTH = 6;
  private static final int JUNIT_SEARCH_DEPTH = 8;
  private static final int DEFAULT_TIMEOUT_MINUTES = 15;
  private static final int MAX_LOG_FACTS = 20;
  private static final Pattern AFFECTED_LINE = Pattern.compile("([\\w./\\\\-]+\\.(?:java|feature|xml|properties)):(\\d+)");
  private static final Pattern ZERO_COUNT = Pattern.compile("\\b(?:failures|errors)\\s*:\\s*0\\b");
  // Output markers read by exitCode(String); keep them in sync with the messages that use them.
  private static final String INVALID_OPTION = "Invalid test-run option: ";
  private static final String NEEDS_INPUT = "needs_input: ";
  private static final String PREFLIGHT_FAILED = "preflight: FAILED";
  private static final String EXECUTION_BLOCKED = "Execution blocked";
  private static final String EXECUTION_FAILED = "Execution failed: ";
  private static final String EXECUTION_INTERRUPTED = "Execution interrupted: ";
  private static final String STATUS_LINE = "**Status:** ";
  private static final List<String> FAILED_STATUSES = List.of("FAILED", "TIMEOUT");

  private final TagExpressionResolver resolver;
  private final MavenCommandBuilder cmdBuilder;
  private final GradleCommandBuilder gradleBuilder;

  public TestRunSkill() {
    this(new TagExpressionResolver(), new MavenCommandBuilder());
  }

  public TestRunSkill(TagExpressionResolver resolver, MavenCommandBuilder cmdBuilder) {
    this(resolver, cmdBuilder, new GradleCommandBuilder());
  }

  public TestRunSkill(TagExpressionResolver resolver, MavenCommandBuilder cmdBuilder,
      GradleCommandBuilder gradleBuilder) {
    this.resolver      = resolver;
    this.cmdBuilder    = cmdBuilder;
    this.gradleBuilder = gradleBuilder;
  }

  @Override
  public String name() { return "test-run"; }

  /**
   * Process exit code for an {@link #execute} output, for command-line callers: {@code 0} when the
   * run passed or only a plan was shown, {@code 1} when the run failed, timed out, could not start or
   * matched no scenario, {@code 2} when input was invalid or missing or execution was blocked.
   */
  public static int exitCode(String output) {
    String text = output.stripLeading();
    if (text.startsWith(INVALID_OPTION) || text.startsWith(NEEDS_INPUT) || text.contains(EXECUTION_BLOCKED)) {
      return 2;
    }
    if (text.startsWith(PREFLIGHT_FAILED) || text.startsWith(EXECUTION_FAILED)
        || text.startsWith(EXECUTION_INTERRUPTED)) {
      return 1;
    }
    for (String status : FAILED_STATUSES) {
      if (text.contains(STATUS_LINE + status) || text.contains("\"status\":\"" + status + "\"")) return 1;
    }
    return 0;
  }

  /** Invalid user input (module, Gradle task, timeout, tag expression) is reported, never thrown. */
  @Override
  public String execute(String input, AgentContext context) {
    try {
      return run(input, context);
    } catch (IllegalArgumentException e) {
      return INVALID_OPTION + e.getMessage() + "\n";
    }
  }

  private String run(String input, AgentContext context) {
    TestaraProjectProfile profile = context.profile();
    Map<String, String> opts = context.options();
    TagExpressionResolver activeResolver = resolverFor(opts);
    boolean dryRun    = "true".equals(opts.getOrDefault("dryRun", "false"));
    boolean execute   = !"false".equals(opts.getOrDefault("execute", "true"));
    boolean rerunFail = "true".equals(opts.getOrDefault("rerunFailed", "false"));
    boolean json      = "json".equalsIgnoreCase(opts.get("format"));
    RunSettings settings = runSettings(profile, opts);

    // Rerun-failed: read Cucumber's rerun file
    if (rerunFail) {
      Path rerunFile = findRerunFile(context.projectRoot(), settings.module());
      if (rerunFile == null) {
        return "No previous test failures found. Check that " + RERUN_FILE_PATH
            + " exists from a previous test run" + moduleHint(settings) + ".\n";
      }
      BuildCommand command = rerunCommand(context.projectRoot(), rerunFile, settings);
      TestRunPlan plan = new TestRunPlan("rerun-failed", "@" + rerunFile, 0, 0, command.display(), List.of());
      if (dryRun || !execute) return plan.toMarkdown();
      String blocked = executionBlocked(context);
      if (blocked != null) return plan.toMarkdown() + blocked;
      String result = executeAndReport(command, "@" + rerunFile, context.projectRoot(), settings, json);
      if (json) return result;
      return plan.toMarkdown() + "\n> **Rerun-failed** uses Cucumber's native rerun-file feature-path.\n\n" + result;
    }

    String tagExpr = activeResolver.resolve(input, profile);
    if (tagExpr.isBlank()) return unresolvedPrompt(input, context, profile);

    int matched = activeResolver.countMatching(tagExpr, profile);
    if (matched == 0) return preflightFailure(input, tagExpr, context, profile, activeResolver, settings);
    int matchedFeatures = (int) profile.features().stream()
        .filter(f -> f.scenarios().stream().anyMatch(s -> activeResolver.matches(tagExpr, f, s)))
        .count();

    List<String> matchedNames = profile.features().stream()
        .flatMap(f -> f.scenarios().stream()
            .filter(s -> activeResolver.matches(tagExpr, f, s))
            .map(s -> s.name()))
        .limit(10)
        .collect(Collectors.toList());

    BuildCommand command = runCommand(context.projectRoot(), tagExpr, settings);
    TestRunPlan plan = new TestRunPlan(input, tagExpr, matchedFeatures, matched, command.display(), matchedNames);

    if (dryRun || !execute) return plan.toMarkdown();
    String blocked = executionBlocked(context);
    if (blocked != null) return plan.toMarkdown() + blocked;
    return executeAndReport(command, tagExpr, context.projectRoot(), settings, json);
  }

  private TagExpressionResolver resolverFor(Map<String, String> options) {
    Map<String, String> aliases = new LinkedHashMap<>();
    options.forEach((key, value) -> {
      if (key.startsWith("tag-alias.") && value != null && !value.isBlank()) {
        aliases.put(key.substring("tag-alias.".length()), value);
      }
    });
    return aliases.isEmpty() ? resolver : new TagExpressionResolver(aliases);
  }

  private RunSettings runSettings(TestaraProjectProfile profile, Map<String, String> opts) {
    String module = opts.get("module");
    if (module != null && module.isBlank()) {
      module = null;
    }
    RunArguments.requireValidModule(module);
    boolean gradle = profile != null && profile.buildTool() == BuildTool.GRADLE;
    return new RunSettings(gradle, module, opts.get("gradleTask"), timeout(opts.get("timeoutMinutes")));
  }

  private Duration timeout(String value) {
    if (value == null || value.isBlank()) {
      return Duration.ofMinutes(DEFAULT_TIMEOUT_MINUTES);
    }
    int minutes;
    try {
      minutes = Integer.parseInt(value.strip());
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("timeoutMinutes must be a positive whole number, got: " + value, e);
    }
    if (minutes <= 0) {
      throw new IllegalArgumentException("timeoutMinutes must be a positive whole number, got: " + value);
    }
    return Duration.ofMinutes(minutes);
  }

  private BuildCommand runCommand(Path projectRoot, String tagExpr, RunSettings settings) {
    if (settings.gradle()) {
      return gradleBuilder.build(projectRoot, tagExpr, settings.module(), settings.gradleTask());
    }
    return cmdBuilder.command(projectRoot, tagExpr, settings.module());
  }

  private BuildCommand rerunCommand(Path projectRoot, Path rerunFile, RunSettings settings) {
    if (settings.gradle()) {
      return gradleBuilder.buildRerun(projectRoot, rerunFile, settings.module(), settings.gradleTask());
    }
    return cmdBuilder.rerunCommand(projectRoot, rerunFile, settings.module());
  }

  /** Returns the markdown note explaining why execution is blocked, or {@code null} when allowed. */
  private String executionBlocked(AgentContext context) {
    if (!context.allowsExecution()) {
      return "\n> **" + EXECUTION_BLOCKED + ".** Agent mode does not allow command execution.\n";
    }
    if (!TestExecutionGuard.isRunEnabled()) {
      return "\n> **" + EXECUTION_BLOCKED + ".** Set " + TestExecutionGuard.RUN_ENABLED_ENV
          + "=true to allow test execution.\n";
    }
    return null;
  }

  private String moduleHint(RunSettings settings) {
    if (settings.module() == null) {
      return "";
    }
    return " (searched module " + settings.module() + " and the project root)";
  }

  private String unresolvedPrompt(String input, AgentContext context, TestaraProjectProfile profile) {
    String availableTags = profile.tags().stream()
        .map(t -> t.tag())
        .limit(20)
        .collect(Collectors.joining(", "));
    String scenarioExamples = profile.features().stream()
        .flatMap(f -> f.scenarios().stream().map(s -> s.name()))
        .limit(8)
        .collect(Collectors.joining(" | "));
    return NEEDS_INPUT + "test_run_filter\n"
        + "question: Which tests should run? Provide an explicit tag, feature name, or scenario name from the project.\n"
        + "input: " + input + "\n"
        + "project-root: " + context.projectRoot() + "\n"
        + "indexed-features: " + profile.features().size() + " | indexed-scenarios: " + profile.totalScenarios() + "\n"
        + "available-tags: " + availableTags + "\n"
        + "scenario-examples: " + scenarioExamples + "\n"
        + "examples: @smoke, @ui and @checkout, feature name, exact scenario name";
  }

  private String preflightFailure(String input, String resolvedExpr, AgentContext context,
      TestaraProjectProfile profile, TagExpressionResolver activeResolver, RunSettings settings) {
    List<String> suggestions = activeResolver.suggestAlternatives(resolvedExpr, profile, 8);
    String availableTags = profile.tags().stream()
        .map(t -> t.tag())
        .limit(15)
        .collect(Collectors.joining(", "));
    StringBuilder sb = new StringBuilder();
    sb.append(PREFLIGHT_FAILED).append("\n");
    sb.append("reason: resolved expression matches 0 scenarios — ").append(toolName(settings))
        .append(" execution skipped\n");
    sb.append("mavenExecuted: false\n");
    sb.append("input: ").append(input).append("\n");
    sb.append("resolved-expression: ").append(resolvedExpr).append("\n");
    sb.append("indexed-scenarios: ").append(profile.totalScenarios()).append("\n");
    sb.append("available-tags: ").append(availableTags).append("\n");
    if (!suggestions.isEmpty()) {
      sb.append("suggested-expressions:\n");
      suggestions.forEach(s -> sb.append("  - ").append(s).append("\n"));
    }
    sb.append("fix: use an explicit @tag from available-tags, or call testara_context to list all project tags");
    return sb.toString();
  }

  private String executeAndReport(BuildCommand command, String tagExpr, Path projectRoot,
      RunSettings settings, boolean json) {
    String guardError = TestExecutionGuard.validateArgv(command.argv());
    if (guardError != null) {
      return EXECUTION_BLOCKED + " by safety guard: " + guardError + "\n";
    }
    if (settings.gradle()) {
      try {
        gradleBuilder.writeInitScript(projectRoot);
      } catch (IOException e) {
        return EXECUTION_FAILED + "cannot write Gradle init script "
            + GradleCommandBuilder.initScriptPath(projectRoot) + ": " + e.getMessage() + "\n";
      }
    }

    Path logFile = runLogFile(projectRoot, settings);
    // Report files older than this belong to an earlier run. Truncate to the second so that
    // filesystems with coarse (1 s) modification times still accept a report this run wrote.
    long runStart = System.currentTimeMillis() / 1000 * 1000;
    ProcessRunner.Outcome outcome;
    try {
      outcome = ProcessRunner.run(command.argv(), projectRoot, logFile, settings.timeout());
    } catch (IOException e) {
      return EXECUTION_FAILED + e.getMessage() + "\n";
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return EXECUTION_INTERRUPTED + "the build process tree was terminated. Log: " + logFile + "\n";
    }

    Path executionRoot = RunArguments.moduleDirectory(projectRoot, settings.module());
    ParsedReport parsed = parseFreshReport(executionRoot, settings, runStart, tagExpr, outcome.durationMs());
    String status = verdict(outcome, parsed.report());
    TestRunReport report;
    if (parsed.report() == null) {
      report = TestRunReport.withoutReport(status, outcome.durationMs(), tagExpr, logFile.toString());
    } else {
      report = parsed.report().forRun(status, outcome.durationMs(), logFile.toString(), parsed.file().toString());
    }
    if (json) return report.toJson();

    String summary = logSummary(String.join(" ", command.argv()), tagExpr, logFile, outcome, status,
        parsed.description(), settings);
    if (outcome.timedOut()) {
      summary += "\nTest execution exceeded the " + settings.timeout().toMinutes()
          + "-minute limit; the build process tree was terminated.\n";
    }
    if (parsed.report() == null) return summary;
    return report.toMarkdown() + "\n\n" + summary;
  }

  /** TIMEOUT and non-zero exit codes always win over a parsed report; a failed report fails a clean exit. */
  private String verdict(ProcessRunner.Outcome outcome, TestRunReport report) {
    if (outcome.timedOut()) return "TIMEOUT";
    if (outcome.exitCode() != 0) return "FAILED";
    if (report != null && "FAILED".equals(report.status())) return "FAILED";
    return "PASSED";
  }

  private record ParsedReport(TestRunReport report, Path file, String description) {}

  /** Prefer a cucumber.json written by this run, then JUnit XML written by this run. */
  private ParsedReport parseFreshReport(Path executionRoot, RunSettings settings, long runStart,
      String tagExpr, long durationMs) {
    Path cucumberJson = findCucumberReport(executionRoot, settings, runStart);
    if (cucumberJson != null) {
      try {
        return new ParsedReport(CucumberReportParser.parseCucumberJson(cucumberJson, tagExpr, durationMs),
            cucumberJson, "`" + cucumberJson + "`");
      } catch (IOException e) {
        LOG.warning("Cannot parse " + cucumberJson + ": " + e.getMessage());
        return new ParsedReport(null, null, "report missing — cannot parse `" + cucumberJson + "`: " + e.getMessage());
      }
    }
    List<Path> junitReports = findJunitReports(executionRoot, runStart);
    if (!junitReports.isEmpty()) {
      Path directory = junitReports.get(0).getParent();
      try {
        return new ParsedReport(CucumberReportParser.parseJunitXml(junitReports, tagExpr, durationMs),
            directory, "JUnit XML fallback (" + junitReports.size() + " files in `" + directory + "`)");
      } catch (IOException e) {
        LOG.warning("Cannot parse JUnit XML under " + directory + ": " + e.getMessage());
        return new ParsedReport(null, null, "report missing — cannot parse JUnit XML under `" + directory + "`: "
            + e.getMessage());
      }
    }
    return new ParsedReport(null, null, "report missing — no cucumber.json or JUnit XML written by this run under `"
        + executionRoot + "` (earlier reports are ignored)");
  }

  private Path findCucumberReport(Path executionRoot, RunSettings settings, long runStart) {
    List<String> candidates = MAVEN_REPORTS;
    if (settings.gradle()) {
      candidates = GRADLE_REPORTS;
    }
    for (String candidate : candidates) {
      Path report = executionRoot.resolve(candidate);
      if (Files.isRegularFile(report) && modifiedSince(report, runStart)) return report;
    }
    try (Stream<Path> files = Files.find(executionRoot, REPORT_SEARCH_DEPTH,
        (path, attrs) -> attrs.isRegularFile() && path.getFileName().toString().equals("cucumber.json")
            && attrs.lastModifiedTime().toMillis() >= runStart)) {
      return files
          .filter(this::inBuildOutput)
          .max(Comparator.comparingLong(this::lastModified))
          .orElse(null);
    } catch (IOException e) {
      LOG.warning("Cannot discover cucumber.json under " + executionRoot + ": " + e.getMessage());
      return null;
    }
  }

  /** Surefire/Failsafe ({@code target/*-reports}) and Gradle ({@code build/test-results}) JUnit XML. */
  private List<Path> findJunitReports(Path executionRoot, long runStart) {
    try (Stream<Path> files = Files.find(executionRoot, JUNIT_SEARCH_DEPTH,
        (path, attrs) -> attrs.isRegularFile() && attrs.lastModifiedTime().toMillis() >= runStart
            && isJunitReport(path))) {
      return files.sorted().toList();
    } catch (IOException e) {
      LOG.warning("Cannot discover JUnit XML under " + executionRoot + ": " + e.getMessage());
      return List.of();
    }
  }

  private boolean isJunitReport(Path path) {
    String name = path.getFileName().toString();
    String normalized = path.toString().replace('\\', '/');
    return name.startsWith("TEST-") && name.endsWith(".xml")
        && (normalized.contains("/target/surefire-reports/") || normalized.contains("/target/failsafe-reports/")
            || normalized.contains("/build/test-results/"));
  }

  private boolean inBuildOutput(Path path) {
    String normalized = path.toString().replace('\\', '/');
    return normalized.contains("/target/") || normalized.contains("/build/");
  }

  private boolean modifiedSince(Path path, long runStart) {
    return lastModified(path) >= runStart;
  }

  private long lastModified(Path path) {
    try {
      return Files.getLastModifiedTime(path).toMillis();
    } catch (IOException e) {
      LOG.warning("Cannot read modification time of " + path + ": " + e.getMessage());
      return Long.MIN_VALUE;
    }
  }

  private Path runLogFile(Path projectRoot, RunSettings settings) {
    String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").format(LocalDateTime.now());
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    if (settings.gradle()) {
      return projectRoot.resolve("build/testara-agent-logs/gradle-run-" + timestamp + "-" + suffix + ".log");
    }
    return projectRoot.resolve("target/testara-agent-logs/maven-run-" + timestamp + "-" + suffix + ".log");
  }

  private String toolName(RunSettings settings) {
    if (settings.gradle()) return "Gradle";
    return "Maven";
  }

  private String logSummary(String command, String tagExpr, Path logFile, ProcessRunner.Outcome outcome,
      String status, String reportDescription, RunSettings settings) {
    List<String> lines;
    try {
      lines = ProcessRunner.readLogLines(logFile);
    } catch (IOException e) {
      lines = List.of("Cannot read captured log: " + e.getMessage());
    }
    LogFacts facts = analyzeLog(lines);
    String tool = toolName(settings);
    StringBuilder sb = new StringBuilder();
    sb.append("## Test Run Log Summary\n\n");
    sb.append(STATUS_LINE).append(status).append("  \n");
    sb.append("**Exit code:** ").append(outcome.exitCode()).append("  \n");
    sb.append("**Duration:** ").append(outcome.durationMs() / 1000).append("s  \n");
    sb.append("**Tag filter:** `").append(tagExpr).append("`  \n");
    sb.append("**").append(tool).append(" command:** `").append(command).append("`  \n");
    sb.append("**Log file:** `").append(logFile).append("`  \n");
    sb.append("**Report:** ").append(reportDescription).append("  \n");
    sb.append("**").append(tool).append(" summary:** ").append(firstNonBlank(facts.testSummary(), "not found")).append("  \n");
    sb.append("**Scenario summary:** ").append(firstNonBlank(facts.scenarioSummary(), "not found")).append("  \n");
    sb.append("**Build summary:** ").append(firstNonBlank(facts.buildSummary(), "not found")).append("  \n");
    sb.append("**Elapsed time:** ").append(firstNonBlank(facts.elapsedTime(), outcome.durationMs() / 1000 + "s")).append("  \n");
    sb.append("\nerrors:\n");
    if (facts.errors().isEmpty()) sb.append("- none detected\n");
    else facts.errors().forEach(e -> sb.append("- ").append(e).append("\n"));
    sb.append("affected-lines:\n");
    if (facts.affectedLines().isEmpty()) sb.append("- none detected\n");
    else facts.affectedLines().forEach(l -> sb.append("- ").append(l).append("\n"));
    sb.append("next-step: read only the referenced log slices or affected files if this summary is insufficient.\n");
    return sb.toString();
  }

  static LogFacts analyzeLog(List<String> lines) {
    List<String> errors = new ArrayList<>();
    List<String> affectedLines = new ArrayList<>();
    String testSummary = "";
    String scenarioSummary = "";
    String buildSummary = "";
    String elapsed = "";
    for (int i = 0; i < lines.size(); i++) {
      String stripped = lines.get(i).strip();
      if (stripped.contains("Tests run:")) testSummary = stripped;
      if (stripped.matches(".*\\d+ scenarios?.*")) scenarioSummary = stripped;
      if (stripped.matches(".*\\d+ steps?.*")) {
        scenarioSummary = scenarioSummary.isBlank() ? stripped : scenarioSummary + "; " + stripped;
      }
      if (stripped.contains("BUILD SUCCESS") || stripped.contains("BUILD FAILURE")
          || stripped.contains("BUILD FAILED")) {
        buildSummary = stripped;
      }
      if (stripped.startsWith("Total time:")) elapsed = stripped.replace("Total time:", "").strip();
      if (isErrorLine(stripped) && errors.size() < MAX_LOG_FACTS) {
        errors.add("line " + (i + 1) + ": " + clipped(stripped));
      }
      Matcher matcher = AFFECTED_LINE.matcher(stripped);
      if (matcher.find() && affectedLines.size() < MAX_LOG_FACTS) {
        affectedLines.add(matcher.group(1) + ":" + matcher.group(2) + " (log line " + (i + 1) + ")");
      }
    }
    return new LogFacts(testSummary, scenarioSummary, buildSummary, elapsed, errors, affectedLines);
  }

  /** Zero counters such as "Failures: 0, Errors: 0" are a clean summary, not an error. */
  private static boolean isErrorLine(String stripped) {
    if (stripped.isBlank()) return false;
    String lower = ZERO_COUNT.matcher(stripped.toLowerCase(Locale.ROOT)).replaceAll("");
    return lower.contains("error") || lower.contains("exception") || lower.contains("failure");
  }

  private static String clipped(String value) {
    return value.length() <= 220 ? value : value.substring(0, 220) + "...";
  }

  private String firstNonBlank(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }

  /**
   * Locate Cucumber's rerun file (written to {@link #RERUN_FILE_PATH} by the
   * {@code cucumber.plugin=...,rerun:target/rerun/rerun.txt} plugin TestInitSkill configures),
   * looking in the selected module first and then the project root, or {@code null} if no previous
   * run produced one.
   */
  private Path findRerunFile(Path projectRoot, String module) {
    List<Path> roots = new ArrayList<>();
    roots.add(RunArguments.moduleDirectory(projectRoot, module));
    roots.add(projectRoot);
    for (Path root : roots) {
      for (String candidate : RERUN_FILE_CANDIDATES) {
        Path rerunFile = root.resolve(candidate);
        if (Files.isRegularFile(rerunFile)) return rerunFile;
      }
    }
    return null;
  }

  private record RunSettings(boolean gradle, String module, String gradleTask, Duration timeout) {}

  record LogFacts(String testSummary, String scenarioSummary, String buildSummary, String elapsedTime,
      List<String> errors, List<String> affectedLines) {}
}
