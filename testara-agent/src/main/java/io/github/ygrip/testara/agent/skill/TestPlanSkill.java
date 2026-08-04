package io.github.ygrip.testara.agent.skill;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.ygrip.testara.agent.catalog.GenerationGuard;
import io.github.ygrip.testara.agent.catalog.PropertyRuleEngine;
import io.github.ygrip.testara.agent.catalog.StepLinker;
import io.github.ygrip.testara.agent.flavor.FlavorEntry;
import io.github.ygrip.testara.agent.index.TestaraProjectProfile;
import io.github.ygrip.testara.agent.knowledge.FrameworkKnowledgeStore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Skill: generate a Testara-flavor Cucumber .feature file from user intent.
 *
 * Priority order (from plan):
 *   1. Testara built-in steps (from FlavorCatalog)
 *   2. Project-specific steps (from profile.stepDefinitions)
 *   3. Generate extension artifact (command, validation, request spec)
 *   4. Mark as MISSING only as last resort
 */
public class TestPlanSkill implements AgentSkill<TestPlanSkill.Input, String> {

  private static final Logger LOG = Logger.getLogger(TestPlanSkill.class.getName());
  private static final ObjectMapper MAPPER = new ObjectMapper();

  public record Input(String intent, String slice, String domain, List<String> tags,
      String mode, String featureFiles, boolean createFiles, boolean useExistingActionCatalog) {
    public Input(String intent, String slice, String domain, List<String> tags) {
      this(intent, slice, domain, tags, null, null, false, false);
    }
  }

  @Override
  public String name() { return "test-plan"; }

  @Override
  public String execute(Input input, AgentContext context) {
    if ("batch".equalsIgnoreCase(input.mode())) {
      return executeBatch(input, context);
    }

    TestaraProjectProfile profile = context.profile();
    String slice  = input.slice() != null ? input.slice() : inferSlice(input.intent());
    String domain = input.domain() != null ? input.domain() : inferDomain(input.intent());
    String clarification = clarificationPrompt(input.intent(), slice, input.domain() != null, profile);
    if (clarification != null) return clarification;
    List<String> tags = buildTags(input.tags(), slice, domain);
    boolean write = "true".equals(context.options().get("write"));
    boolean concise = "concise".equals(context.options().get("format"));

    // Use project-level catalog if available, otherwise fall back to bundled framework catalog
    List<FlavorEntry> flavorSteps = profile.flavorStepsForSlice(slice);
    if (flavorSteps.isEmpty()) {
      flavorSteps = FrameworkKnowledgeStore.instance().flavorCatalogForSlice(slice);
    }
    String featureContent = generateFlavorFeature(input.intent(), domain, tags, slice, flavorSteps, profile);
    String placement = resolvePlacement(slice, domain);
    String fileName  = toFileName(input.intent());

    String writtenPath = null;
    List<String> generatedArtifacts = new ArrayList<>();
    if (write) {
      writtenPath = writeFeatureFile(context.projectRoot(), placement, fileName, featureContent);
      if ("api".equals(slice)) {
        generatedArtifacts.addAll(generateRequestSpecs(context.projectRoot(), domain, input.intent()));
      } else if ("ui".equals(slice)) {
        String pageName = inferUiPage(input.intent(), domain);
        String actionName = inferUiAction(input.intent(), domain);
        generatedArtifacts.add("suggested: testara_bootstrap artifact=page pageName=" + pageName);
        generatedArtifacts.add("optional: testara_bootstrap artifact=action pageName=" + pageName
            + " actionName=\"" + actionName + "\" when this flow should be reusable");
      }
    }

    var stepLinks = StepLinker.linkFeature(featureContent, flavorSteps, profile.stepDefinitions());
    int builtInCount = (int) stepLinks.stream().filter(StepLinker.Link::matched).count();
    int totalStepLines = stepLinks.size();
    int missingCount = countMissing(featureContent);
    int score = totalStepLines > 0 ? (builtInCount * 100 / totalStepLines) : 100;
    int runtimeScore = computeRuntimeContextScore(featureContent, generatedArtifacts, slice);

    List<String> scenarioNames = Arrays.stream(featureContent.split("\n"))
        .filter(l -> l.trim().startsWith("Scenario:"))
        .map(l -> l.trim().substring("Scenario:".length()).trim())
        .toList();
    List<String> featureTags = Arrays.stream(featureContent.split("\n"))
        .filter(l -> l.trim().startsWith("@") && !l.trim().startsWith("@P"))
        .flatMap(l -> Arrays.stream(l.trim().split("\\s+")))
        .filter(t -> t.startsWith("@") && !Set.of("@P1","@P2","@P3","@positive","@negative").contains(t))
        .distinct()
        .toList();

    if (concise) {
      StringBuilder sb = new StringBuilder();
      if (write) {
        sb.append(writtenPath != null ? "written: " + writtenPath : "write failed");
        generatedArtifacts.forEach(a -> sb.append("\ngenerated: ").append(a));
        if (writtenPath != null) {
          String scenarioSymbols = scenarioNames.isEmpty() ? "" : "; scenarios:" + String.join(",", scenarioNames);
          String tagSymbols = featureTags.isEmpty() ? "" : "; tags:" + String.join(" ", featureTags);
          sb.append("\nfilesChanged:\n- created ").append(writtenPath)
              .append(" [feature:").append(toFeatureName(input.intent()))
              .append(scenarioSymbols).append(tagSymbols).append("]");
        }
        sb.append("\n\n");
      }
      sb.append(featureContent);
      if (missingCount > 0) sb.append("\nmissing: ").append(missingCount).append(" steps need implementation");
      sb.append("\nflavor-score: ").append(score).append("% | runtime-context-score: ").append(runtimeScore).append("%");
      var violations = GenerationGuard.validateFeature(featureContent, flavorSteps, profile.stepDefinitions());
      if (!violations.isEmpty()) {
        sb.append("\nguardrail-violations: ").append(violations.size()).append("\n");
        violations.forEach(v -> sb.append("  ").append(v.format()).append("\n"));
      }
      return sb.toString();
    }

    StringBuilder sb = new StringBuilder();
    sb.append("## Test Plan: ").append(input.intent()).append("\n\n");
    sb.append("**Slice:** ").append(slice).append("  **Domain:** ").append(domain).append("  \n");
    sb.append("**Placement:** `").append(placement).append(fileName).append("`\n\n");
    if (write) {
      sb.append(writtenPath != null ? "> Written to `" + writtenPath + "`\n" : "> Warning: could not write.\n");
      generatedArtifacts.forEach(a -> sb.append("> Generated: `").append(a).append("`\n"));
      if (writtenPath != null) {
        String scenarioSymbols = scenarioNames.isEmpty() ? "" : "; scenarios:" + String.join(",", scenarioNames);
        String tagSymbols = featureTags.isEmpty() ? "" : "; tags:" + String.join(" ", featureTags);
        sb.append("> filesChanged: created ").append(writtenPath)
            .append(" [feature:").append(toFeatureName(input.intent()))
            .append(scenarioSymbols).append(tagSymbols).append("]\n");
      }
      sb.append("\n");
    }
    sb.append("```gherkin\n").append(featureContent).append("\n```\n\n");
    sb.append("**Testara Flavor Score: ").append(score).append("% | Runtime Context Score: ").append(runtimeScore).append("%**  \n");
    sb.append("Built-in steps: ").append(builtInCount).append("  | Missing: ").append(missingCount).append("\n");
    if (write && writtenPath != null) {
      sb.append("\nNext: `testara-agent test-run '").append(input.intent()).append("' --execute`\n");
    }
    // Guardrail check
    var violations = GenerationGuard.validateFeature(featureContent, flavorSteps, profile.stepDefinitions());
    return GenerationGuard.annotate(sb.toString(), violations);
  }

