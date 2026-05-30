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
  private static final Pattern JSON_STATUS  = Pattern.compile("\"status\"\\s*:\\s*\"(passed|failed|pending|skipped)\"");
  private static final Pattern JSON_NAME    = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");
  private static final Pattern JSON_MESSAGE = Pattern.compile("\"message\"\\s*:\\s*\"([^\"]+)\"");

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
    boolean dryRun  = !"false".equals(opts.getOrDefault("dryRun", "true"));
    boolean execute = "true".equals(opts.getOrDefault("execute", "false"));
    String module   = opts.getOrDefault("module", null);

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

    // Try to parse Cucumber JSON report
    Path reportJson = projectRoot.resolve("target/cucumber.json");
    if (Files.exists(reportJson)) {
      try {
        TestRunReport report = parseJson(Files.readString(reportJson), tagExpr, duration);
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

  private TestRunReport parseJson(String json, String tagExpr, long durationMs) {
    int passed = 0, failed = 0, skipped = 0;
    List<TestRunReport.FailedScenario> failedScenarios = new ArrayList<>();
    // Simple scan — avoids full JSON parse dependency
    Matcher statusMatcher = JSON_STATUS.matcher(json);
    while (statusMatcher.find()) {
      switch (statusMatcher.group(1)) {
        case "passed"  -> passed++;
        case "failed"  -> failed++;
        case "skipped", "pending" -> skipped++;
      }
    }
    String status = failed > 0 ? "FAILED" : "PASSED";
    return new TestRunReport(status, durationMs, tagExpr,
        passed + failed + skipped, passed, failed, skipped, failedScenarios);
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
        base.commands(), base.validations(), base.drivers(), fakeTag);
  }
}
