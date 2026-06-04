package io.github.ygrip.testara.agent.skill;

import io.github.ygrip.testara.agent.index.FeatureIndex;
import io.github.ygrip.testara.agent.index.TestaraProjectProfile;
import io.github.ygrip.testara.agent.skill.run.*;

import java.util.concurrent.TimeUnit;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Skill: resolve natural-language test intent → Cucumber tag expression → safe Maven execution.
 */
public class TestRunSkill implements AgentSkill<String, String> {

  private static final Logger LOG = Logger.getLogger(TestRunSkill.class.getName());

  private final TagExpressionResolver resolver;
  private final MavenCommandBuilder cmdBuilder;

  public TestRunSkill() {
    this(new TagExpressionResolver(), new MavenCommandBuilder());
  }

  public TestRunSkill(TagExpressionResolver resolver, MavenCommandBuilder cmdBuilder) {
    this.resolver   = resolver;
    this.cmdBuilder = cmdBuilder;
  }

  @Override
  public String name() { return "test-run"; }

  @Override
  public String execute(String input, AgentContext context) {
    TestaraProjectProfile profile = context.profile();
    Map<String, String> opts = context.options();
    boolean dryRun    = "true".equals(opts.getOrDefault("dryRun", "false"));
    boolean execute   = !"false".equals(opts.getOrDefault("execute", "true"));
    boolean rerunFail = "true".equals(opts.getOrDefault("rerunFailed", "false"));
    String module     = opts.getOrDefault("module", null);

    // Rerun-failed: read Cucumber rerun file or previous report
    if (rerunFail) {
      String rerunResult = buildRerunExpression(context.projectRoot());
      if (rerunResult != null) {
        String command = cmdBuilder.build(rerunResult, module, true);
        return new TestRunPlan("rerun-failed", rerunResult, 0, 0, command, List.of()).toMarkdown()
            + "\n> **Rerun-failed** uses Cucumber rerun file or previous report.\n";
      }
      return "No previous test failures found. Check that target/rerun.txt or "
          + "target/cucumber.json exists from a previous test run.\n";
    }

    String tagExpr = resolver.resolve(input, profile);
    if (tagExpr.isBlank()) return unresolvedPrompt(input, context, profile);

    int matched = resolver.countMatching(tagExpr, profile);
    if (matched == 0) return preflightFailure(input, tagExpr, context, profile);
    int matchedFeatures = (int) profile.features().stream()
        .filter(f -> f.scenarios().stream().anyMatch(s ->
            resolver.countMatching(tagExpr, profileForScenario(profile, f, s)) > 0))
        .count();

    List<String> matchedNames = profile.features().stream()
        .flatMap(f -> f.scenarios().stream()
            .filter(s -> resolver.countMatching(tagExpr,
                profileForScenario(profile, f, s)) > 0)
            .map(s -> s.name()))
        .limit(10)
        .collect(Collectors.toList());

    String command = cmdBuilder.build(tagExpr, module, true);
    TestRunPlan plan = new TestRunPlan(input, tagExpr, matchedFeatures, matched, command, matchedNames);

    if (dryRun || !execute) return plan.toMarkdown();

    // Execution guard: enabled by default. Set TESTARA_AGENT_RUN_ENABLED=false to block execution.
    boolean runEnabled = !"false".equalsIgnoreCase(System.getenv("TESTARA_AGENT_RUN_ENABLED"));
    if (!runEnabled) {
      return plan.toMarkdown() + "\n> **Execution blocked.** Set TESTARA_AGENT_RUN_ENABLED=true to allow test execution.\n";
    }
    return executeAndReport(command, tagExpr, context.projectRoot());
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
    return "needs_input: test_run_filter\n"
        + "question: Which tests should run? Provide an explicit tag, feature name, or scenario name from the project.\n"
        + "input: " + input + "\n"
        + "project-root: " + context.projectRoot() + "\n"
        + "indexed-features: " + profile.features().size() + " | indexed-scenarios: " + profile.totalScenarios() + "\n"
        + "available-tags: " + availableTags + "\n"
        + "scenario-examples: " + scenarioExamples + "\n"
        + "examples: @smoke, @ui and @checkout, feature name, exact scenario name";
  }