  private String executeBatch(Input input, AgentContext context) {
    List<FeatureBatchSpec> features = parseFeatureBatch(input);
    if (features.isEmpty()) {
      return "needs_input: testara_plan_batch\n"
          + "reason: featureFiles must be a JSON array with featureName/path/scenarios.\n";
    }

    List<String> createdFeatureFiles = new ArrayList<>();
    List<String> usedActions = new ArrayList<>();
    List<String> unresolvedActions = new ArrayList<>();
    Set<String> tagIndex = new LinkedHashSet<>();
    List<String> fileSummaries = new ArrayList<>();
    StringBuilder preview = new StringBuilder();
    Map<String, String> actionCatalog = actionCatalog(context.projectRoot());
    boolean write = input.createFiles() || "true".equals(context.options().get("write"));

    for (FeatureBatchSpec feature : features) {
      String featureText = buildBatchFeature(feature, actionCatalog, usedActions, unresolvedActions, tagIndex);
      preview.append("\n--- ").append(feature.path()).append(" ---\n").append(featureText).append("\n");
      if (write) {
        String written = writeFeatureAtPath(context.projectRoot(), feature.path(), featureText);
        if (written != null) {
          createdFeatureFiles.add(written);
          String scenarioNames = feature.scenarios().stream()
              .map(ScenarioBatchSpec::name)
              .collect(Collectors.joining(","));
          String tags = feature.tags().isEmpty() ? "" : "; tags:" + String.join(" ", feature.tags());
          fileSummaries.add("created " + written + " [feature:" + feature.featureName()
              + "; scenarios:" + scenarioNames + tags + "]");
        }
      }
    }

    String recommendedTag = tagIndex.contains("@regression") ? "@regression"
        : tagIndex.stream().findFirst().orElse("@regression");
    StringBuilder sb = new StringBuilder();
    sb.append("mode: batch\n");
    sb.append("featureFiles: ").append(features.size()).append("\n");
    sb.append("createdFeatureFiles:\n");
    if (createdFeatureFiles.isEmpty()) sb.append("- none\n");
    else createdFeatureFiles.forEach(path -> sb.append("- ").append(path).append("\n"));
    sb.append("usedActions:\n");
    if (usedActions.isEmpty()) sb.append("- none\n");
    else usedActions.stream().distinct().forEach(action -> sb.append("- ").append(action).append("\n"));
    sb.append("unresolvedActions:\n");
    if (unresolvedActions.isEmpty()) sb.append("- none\n");
    else unresolvedActions.stream().distinct().forEach(action -> sb.append("- ").append(action).append("\n"));
    sb.append("tagIndex:\n");
    if (tagIndex.isEmpty()) sb.append("- none\n");
    else tagIndex.forEach(tag -> sb.append("- ").append(tag).append("\n"));
    sb.append("recommendedRun: testara_run --tags ").append(recommendedTag).append("\n");
    sb.append("filesChanged:\n");
    if (fileSummaries.isEmpty()) sb.append("- none\n");
    else fileSummaries.forEach(summary -> sb.append("- ").append(summary).append("\n"));
    if (!"summary".equals(context.options().get("format"))) {
      sb.append("\npreview:\n").append(preview);
    }
    return sb.toString();
  }

  private List<FeatureBatchSpec> parseFeatureBatch(Input input) {
    String raw = input.featureFiles();
    if (raw == null || raw.isBlank()) return List.of();
    try {
      JsonNode root = MAPPER.readTree(raw);
      JsonNode array = root.isArray() ? root : root.path("featureFiles");
      if (!array.isArray()) return List.of();
      List<FeatureBatchSpec> features = new ArrayList<>();
      for (JsonNode feature : array) {
        String featureName = first(feature.path("featureName").asText(null),
            feature.path("name").asText(null), "Generated feature");
        String path = first(feature.path("path").asText(null),
            "src/test/resources/features/" + toFileName(featureName));
        List<ScenarioBatchSpec> scenarios = new ArrayList<>();
        JsonNode scenarioArray = feature.path("scenarios");
        if (scenarioArray.isArray()) {
          for (JsonNode scenario : scenarioArray) {
            scenarios.add(new ScenarioBatchSpec(
                first(scenario.path("name").asText(null), "Generated scenario"),
                first(scenario.path("intent").asText(null), scenario.path("name").asText("")),
                stringArray(scenario.path("tags")),
                stringArray(scenario.path("steps"))));
          }
        }
        features.add(new FeatureBatchSpec(path, featureName, stringArray(feature.path("tags")), scenarios));
      }
      return features;
    } catch (IOException e) {
      return List.of();
    }
  }

