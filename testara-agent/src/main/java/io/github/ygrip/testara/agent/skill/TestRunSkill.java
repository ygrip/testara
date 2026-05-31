package io.github.ygrip.testara.agent.skill;

import io.github.ygrip.testara.agent.index.FeatureIndex;
import io.github.ygrip.testara.agent.index.TestaraProjectProfile;
import io.github.ygrip.testara.agent.skill.run.*;

import java.io.BufferedReader;
import java.util.concurrent.TimeUnit;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
    boolean dryRun    = !"false".equals(opts.getOrDefault("dryRun", "true"));
    boolean execute   = "true".equals(opts.getOrDefault("execute", "false"));
    boolean rerunFail = "true".equals(opts.getOrDefault("rerunFailed", "false"));
    String module     = opts.getOrDefault("module", null);

    // Rerun-failed: read Cucumber rerun file or previous report
    if (rerunFail) {
      String rerunResult = buildRerunExpression(context.projectRoot());
      if (rerunResult != null) {
        String command = cmdBuilder.build(rerunResult, module, false);
        return new TestRunPlan("rerun-failed", rerunResult, 0, 0, command, List.of()).toMarkdown()
            + "\n> **Rerun-failed** uses Cucumber rerun file or previous report.\n";
      }
      return "No previous test failures found. Check that target/rerun.txt or "
          + "target/cucumber.json exists from a previous test run.\n";
    }

    String tagExpr = resolver.resolve(input, profile);
    if (tagExpr.isBlank()) return "Could not resolve a tag expression from: \"" + input + "\"\n"
        + "Available tags: " + profile.tags().stream()
            .map(t -> t.tag()).collect(Collectors.joining(", "));

    int matched = resolver.countMatching(tagExpr, profile);
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

    String command = cmdBuilder.build(tagExpr, module, false);
    TestRunPlan plan = new TestRunPlan(input, tagExpr, matchedFeatures, matched, command, matchedNames);

    if (dryRun || !execute) return plan.toMarkdown();

    // Execution guard: respect TESTARA_AGENT_RUN_ENABLED
    boolean runEnabled = "true".equalsIgnoreCase(System.getenv("TESTARA_AGENT_RUN_ENABLED"));
    if (!runEnabled) {
      return plan.toMarkdown() + "\n> **Execution blocked.** Set TESTARA_AGENT_RUN_ENABLED=true to allow test execution.\n";
    }
    return executeAndReport(command, tagExpr, context.projectRoot());
  }

  private String executeAndReport(String command, String tagExpr, Path projectRoot) {
    long start = System.currentTimeMillis();
    List<String> output = new ArrayList<>();
    int exitCode;
    // Choose mvnw or mvn
    java.nio.file.Path mvnw = projectRoot.resolve("mvnw");
    String mvnExec = java.nio.file.Files.exists(mvnw) ? mvnw.toAbsolutePath().toString() : "mvn";
    String safeCommand = command.replace("mvn ", mvnExec + " ");
    try {
      String[] cmd = {"sh", "-c", safeCommand};
      ProcessBuilder pb = new ProcessBuilder(cmd)
          .directory(projectRoot.toFile())
          .redirectErrorStream(true);
      Process process = pb.start();
      try (BufferedReader br = new BufferedReader(
          new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
        String line;
        while ((line = br.readLine()) != null) output.add(line);
      }
      boolean finished = process.waitFor(15, TimeUnit.MINUTES);
      if (!finished) {
        process.destroyForcibly();
        return "## Test Run Report\n\n**Status:** TIMEOUT  \nTest execution exceeded 15-minute limit.\n";
      }
      exitCode = process.exitValue();
    } catch (IOException | InterruptedException e) {
      if (e instanceof InterruptedException) Thread.currentThread().interrupt();
      return "Execution failed: " + e.getMessage();
    }
    long duration = System.currentTimeMillis() - start;

    // Try Cucumber JSON / JUnit XML report parsing
    Path reportJson = projectRoot.resolve("target/cucumber.json");
    Path junitXml = projectRoot.resolve("target/surefire-reports");
    if (Files.exists(reportJson)) {
      try {
        TestRunReport report = io.github.ygrip.testara.agent.parser.CucumberReportParser
            .parseCucumberJson(reportJson, tagExpr, duration);
        return report.toMarkdown();
      } catch (IOException e) {
        LOG.warning("Cannot parse cucumber.json: " + e.getMessage());
      }
    }

    // Fallback: console summary
    String status = exitCode == 0 ? "PASSED" : "FAILED";
    return "## Test Run Report\n\n**Status:** " + status + "  \n**Duration:** "
        + duration / 1000 + "s  \n**Tag filter:** `" + tagExpr + "`  \n";
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
}