  private String preflightFailure(String input, String resolvedExpr,
      AgentContext context, TestaraProjectProfile profile) {
    List<String> suggestions = resolver.suggestAlternatives(resolvedExpr, profile, 8);
    String availableTags = profile.tags().stream()
        .map(t -> t.tag())
        .limit(15)
        .collect(Collectors.joining(", "));
    StringBuilder sb = new StringBuilder();
    sb.append("preflight: FAILED\n");
    sb.append("reason: resolved expression matches 0 scenarios — Maven execution skipped\n");
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

  private String executeAndReport(String command, String tagExpr, Path projectRoot) {
    long start = System.currentTimeMillis();
    int exitCode;
    // Choose mvnw or mvn
    java.nio.file.Path mvnw = projectRoot.resolve("mvnw");
    String mvnExec = java.nio.file.Files.exists(mvnw) ? mvnw.toAbsolutePath().toString() : "mvn";
    String safeCommand = command.replace("mvn ", mvnExec + " ");
    Path logFile = runLogFile(projectRoot);
    try {
      Files.createDirectories(logFile.getParent());
      String[] cmd = {"sh", "-c", safeCommand};
      ProcessBuilder pb = new ProcessBuilder(cmd)
          .directory(projectRoot.toFile())
          .redirectErrorStream(true)
          .redirectOutput(logFile.toFile());
      Process process = pb.start();
      boolean finished = process.waitFor(15, TimeUnit.MINUTES);
      if (!finished) {
        process.destroyForcibly();
        long duration = System.currentTimeMillis() - start;
        return logSummary(safeCommand, tagExpr, logFile, -1, duration, "TIMEOUT")
            + "\nTest execution exceeded 15-minute limit.\n";
      }
      exitCode = process.exitValue();
    } catch (IOException | InterruptedException e) {
      if (e instanceof InterruptedException) Thread.currentThread().interrupt();
      return "Execution failed: " + e.getMessage();
    }
    long duration = System.currentTimeMillis() - start;

    // Try Cucumber JSON / JUnit XML report parsing
    Path reportJson = projectRoot.resolve("target/cucumber.json");
    String summary = logSummary(safeCommand, tagExpr, logFile, exitCode, duration, exitCode == 0 ? "PASSED" : "FAILED");
    if (Files.exists(reportJson)) {
      try {
        TestRunReport report = io.github.ygrip.testara.agent.parser.CucumberReportParser
            .parseCucumberJson(reportJson, tagExpr, duration);
        return report.toMarkdown() + "\n\n" + summary;
      } catch (IOException e) {
        LOG.warning("Cannot parse cucumber.json: " + e.getMessage());
      }
    }

    // Fallback: console summary
    return summary;
  }

  private Path runLogFile(Path projectRoot) {
    String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now());
    return projectRoot.resolve("target/testara-agent-logs/maven-run-" + timestamp + ".log");
  }

  private String logSummary(String command, String tagExpr, Path logFile, int exitCode, long durationMillis,
      String status) {
    List<String> lines;
    try {
      lines = Files.exists(logFile) ? Files.readAllLines(logFile, StandardCharsets.UTF_8) : List.of();
    } catch (IOException e) {
      lines = List.of("Cannot read captured log: " + e.getMessage());
    }
    LogFacts facts = analyzeLog(lines);
    StringBuilder sb = new StringBuilder();
    sb.append("## Test Run Log Summary\n\n");
    sb.append("**Status:** ").append(status).append("  \n");
    sb.append("**Exit code:** ").append(exitCode).append("  \n");
    sb.append("**Duration:** ").append(durationMillis / 1000).append("s  \n");
    sb.append("**Tag filter:** `").append(tagExpr).append("`  \n");
    sb.append("**Maven command:** `").append(command).append("`  \n");
    sb.append("**Log file:** `").append(logFile).append("`  \n");
    sb.append("**Maven summary:** ").append(firstNonBlank(facts.mavenSummary(), "not found")).append("  \n");
    sb.append("**Scenario summary:** ").append(firstNonBlank(facts.scenarioSummary(), "not found")).append("  \n");
    sb.append("**Build summary:** ").append(firstNonBlank(facts.buildSummary(), "not found")).append("  \n");
    sb.append("**Elapsed time:** ").append(firstNonBlank(facts.elapsedTime(), durationMillis / 1000 + "s")).append("  \n");
    sb.append("\nerrors:\n");
    if (facts.errors().isEmpty()) sb.append("- none detected\n");
    else facts.errors().forEach(e -> sb.append("- ").append(e).append("\n"));
    sb.append("affected-lines:\n");
    if (facts.affectedLines().isEmpty()) sb.append("- none detected\n");
    else facts.affectedLines().forEach(l -> sb.append("- ").append(l).append("\n"));
    sb.append("next-step: read only the referenced log slices or affected files if this summary is insufficient.\n");
    return sb.toString();
  }