  private String buildBatchFeature(FeatureBatchSpec feature, Map<String, String> actionCatalog,
      List<String> usedActions, List<String> unresolvedActions, Set<String> tagIndex) {
    StringBuilder sb = new StringBuilder();
    sb.append("# Generated by Testara Agent — review before committing.\n\n");
    appendTags(sb, feature.tags(), tagIndex, false);
    sb.append("Feature: ").append(feature.featureName()).append("\n\n");
    sb.append("  Background:\n");
    sb.append("    Given user using chrome in desktop\n\n");
    for (ScenarioBatchSpec scenario : feature.scenarios()) {
      appendTags(sb, scenario.tags(), tagIndex, true);
      sb.append("  Scenario: ").append(scenario.name()).append("\n");
      List<String> steps = scenario.steps().isEmpty()
          ? stepsFromIntent(scenario.intent(), actionCatalog, usedActions, unresolvedActions)
          : scenario.steps();
      for (String step : steps) {
        sb.append("    ").append(stripStepIndent(step)).append("\n");
      }
      sb.append("\n");
    }
    return sb.toString().stripTrailing();
  }

  private List<String> stepsFromIntent(String intent, Map<String, String> actionCatalog,
      List<String> usedActions, List<String> unresolvedActions) {
    String lower = intent == null ? "" : intent.toLowerCase(Locale.ROOT);
    String page = inferUiPage(lower, inferDomain(first(intent, "generated")));
    String action = bestAction(lower, actionCatalog);
    List<String> steps = new ArrayList<>();
    steps.add("When user open \"" + page + "\" page");
    steps.add("Then user is in \"" + page + "\" page");
    if (action != null) {
      usedActions.add(action);
      steps.add("When user do \"" + action + "\" in \"" + page + "\" page with parameter");
      UiActionParameters params = uiActionParameters(page, action,
          lower.contains("invalid") || lower.contains("negative") || lower.contains("error"));
      steps.addAll(params.toFeatureTable(6).lines().map(String::strip).toList());
    } else {
      unresolvedActions.add(intent);
      steps.add("# MISSING action for intent: " + intent);
    }
    if (lower.contains("error") || lower.contains("invalid") || lower.contains("negative")) {
      steps.add("Then user should see \"error message\" is displayed");
    } else if (lower.contains("cart")) {
      steps.add("Then user should see \"cart badge\" is displayed");
    } else if (lower.contains("inventory")) {
      steps.add("Then user is in \"inventory\" page");
    } else {
      steps.add("Then user should see \"success message\" is displayed");
    }
    return steps;
  }

  private Map<String, String> actionCatalog(Path root) {
    Map<String, String> actions = new LinkedHashMap<>();
    try (var stream = Files.walk(root)) {
      stream.filter(path -> path.toString().endsWith(".java"))
          .filter(path -> !path.toString().contains("/target/"))
          .forEach(path -> {
            try {
              String source = Files.readString(path, StandardCharsets.UTF_8);
              var matcher = java.util.regex.Pattern.compile("@Action\\s*\\(\\s*(?:value\\s*=\\s*)?\"([^\"]+)\"")
                  .matcher(source);
              while (matcher.find()) {
                String action = matcher.group(1);
                actions.put(action.toLowerCase(Locale.ROOT), action);
              }
            } catch (IOException ignored) {
            }
          });
    } catch (IOException ignored) {
    }
    return actions;
  }

  private String bestAction(String lowerIntent, Map<String, String> actionCatalog) {
    if (actionCatalog.isEmpty()) return null;
    return actionCatalog.entrySet().stream()
        .filter(entry -> lowerIntent.contains(entry.getKey()) || wordsOverlap(lowerIntent, entry.getKey()) >= 2)
        .max(Comparator.comparingInt(entry -> wordsOverlap(lowerIntent, entry.getKey())))
        .map(Map.Entry::getValue)
        .orElse(null);
  }

  private int wordsOverlap(String left, String right) {
    Set<String> leftWords = Arrays.stream(left.split("[^a-z0-9]+"))
        .filter(word -> word.length() > 2)
        .collect(Collectors.toSet());
    int count = 0;
    for (String word : right.split("[^a-z0-9]+")) {
      if (word.length() > 2 && leftWords.contains(word)) count++;
    }
    return count;
  }

  private String writeFeatureAtPath(Path root, String relative, String content) {
    try {
      Path target = root.resolve(relative);
      Files.createDirectories(target.getParent());
      Files.writeString(target, content + "\n", StandardCharsets.UTF_8);
      return root.relativize(target).toString();
    } catch (IOException e) {
      LOG.warning("Cannot write feature file: " + e.getMessage());
      return null;
    }
  }

  private void appendTags(StringBuilder sb, List<String> tags, Set<String> tagIndex, boolean scenario) {
    if (tags.isEmpty()) return;
    tags.forEach(tagIndex::add);
    if (scenario) sb.append("  ");
    sb.append(String.join(" ", tags)).append("\n");
  }

  private List<String> stringArray(JsonNode node) {
    if (!node.isArray()) return List.of();
    List<String> values = new ArrayList<>();
    node.forEach(item -> {
      String value = item.asText("");
      if (!value.isBlank()) values.add(value);
    });
    return values;
  }

  private String stripStepIndent(String step) {
    return step.strip().replaceFirst("^(Given|When|Then|And|But)\\s+", "$1 ");
  }

  private String first(String... values) {
    for (String value : values) {
      if (value != null && !value.isBlank()) return value;
    }
    return "";
  }

  private record FeatureBatchSpec(String path, String featureName, List<String> tags,
      List<ScenarioBatchSpec> scenarios) {}
  private record ScenarioBatchSpec(String name, String intent, List<String> tags, List<String> steps) {}

  // ── Feature generation ────────────────────────────────────────────────────

  private String generateFlavorFeature(String intent, String domain, List<String> tags,
      String slice, List<FlavorEntry> flavorSteps, TestaraProjectProfile profile) {
    StringBuilder sb = new StringBuilder();
    sb.append("# Generated by Testara Agent — review before committing.\n\n");
    sb.append(tags.stream().collect(Collectors.joining(" "))).append("\n");
    sb.append("Feature: ").append(toFeatureName(intent)).append("\n\n");

    if ("ui".equals(slice)) {
      sb.append(buildStandardUiFeature(intent, domain));
      return sb.toString();
    }

    // Background
    String background = buildBackground(domain, slice, flavorSteps);
    if (!background.isBlank()) {
      sb.append("  Background:\n").append(background).append("\n");
    }

    // Positive scenario
    sb.append("  @P1 @positive\n");
    sb.append("  Scenario: ").append(toFeatureName(intent)).append(" — happy path\n");
    sb.append(buildScenarioSteps(intent, domain, slice, flavorSteps, profile, false));
    sb.append("\n");

    // Negative scenario
    sb.append("  @P2 @negative\n");
    sb.append("  Scenario: ").append(toFeatureName(intent)).append(" — failure case\n");
    sb.append(buildScenarioSteps(intent, domain, slice, flavorSteps, profile, true));

    return sb.toString();
  }

  private String buildBackground(String domain, String slice, List<FlavorEntry> flavorSteps) {
    return switch (slice) {
      case "api" -> {
        String initStep = findExample(flavorSteps, "Given", "using service with alias");
        if (initStep != null) {
          String step = initStep.replace("{name}", domain + "-api");
          yield "    Given " + step + "\n";
        }
        yield "";
      }
      case "ui" -> {
        // Always include driver init — missing it causes session=null NPE on first open-page step
        String sessionStep = findExample(flavorSteps, "Given", "using");
        String step = sessionStep != null
            ? sessionStep.replace("{name}", "chrome").replace("{param}", "desktop")
            : "user using chrome in desktop";
        yield "    Given " + step + "\n";
      }
      default -> "";
    };
  }

  private String buildScenarioSteps(String intent, String domain, String slice,
      List<FlavorEntry> flavorSteps, TestaraProjectProfile profile, boolean negative) {
    return switch (slice) {
      case "api" -> buildApiSteps(intent, domain, flavorSteps, negative);
      case "ui"  -> buildUiSteps(intent, domain, flavorSteps, negative);
      case "sql" -> buildSqlSteps(domain, negative);
      case "mongo" -> buildMongoSteps(domain, negative);
      default    -> buildGenericSteps(intent, domain, negative);
    };
  }

  // ── API scenario steps ────────────────────────────────────────────────────

  private String buildApiSteps(String intent, String domain, List<FlavorEntry> flavorSteps, boolean negative) {
    StringBuilder sb = new StringBuilder();
    String verb = extractVerb(intent);
    String flow = domain + "/" + verb;

    // Use properties() for path param IDs — they're test data
    String idProp = "properties(test." + domain + ".id)";
    String pathParamStep = findExample(flavorSteps, "Given", "prepare pathParam");
    if (pathParamStep != null) {
      sb.append("    Given ").append(sub(pathParamStep, "id", idProp)).append("\n");
    }

    if (negative) {
      String bodyStep = findExample(flavorSteps, "Given", "prepare body request");
      if (bodyStep != null) {
        // Payload path is a file path — not env-specific, OK as literal
        sb.append("    Given ").append(sub(bodyStep,
            "files/" + domain + "/payload/" + verb + "-invalid.json", null)).append("\n");
      }
    }

    // Prefer request spec (files/{domain}/request/{flow}) over inline when possible
    String requestStep = findExample(flavorSteps, "When", "process request");
    if (requestStep != null) {
      sb.append("    When ").append(sub(requestStep,
          "files/" + domain + "/request/" + verb + "-" + domain, null)).append("\n");
    } else {
      // Fallback: direct HTTP step — endpoint should come from properties
      String httpStep = findExample(flavorSteps, "When", "try");
      if (httpStep != null) {
        sb.append("    When ").append(sub(httpStep,
            "properties(api." + domain + ".endpoint)", null)).append("\n");
      }
    }

    // Then: assert response
    String statusStep = findExample(flavorSteps, "Then", "statusCode should be");
    if (statusStep != null) {
      sb.append("    Then ").append(statusStep
          .replaceAll("\\{\\w+\\}", negative ? "400" : "200")).append("\n");
    }

    if (!negative) {
      String successStep = findExample(flavorSteps, "Then", "response success should be");
      if (successStep != null) {
        sb.append("    Then ").append(successStep.replaceAll("\\{\\w+\\}", "true")).append("\n");
      }
      String assignStep = findExample(flavorSteps, "Then", "assign previous response data");
      if (assignStep != null) {
        sb.append("    Then ").append(assignStep.replaceAll("\\{\\w+\\}", domain + "Response")).append("\n");
      }
    } else {
      String successStep = findExample(flavorSteps, "Then", "response success should be");
      if (successStep != null) {
        sb.append("    Then ").append(successStep.replaceAll("\\{\\w+\\}", "false")).append("\n");
      }
    }

    return sb.toString();
  }

  // ── UI scenario steps ─────────────────────────────────────────────────────

  private String buildUiSteps(String intent, String domain, List<FlavorEntry> flavorSteps, boolean negative) {
    StringBuilder sb = new StringBuilder();

    // Open page always from base steps
    String openStep = findExample(flavorSteps, "When", "open .* page");
    if (openStep != null) {
      sb.append("    When ").append(openStep.replace("{value}", domain)).append("\n");
    }

    if (!negative) {
      appendUiBaseActionSteps(sb, domain, extractVerb(intent) + " " + domain, false);
      String pageStep = findExample(flavorSteps, "Then", "is in .* page");
      if (pageStep != null) sb.append("    Then ").append(pageStep.replace("{value}", domain + "-result")).append("\n");
      String seeStep = findExample(flavorSteps, "Then", "should see .* is");
      if (seeStep != null) sb.append("    Then ").append(seeStep.replace("{value}", "successMessage").replace("{param}", "displayed")).append("\n");
    } else {
      appendUiBaseActionSteps(sb, domain, extractVerb(intent) + " " + domain, true);
      String seeStep = findExample(flavorSteps, "Then", "should see .* is");
      if (seeStep != null) sb.append("    Then ").append(seeStep.replace("{value}", "errorMessage").replace("{param}", "displayed")).append("\n");
    }
    return sb.toString();
  }