  private LogFacts analyzeLog(List<String> lines) {
    List<String> errors = new ArrayList<>();
    List<String> affectedLines = new ArrayList<>();
    String mavenSummary = "";
    String scenarioSummary = "";
    String buildSummary = "";
    String elapsed = "";
    Pattern affected = Pattern.compile("([\\w./\\\\-]+\\.(?:java|feature|xml|properties)):(\\d+)");
    for (int i = 0; i < lines.size(); i++) {
      String line = lines.get(i);
      String stripped = line.strip();
      if (stripped.contains("Tests run:")) mavenSummary = stripped;
      if (stripped.matches(".*\\d+ scenarios?.*")) scenarioSummary = stripped;
      if (stripped.matches(".*\\d+ steps?.*")) {
        scenarioSummary = scenarioSummary.isBlank() ? stripped : scenarioSummary + "; " + stripped;
      }
      if (stripped.contains("BUILD SUCCESS") || stripped.contains("BUILD FAILURE")) buildSummary = stripped;
      if (stripped.startsWith("Total time:")) elapsed = stripped.replace("Total time:", "").strip();
      String lower = stripped.toLowerCase();
      if ((lower.contains("error") || lower.contains("exception") || lower.contains("failure"))
          && !stripped.isBlank() && errors.size() < 20) {
        errors.add("line " + (i + 1) + ": " + clipped(stripped));
      }
      Matcher matcher = affected.matcher(stripped);
      if (matcher.find() && affectedLines.size() < 20) {
        affectedLines.add(matcher.group(1) + ":" + matcher.group(2) + " (log line " + (i + 1) + ")");
      }
    }
    return new LogFacts(mavenSummary, scenarioSummary, buildSummary, elapsed, errors, affectedLines);
  }

  private String clipped(String value) {
    return value.length() <= 220 ? value : value.substring(0, 220) + "...";
  }

  private String firstNonBlank(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }

  /**
   * Build a rerun expression from Cucumber's rerun file or previous JSON report.
   * Returns null if no previous failure data is found.
   */
  private String buildRerunExpression(Path projectRoot) {
    // 1. Try Cucumber rerun file
    Path rerunFile = projectRoot.resolve("target/rerun.txt");
    if (Files.exists(rerunFile)) {
      try {
        return Files.readString(rerunFile, StandardCharsets.UTF_8).strip();
      } catch (IOException e) {
        LOG.fine("Cannot read rerun file: " + e.getMessage());
      }
    }

    // 2. Try parsing previous cucumber.json for failed scenario locations
    Path reportJson = projectRoot.resolve("target/cucumber.json");
    if (Files.exists(reportJson)) {
      try {
        String json = Files.readString(reportJson, StandardCharsets.UTF_8);
        List<String> failedLocations = new ArrayList<>();
        // Extract failed scenario URIs from cucumber JSON
        Pattern uriPattern = Pattern.compile("\"uri\"\\s*:\\s*\"([^\"]+)\"");
        Pattern failedLine = Pattern.compile("\"line\"\\s*:\\s*(\\d+)");
        Matcher uriMatcher = uriPattern.matcher(json);
        Matcher lineMatcher = failedLine.matcher(json);

        // Collect URIs and lines; pair by position (naive approach)
        List<String> uris = new ArrayList<>();
        List<Integer> lines = new ArrayList<>();
        while (uriMatcher.find()) uris.add(uriMatcher.group(1));
        while (lineMatcher.find()) lines.add(Integer.parseInt(lineMatcher.group(1)));

        // Fallback: if no granular data, return empty
        if (!uris.isEmpty()) {
          return "@target/rerun.txt"; // signals Cucumber to use its own rerun
        }
      } catch (IOException e) {
        LOG.fine("Cannot parse cucumber.json for rerun: " + e.getMessage());
      }
    }
    return null;
  }

  private TestaraProjectProfile profileForScenario(TestaraProjectProfile base,
      FeatureIndex feature, io.github.ygrip.testara.agent.index.ScenarioIndex scenario) {
    var merged = new ArrayList<>(feature.tags());
    merged.addAll(scenario.tags());
    var fakeFeature = new FeatureIndex(feature.path(), feature.featureName(),
        feature.tags(), List.of(scenario), feature.backgroundSteps());
    var fakeTag = base.tags().stream()
        .filter(t -> merged.contains(t.tag())).toList();
    return new TestaraProjectProfile(base.projectRoot(), base.buildTool(),
        base.javaVersion(), base.mavenModules(), base.featureRoots(),
        base.requestSpecRoots(), base.validationRoots(),
        List.of(fakeFeature), base.stepDefinitions(),
        base.commands(), base.validations(), base.drivers(), fakeTag,
        base.properties(), base.conventions(), base.flavorSteps(), base.runtimeCatalog());
  }

  private record LogFacts(String mavenSummary, String scenarioSummary, String buildSummary, String elapsedTime,
      List<String> errors, List<String> affectedLines) {}
}