  private String buildStandardUiFeature(String intent, String domain) {
    String pageName = toPropertyKey(inferUiPage(intent, domain));
    String actionName = inferUiAction(intent, domain);
    String successPage = toPropertyKey(inferUiSuccessPage(intent, domain, pageName));
    String successElement = inferUiSuccessElement(intent);
    return """
          Background:
            Given user using chrome in desktop

          @P1 @positive
          Scenario: %s succeeds
            When user open "%s" page
            Then user is in "%s" page
        %s
            Then user is in "%s" page
            Then user should see "%s" is displayed

          @P2 @negative
          Scenario: %s shows validation error
            When user open "%s" page
            Then user is in "%s" page
        %s
            Then user should see "error message" is displayed
        """.formatted(toFeatureName(actionName), pageName, pageName,
        uiBaseActionSteps(pageName, actionName, false), successPage, successElement,
        toFeatureName(actionName), pageName, pageName, uiBaseActionSteps(pageName, actionName, true));
  }

  private void appendUiBaseActionSteps(StringBuilder sb, String pageName, String actionName, boolean invalid) {
    sb.append(uiBaseActionSteps(pageName, actionName, invalid));
  }

  private String uiBaseActionSteps(String pageName, String actionName, boolean invalid) {
    UiActionParameters params = uiActionParameters(pageName, actionName, invalid);
    // RULE 3: 3+ operations on same page → user do "action" in "page" page with parameter
    // Always generate the UserAction step with | key | value | DataTable — never individual type/click steps
    return "    When user do \"" + actionName + "\" in \"" + pageName + "\" page with parameter\n"
        + params.toFeatureTable(6) + "\n";
  }

  // ── SQL scenario steps ────────────────────────────────────────────────────

  private String buildSqlSteps(String domain, boolean negative) {
    StringBuilder sb = new StringBuilder();
    sb.append("    Given [sql] connect to database with name ").append(domain).append("Db\n");
    sb.append("    Given [sql] prepare query with value :\n");
    sb.append("      \"\"\"\n");
    sb.append("      select *\n      from ").append(domain).append("\n");
    // Use properties() for test data values — IDs are test-specific
    if (negative) sb.append("      where status = 'INVALID'\n");
    else sb.append("      where id = 'properties(test.").append(domain).append(".id)'\n");
    sb.append("      \"\"\"\n");
    sb.append("    When [sql] execute database query\n");
    sb.append("    Then [sql] assign previous database response to ").append(domain).append("Rows\n");
    return sb.toString();
  }

  // ── Mongo scenario steps ──────────────────────────────────────────────────

  private String buildMongoSteps(String domain, boolean negative) {
    StringBuilder sb = new StringBuilder();
    sb.append("    Given [mongo] connect to database with name ").append(domain).append("Db\n");
    sb.append("    Given [mongo] select collection with name ").append(domain).append("\n");
    sb.append("    When [mongo] select data with query :\n");
    // Use properties() for query values — test data
    if (negative) sb.append("      | query | {\"status\":\"INVALID\"} |\n");
    else sb.append("      | query | {\"_id\":\"properties(test.").append(domain).append(".id)\"} |\n");
    sb.append("      | limit | 1 |\n");
    sb.append("    Then [mongo] assign previous database response to ").append(domain).append("Rows\n");
    return sb.toString();
  }

  // ── Generic fallback steps ────────────────────────────────────────────────

  private String buildGenericSteps(String intent, String domain, boolean negative) {
    String verb = extractVerb(intent);
    StringBuilder sb = new StringBuilder();
    sb.append("    Given the system is ready for ").append(domain).append(" # MISSING\n");
    sb.append("    When the ").append(verb).append(" operation is performed on ").append(domain).append(" # MISSING\n");
    sb.append("    Then the result should be ").append(negative ? "an error" : "success").append(" # MISSING\n");
    return sb.toString();
  }

  // ── Utilities ─────────────────────────────────────────────────────────────

  /** Substitutes first and optionally second {placeholder} in an example step. */
  private String sub(String example, String first, String second) {
    String s = example.replaceFirst("\\{\\w+\\}", first != null ? first : "");
    if (second != null) s = s.replaceFirst("\\{\\w+\\}", second);
    return s;
  }

  /** Find the first flavor step whose example matches a key phrase (supports simple regex). */
  private String findExample(List<FlavorEntry> steps, String keyword, String phrasePattern) {
    return steps.stream()
        .filter(e -> e.keyword().equalsIgnoreCase(keyword))
        .filter(e -> e.example().matches(".*" + phrasePattern + ".*")
            || e.expression().matches(".*" + phrasePattern.replace(".*", "\\S*") + ".*"))
        .map(FlavorEntry::example)
        .findFirst()
        .orElse(null);
  }

  /** Generates request spec and payload JSON files for API flows. */
  private List<String> generateRequestSpecs(Path projectRoot, String domain, String intent) {
    List<String> generated = new ArrayList<>();
    String verb = extractVerb(intent);
    String flowName = verb + "-" + domain;

    // Request spec
    String requestDir = "src/test/resources/files/" + domain + "/request";
    String requestFile = requestDir + "/" + flowName + ".json";
    // Use properties() for URL and test data — not hardcoded values
    String requestSpec = """
        {
          "specification": "%s-api",
          "httpMethod": "POST",
          "url": "properties(api.%s.endpoint)",
          "contentType": "application/json",
          "pathParameters": {
            "id": "properties(test.%s.id)"
          }
        }
        """.formatted(domain, domain, domain);
    writeJsonIfAbsent(projectRoot, requestFile, requestSpec, generated);

    // Payload stubs — values should come from properties() in production tests
    String payloadDir = "src/test/resources/files/" + domain + "/payload";
    writeJsonIfAbsent(projectRoot, payloadDir + "/" + flowName + "-success.json",
        "{\n  \"field\": \"properties(test." + domain + ".field)\"\n}\n", generated);
    writeJsonIfAbsent(projectRoot, payloadDir + "/" + flowName + "-invalid.json",
        "{\n  \"field\": null\n}\n", generated);

    return generated;
  }

  private void writeJsonIfAbsent(Path root, String relative, String content, List<String> generated) {
    try {
      Path target = root.resolve(relative);
      if (Files.exists(target)) return;
      Files.createDirectories(target.getParent());
      Files.writeString(target, content, StandardCharsets.UTF_8);
      generated.add(relative);
    } catch (IOException e) {
      LOG.fine("Cannot write " + relative + ": " + e.getMessage());
    }
  }

  private String writeFeatureFile(Path projectRoot, String placement, String fileName, String content) {
    try {
      Path dir = projectRoot.resolve(placement);
      Files.createDirectories(dir);
      Path file = dir.resolve(fileName);
      Files.writeString(file, content, StandardCharsets.UTF_8);
      return projectRoot.relativize(file).toString();
    } catch (IOException e) {
      LOG.warning("Cannot write feature file: " + e.getMessage());
      return null;
    }
  }

  /** Runtime Context Score: measures correct use of properties(), request specs, UserAction. */
  private int computeRuntimeContextScore(String feature, List<String> artifacts, String slice) {
    int points = 0, total = 0;

    // 1. properties() usage for non-status-code quoted values
    long quotedValues = Arrays.stream(feature.split("\n"))
        .filter(l -> l.contains("\"") && (l.trim().startsWith("Given") || l.trim().startsWith("When")))
        .count();
    long propertiesUsed = Arrays.stream(feature.split("\n"))
        .filter(l -> l.contains("properties(") || l.contains("prop(")).count();
    long hardcodedUrls = Arrays.stream(feature.split("\n"))
        .filter(l -> l.contains("localhost") || (l.contains("http://") && !l.contains("properties("))).count();

    if (quotedValues > 0) { total += 40; points += hardcodedUrls == 0 ? 40 : Math.max(0, 40 - (int)(hardcodedUrls * 20)); }
    if (propertiesUsed > 0) { total += 20; points += 20; }

    // 2. Request spec for API (not inline inline params)
    if ("api".equals(slice)) {
      total += 20;
      boolean usesRequestSpec = feature.contains("process request to");
      points += usesRequestSpec ? 20 : 0;
    }

    // 3. UI interaction shape
    if ("ui".equals(slice)) {
      total += 20;
      boolean usesAction = feature.contains("user do \"") && feature.contains("in \"") && feature.contains("page");
      boolean usesBuiltInUi = feature.contains("user type value \"")
          || feature.contains("user click the \"")
          || feature.contains("user should see \"");
      points += (usesAction || usesBuiltInUi) ? 20 : 0;
    }

    // 4. Generated support artifacts (request spec, action files)
    if (!artifacts.isEmpty()) { total += 20; points += 20; }

    return total == 0 ? 100 : (points * 100 / total);
  }

  private int countMissing(String feature) {
    return (int) Arrays.stream(feature.split("\n"))
        .filter(l -> l.contains("# MISSING")).count();
  }

  private String resolvePlacement(String slice, String domain) {
    return switch (slice.toLowerCase(Locale.ROOT)) {
      case "api"       -> "src/test/resources/features/api/" + domain + "/";
      case "ui"        -> "src/test/resources/features/ui/" + domain + "/";
      case "sql", "database" -> "src/test/resources/features/database/" + domain + "/";
      case "mongo"     -> "src/test/resources/features/database/" + domain + "/";
      case "kafka", "streaming" -> "src/test/resources/features/streaming/" + domain + "/";
      case "elastic"   -> "src/test/resources/features/elastic/" + domain + "/";
      default          -> "src/test/resources/features/" + domain + "/";
    };
  }

  private String toFileName(String intent) {
    String slug = intent.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    return slug.substring(0, Math.min(50, slug.length())) + ".feature";
  }

  private String inferSlice(String intent) {
    String lower = intent.toLowerCase(Locale.ROOT);
    if (lower.contains("sql") || lower.contains("database") || lower.contains("db")
        || lower.contains("query") || lower.contains("table") || lower.contains("settlement")
        || lower.contains("row")) return "sql";
    if (lower.contains("mongo") || lower.contains("collection") || lower.contains("document")) return "mongo";
    if (lower.contains("kafka") || lower.contains("topic") || lower.contains("consumer")
        || lower.contains("producer") || lower.contains("streaming")) return "kafka";
    if (lower.contains("ui") || lower.contains("page") || lower.contains("click")
        || lower.contains("button") || lower.contains("login") || lower.contains("browser")
        || lower.contains("selenium") || lower.contains("playwright")
        || lower.contains("appium") || lower.contains("vibium")) return "ui";
    return "api"; // default
  }

  private String inferUiPage(String intent, String domain) {
    String lower = intent == null ? "" : intent.toLowerCase(Locale.ROOT);
    if (lower.contains("login") || lower.contains("credential")) return "login";
    if (lower.contains("checkout")) return "checkout";
    if (lower.contains("cart")) return "cart";
    if (lower.contains("search")) return "search";
    return domain;
  }

  private String inferUiAction(String intent, String domain) {
    String lower = intent == null ? "" : intent.toLowerCase(Locale.ROOT);
    if (lower.contains("login") || lower.contains("credential") || lower.contains("sign in")) return "login with credentials";
    if (lower.contains("register") || lower.contains("sign up")) return "register account";
    if (lower.contains("search")) return "search " + domain;
    if (lower.contains("add") && (lower.contains("cart") || lower.contains("basket"))) return "add " + domain + " to cart";
    if (lower.contains("checkout")) return "checkout " + domain;
    if (lower.contains("remove") && lower.contains("cart")) return "remove " + domain + " from cart";
    if (lower.contains("filter") || lower.contains("sort")) return "filter " + domain;
    String verb = extractVerb(intent);
    if ("process".equals(verb)) return "submit " + domain;
    return verb + " " + domain;
  }

  private String inferUiSuccessPage(String intent, String domain, String pageName) {
    String lower = intent == null ? "" : intent.toLowerCase(Locale.ROOT);
    // Explicit page mentions take priority
    for (String candidate : List.of("inventory", "dashboard", "home", "cart", "checkout-complete",
        "checkout-overview", "checkout", "confirmation", "results", "profile")) {
      if (!candidate.equals(pageName) && lower.contains(candidate.replace("-", " ").replace("-", ""))) return candidate;
    }
    if (lower.contains("login") || lower.contains("sign in")) return "inventory";
    if (lower.contains("add") && lower.contains("cart")) return "cart";
    if (lower.contains("checkout")) return "checkout-complete";
    if (lower.contains("search")) return "results";
    return pageName;
  }

  private String inferUiSuccessElement(String intent) {
    String lower = intent == null ? "" : intent.toLowerCase(Locale.ROOT);
    if (lower.contains("add") && (lower.contains("cart") || lower.contains("basket"))) return "cart badge";
    if (lower.contains("search")) return "search results";
    if (lower.contains("checkout")) return "success message";
    if (lower.contains("login") || lower.contains("sign in")) return "success message";
    if (lower.contains("remove")) return "cart badge";
    return "success message";
  }

  private UiActionParameters uiActionParameters(String pageName, String actionName, boolean invalid) {
    String key = toPropertyKey(pageName);
    String prefix = invalid ? "test.invalid-" + key : "test." + key;
    String lower = actionName.toLowerCase(Locale.ROOT);
    if (lower.contains("login") || lower.contains("credential") || lower.contains("sign in")) {
      String userPrefix = invalid ? "test.invalid-user" : "test.user";
      return new UiActionParameters(List.of("username", "password"),
          List.of("properties(" + userPrefix + ".username)", "properties(" + userPrefix + ".password)"));
    }
    if (lower.contains("register") || lower.contains("sign up")) {
      String userPrefix = invalid ? "test.invalid-user" : "test.user";
      return new UiActionParameters(List.of("email", "password", "name"),
          List.of("properties(" + userPrefix + ".email)", "properties(" + userPrefix + ".password)",
              "properties(" + userPrefix + ".name)"));
    }
    if (lower.contains("search")) {
      return new UiActionParameters(List.of("query"),
          List.of("properties(" + prefix + ".query)"));
    }
    if (lower.contains("add") && (lower.contains("cart") || lower.contains("basket"))) {
      // Parameterless action — empty table (with parameter + |key|value| is still required)
      return new UiActionParameters(List.of(), List.of());
    }
    if (lower.contains("checkout") || lower.contains("fill")) {
      return new UiActionParameters(List.of("firstName", "lastName", "postalCode"),
          List.of("properties(" + prefix + ".first-name)", "properties(" + prefix + ".last-name)",
              "properties(" + prefix + ".postal-code)"));
    }
    if (lower.contains("filter") || lower.contains("sort")) {
      return new UiActionParameters(List.of("option"),
          List.of("properties(" + prefix + ".sort-option)"));
    }
    return new UiActionParameters(List.of("value"),
        List.of("properties(" + prefix + ".value)"));
  }

  private String toPropertyKey(String value) {
    return value.toLowerCase(Locale.ROOT)
        .replaceAll("[^a-z0-9]+", "-")
        .replaceAll("^-|-$", "");
  }

  private record UiActionParameters(List<String> columns, List<String> values) {
    String value(String column) {
      int index = columns.indexOf(column);
      if (index < 0) return values.isEmpty() ? "" : values.get(0);
      return values.get(index);
    }

    String toFeatureTable(int spaces) {
      String indent = " ".repeat(spaces);
      StringBuilder table = new StringBuilder(indent).append("|key|value|");
      for (int i = 0; i < columns.size(); i++) {
        table.append("\n")
            .append(indent)
            .append("| ")
            .append(pad(columns.get(i), 10))
            .append(" | ")
            .append(pad(values.get(i), 36))
            .append(" |");
      }
      return table.toString();
    }

    private static String pad(String value, int length) {
      if (value.length() >= length) return value + " ";
      return value + " ".repeat(length - value.length());
    }
  }

  private String inferDomain(String intent) {
    String lower = intent.toLowerCase(Locale.ROOT);
    for (String word : new String[]{"payment","order","user","product","cart","checkout",
        "login","auth","refund","search","catalog","notification","inventory","settlement",
        "transaction","account","customer","report","approval","validation"}) {
      if (lower.contains(word)) return word;
    }
    return intent.replaceAll("[^a-zA-Z0-9]+", "-").toLowerCase(Locale.ROOT)
        .replaceAll("^-|-$", "").substring(0, Math.min(20, intent.length()));
  }

  private String clarificationPrompt(String intent, String slice, boolean explicitDomain,
      TestaraProjectProfile profile) {
    String lower = intent == null ? "" : intent.toLowerCase(Locale.ROOT).strip();

    // 1. Blank or completely generic intent
    if (lower.isBlank() || Set.of("test", "create test", "generate test", "make test",
        "feature", "scenario", "write test", "add test").contains(lower)) {
      return clarification("test_plan_clarity",
          "Intent is too generic — cannot determine what to test.",
          List.of(
              "What slice? (api | ui | sql | mongo | kafka | elastic)",
              "What domain or feature area? (e.g. order, login, payment, cart)",
              "What action should be tested? (e.g. create order, login with credentials, add item to cart)",
              "What is the expected outcome? (e.g. HTTP 200, page redirects to inventory, record saved)"
          ), null);
    }

    // 2. UI needs page + action context
    if ("ui".equals(slice) && needsUiClarification(lower, explicitDomain, profile)) {
      boolean hasPage   = containsAny(lower, "login", "checkout", "cart", "search", "inventory",
          "home", "dashboard", "profile", "page");
      boolean hasAction = containsAny(lower, "login", "submit", "click", "add", "remove",
          "search", "fill", "open", "navigate", "checkout", "do", "perform");
      List<String> questions = new ArrayList<>();
      if (!hasPage)   questions.add("What page(s) are involved? Use exact names matching your Page classes (e.g. login, inventory, cart).");
      if (!hasAction) questions.add("What user action(s) should be performed? Use exact names matching your UserAction classes (e.g. 'login with credentials', 'add item to cart').");
      questions.add("What is the expected outcome? (e.g. 'user lands on inventory page', 'error message is visible')");
      questions.add("Do the required Page and UserAction classes already exist? If not, call testara_ui first to generate them.");
      return clarification("test_plan_ui_context",
          "UI feature cannot be generated without knowing the page, action, and expected outcome.",
          questions, availableUiContext(profile));
    }

    // 3. API needs service alias + method/endpoint
    if ("api".equals(slice) && needsApiClarification(lower, explicitDomain)) {
      return clarification("test_plan_api_context",
          "API feature cannot be generated without knowing the service and endpoint.",
          List.of(
              "What is the service alias? (must match api.service.{alias} in configuration.properties)",
              "What is the HTTP method? (GET | POST | PUT | PATCH | DELETE)",
              "What is the endpoint path? (e.g. /orders/{id}, /users)",
              "What response is expected? (HTTP status, specific field, success/failure)"
          ), availableApiContext(profile));
    }

    // 4. DB/Streaming/Elastic — need service alias + operation
    if (containsAny(slice != null ? slice : "", "sql", "mongo", "kafka", "streaming", "elastic")
        && needsDbClarification(lower, explicitDomain)) {
      return clarification("test_plan_db_context",
          "DB/Streaming feature cannot be generated without a service alias and operation.",
          List.of(
              "What is the service alias? (must match the config prefix: sql.service.{alias}, mongo.service.{alias}, etc.)",
              "What operation should be performed? (e.g. query by id, insert record, publish event)",
              "What is the expected outcome? (e.g. row exists, document found, event consumed)"
          ), null);
    }

    return null;
  }

  private String clarification(String key, String reason, List<String> questions, String available) {
    StringBuilder sb = new StringBuilder();
    sb.append("needs_input: ").append(key).append("\n");
    sb.append("reason: ").append(reason).append("\n");
    sb.append("ask_user:\n");
    questions.forEach(q -> sb.append("  - ").append(q).append("\n"));
    if (available != null && !available.isBlank()) {
      sb.append("available_in_project:\n").append(available);
    }
    sb.append("hint: call testara_plan again with the answers filled in as intent, slice, and domain.");
    return sb.toString();
  }

  private String availableUiContext(TestaraProjectProfile profile) {
    Set<String> uiTags = profile.features().stream()
        .flatMap(f -> f.tags().stream())
        .filter(t -> !Set.of("@ui", "@regression", "@smoke", "@P1", "@P2", "@P3",
            "@positive", "@negative").contains(t))
        .collect(Collectors.toCollection(LinkedHashSet::new));
    if (uiTags.isEmpty()) return null;
    return "  existing_ui_tags: " + String.join(", ", uiTags) + "\n";
  }

  private String availableApiContext(TestaraProjectProfile profile) {
    Set<String> apiTags = profile.features().stream()
        .flatMap(f -> f.tags().stream())
        .filter(t -> !Set.of("@api", "@regression", "@smoke", "@P1", "@P2", "@P3",
            "@positive", "@negative").contains(t))
        .collect(Collectors.toCollection(LinkedHashSet::new));
    if (apiTags.isEmpty()) return null;
    return "  existing_api_tags: " + String.join(", ", apiTags) + "\n";
  }

  private boolean needsUiClarification(String lower, boolean explicitDomain, TestaraProjectProfile profile) {
    boolean hasPage   = containsAny(lower, "login", "checkout", "cart", "search", "inventory",
        "home", "dashboard", "profile", "page");
    boolean hasAction = containsAny(lower, "login", "submit", "click", "add", "remove",
        "search", "fill", "open", "navigate", "checkout", "do", "perform");
    if (explicitDomain) return false;
    boolean hasExistingUi = profile.features().stream().anyMatch(f -> f.tags().contains("@ui"));
    return !(hasPage && hasAction) && !hasExistingUi;
  }

  private boolean needsApiClarification(String lower, boolean explicitDomain) {
    boolean hasMethod = containsAny(lower, "get", "post", "put", "patch", "delete",
        "create", "update", "fetch", "retrieve");
    boolean hasService = lower.contains("/") || lower.contains("endpoint")
        || lower.contains("service") || lower.contains("api") || explicitDomain;
    boolean hasExpectation = containsAny(lower, "200", "201", "400", "404", "500",
        "success", "error", "valid", "invalid", "return", "response");
    return !(hasMethod || hasService) && !hasExpectation;
  }

  private boolean needsDbClarification(String lower, boolean explicitDomain) {
    boolean hasAlias = explicitDomain || lower.contains("db") || lower.contains("database")
        || lower.contains("collection") || lower.contains("topic") || lower.contains("index");
    boolean hasOperation = containsAny(lower, "query", "select", "insert", "update",
        "delete", "find", "publish", "consume", "search", "count");
    return !hasAlias || !hasOperation;
  }

  private boolean containsAny(String text, String... needles) {
    for (String needle : needles) {
      if (text.contains(needle)) return true;
    }
    return false;
  }

  private String extractVerb(String intent) {
    String lower = intent.toLowerCase(Locale.ROOT);
    for (String verb : new String[]{"approve","reject","create","update","delete","get","fetch",
        "submit","cancel","confirm","process","validate","search","refund","pay","transfer"}) {
      if (lower.contains(verb)) return verb;
    }
    return "process";
  }

  private List<String> buildTags(List<String> userTags, String slice, String domain) {
    List<String> t = new ArrayList<>();
    t.add("@" + slice.toLowerCase(Locale.ROOT));
    t.add("@" + domain.toLowerCase(Locale.ROOT));
    t.add("@regression");
    if (userTags != null) userTags.forEach(tag -> t.add(tag.startsWith("@") ? tag : "@" + tag));
    return t;
  }

  private String toFeatureName(String intent) {
    return intent.substring(0, 1).toUpperCase(Locale.ROOT) + intent.substring(1).toLowerCase(Locale.ROOT);
  }
}
