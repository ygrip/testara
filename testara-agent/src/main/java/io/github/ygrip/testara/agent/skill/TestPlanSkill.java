package io.github.ygrip.testara.agent.skill;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.ygrip.testara.agent.catalog.GenerationGuard;
import io.github.ygrip.testara.agent.catalog.StepLinker;
import io.github.ygrip.testara.agent.flavor.FlavorEntry;
import io.github.ygrip.testara.agent.index.FeatureIndex;
import io.github.ygrip.testara.agent.index.ScenarioIndex;
import io.github.ygrip.testara.agent.index.ScenarioType;
import io.github.ygrip.testara.agent.index.TestaraProjectProfile;
import io.github.ygrip.testara.agent.knowledge.FrameworkKnowledgeStore;
import io.github.ygrip.testara.agent.safety.FeaturePlacementGuard;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Skill: generate a Testara-flavor Cucumber .feature file from user intent.
 *
 * Priority order (from plan):
 *   1. Testara built-in steps (from FlavorCatalog)
 *   2. Project-specific steps (from profile.stepDefinitions)
 *   3. Generate extension artifact (command, validation, request spec)
 *   4. Mark as MISSING only as last resort
 *
 * <p>Steps are rendered from each built-in step's Cucumber Expression with typed values; a feature
 * whose steps do not all link to a step definition is never written. A written plan also writes the
 * request specs it references and merges the service config / test data properties it needs.
 */
public class TestPlanSkill implements AgentSkill<TestPlanSkill.Input, String> {

  private static final Logger LOG = Logger.getLogger(TestPlanSkill.class.getName());
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Pattern ACTION_ANNOTATION =
      Pattern.compile("@Action\\s*\\(\\s*(?:value\\s*=\\s*)?\"([^\"]+)\"");
  private static final Pattern PROPERTY_REFERENCE = Pattern.compile("properties\\(([^)]+)\\)");
  private static final List<String> CONFIGURATION_FILES =
      List.of("src/test/resources/configuration.properties", "configuration.properties");
  private static final List<String> APPLICATION_FILES =
      List.of("src/test/resources/application.properties", "application.properties");

  private static final String API_ACTOR = "[api]";
  private static final String KAFKA_ACTOR = "[kafka]";
  private static final String USING_SERVICE = "{actor} using service with alias {word}";
  private static final String PATH_PARAM = "{actor} prepare pathParam for {word} with value {string}";
  private static final String QUERY_PARAM = "{actor} prepare queryParam {string} with value {string}";
  private static final String PROCESS_REQUEST = "{actor} process request to {string}";
  private static final String STATUS_CODE = "{actor} response statusCode should be {int}";
  private static final String ASSIGN_RESPONSE = "{actor} assign previous response data to {word}";
  private static final String SQL_CONNECT = "{sql} connect to database with name {word}";
  private static final String SQL_QUERY = "{sql} prepare query with value :";
  private static final String SQL_EXECUTE = "{sql} execute database query";
  private static final String SQL_ASSIGN = "{sql} assign previous database response to {word}";
  private static final String MONGO_CONNECT = "{mongo} connect to database with name {word}";
  private static final String MONGO_COLLECTION = "{mongo} select collection with name {word}";
  private static final String MONGO_SELECT = "{mongo} select data with query :";
  private static final String MONGO_ASSIGN = "{mongo} assign previous database response to {word}";
  private static final String KAFKA_START = "{actor} start kafka producer for {word}";
  private static final String KAFKA_SEND = "{actor} send kafka message to topic {string} with key {string} and data {string}";
  private static final String KAFKA_STOP = "{actor} stop kafka producer";
  private static final String ELASTIC_CONNECT = "{elasticsearch} connect to elastic search with name {word}";
  private static final String ELASTIC_SEARCH = "{elasticsearch} assign data {word} from index {word} with query :";
  private static final String ELASTIC_ASSIGN = "{elasticsearch} assign previous elastic search response to {word}";

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
    String domain = input.domain() != null ? sanitizeDomain(input.domain()) : inferDomain(input.intent());
    String clarification = clarificationPrompt(input.intent(), slice, input.domain() != null, profile);
    if (clarification != null) return clarification;
    List<String> tags = buildTags(input.tags(), slice, domain);
    boolean write = context.allowsWrite()
        && ("true".equals(context.options().get("write")) || input.createFiles());
    boolean concise = "concise".equals(context.options().get("format"));

    // Use project-level catalog if available, otherwise fall back to bundled framework catalog
    List<FlavorEntry> flavorSteps = profile.flavorStepsForSlice(slice);
    if (flavorSteps.isEmpty()) {
      flavorSteps = FrameworkKnowledgeStore.instance().flavorCatalogForSlice(slice);
    }
    List<FlavorEntry> linkCatalog = linkCatalog(profile);
    ApiFlow apiFlow = apiFlow(input.intent(), domain);
    String featureContent = generateFlavorFeature(input.intent(), domain, tags, slice, flavorSteps, apiFlow);
    String placement = resolvePlacement(slice, domain);
    String fileName  = toFileName(input.intent());

    var stepLinks = StepLinker.linkFeature(featureContent, linkCatalog, profile.stepDefinitions());
    int builtInCount = (int) stepLinks.stream().filter(StepLinker.Link::matched).count();
    int totalStepLines = stepLinks.size();
    int missingCount = countMissing(featureContent);
    int unlinkedCount = totalStepLines - builtInCount;
    int score = totalStepLines > 0 ? (builtInCount * 100 / totalStepLines) : 100;

    String writtenPath = null;
    String writeProblem = null;
    List<String> generatedArtifacts = new ArrayList<>();
    List<String> filesChanged = new ArrayList<>();
    if (write) {
      if (unlinkedCount > 0 || missingCount > 0) {
        writeProblem = "write blocked: " + unlinkedCount + " step(s) do not link to a step definition and "
            + missingCount + " step(s) are MISSING — nothing was written";
      } else {
        try {
          PlanWrite planWrite = writePlan(context, slice, domain, input.intent(), placement + fileName,
              featureContent, apiFlow, profile);
          writtenPath = planWrite.feature().path();
          filesChanged.add(planWrite.feature().status().label() + " " + writtenPath);
          generatedArtifacts.addAll(planWrite.artifacts());
        } catch (IOException e) {
          LOG.warning("Cannot write test plan: " + e.getMessage());
          writeProblem = "write failed: " + e.getMessage();
        }
      }
      if ("ui".equals(slice)) {
        String pageName = inferUiPage(input.intent(), domain);
        String actionName = inferUiAction(input.intent(), domain);
        generatedArtifacts.add("suggested: testara_bootstrap artifact=page pageName=" + pageName);
        generatedArtifacts.add("optional: testara_bootstrap artifact=action pageName=" + pageName
            + " actionName=\"" + actionName + "\" when this flow should be reusable");
      }
    }
    int runtimeScore = computeRuntimeContextScore(featureContent, slice);

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
    String scenarioSymbols = scenarioNames.isEmpty() ? "" : "; scenarios:" + String.join(",", scenarioNames);
    String tagSymbols = featureTags.isEmpty() ? "" : "; tags:" + String.join(" ", featureTags);
    String fileSymbols = " [feature:" + toFeatureName(input.intent()) + scenarioSymbols + tagSymbols + "]";
    var violations = GenerationGuard.validateFeature(featureContent, linkCatalog, profile.stepDefinitions());

    if (concise) {
      StringBuilder sb = new StringBuilder();
      if (write) {
        if (writtenPath != null) sb.append("written: ").append(writtenPath);
        else sb.append(writeProblem);
        generatedArtifacts.forEach(a -> sb.append("\ngenerated: ").append(a));
        if (writtenPath != null) {
          sb.append("\nfilesChanged:");
          filesChanged.forEach(f -> sb.append("\n- ").append(f).append(fileSymbols));
          String compile = ArtifactFiles.compileLine(context);
          if (compile != null) sb.append("\n").append(compile);
        }
        sb.append("\n\n");
      }
      sb.append(featureContent);
      if (missingCount > 0) sb.append("\nmissing: ").append(missingCount).append(" steps need implementation");
      sb.append("\nflavor-score: ").append(score).append("% | runtime-context-score: ").append(runtimeScore).append("%");
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
      if (writtenPath != null) sb.append("> Written to `").append(writtenPath).append("`\n");
      else sb.append("> Warning: ").append(writeProblem).append("\n");
      generatedArtifacts.forEach(a -> sb.append("> Generated: `").append(a).append("`\n"));
      if (writtenPath != null) {
        filesChanged.forEach(f -> sb.append("> filesChanged: ").append(f).append(fileSymbols).append("\n"));
        String compile = ArtifactFiles.compileLine(context);
        if (compile != null) sb.append("> ").append(compile).append("\n");
      }
      sb.append("\n");
    }
    sb.append("```gherkin\n").append(featureContent).append("\n```\n\n");
    sb.append("**Testara Flavor Score: ").append(score).append("% | Runtime Context Score: ").append(runtimeScore).append("%**  \n");
    sb.append("Built-in steps: ").append(builtInCount).append("  | Missing: ").append(missingCount).append("\n");
    if (writtenPath != null) {
      sb.append("\nNext: `testara-agent test-run '").append(input.intent()).append("' --execute`\n");
    }
    return GenerationGuard.annotate(sb.toString(), violations);
  }

  /** All indexed glue: Cucumber links a step to any loaded step definition, whatever its slice. */
  private List<FlavorEntry> linkCatalog(TestaraProjectProfile profile) {
    if (!profile.flavorSteps().isEmpty()) return profile.flavorSteps();
    return FrameworkKnowledgeStore.instance().flavorCatalog();
  }

  private record PlanWrite(ArtifactFiles.Written feature, List<String> artifacts) {}

  private PlanWrite writePlan(AgentContext context, String slice, String domain, String intent,
      String featurePath, String featureContent, ApiFlow apiFlow, TestaraProjectProfile profile) throws IOException {
    Path root = context.projectRoot();
    boolean overwrite = ArtifactFiles.overwrite(context);
    List<String> artifacts = new ArrayList<>();
    ArtifactFiles.Written feature = ArtifactFiles.writeFeature(root, featurePath, featureContent + "\n", overwrite);
    placementWarnings(root, feature.path(), featureContent, intent, profile)
        .forEach(warning -> artifacts.add("placement-warning: " + warning));

    StringBuilder references = new StringBuilder(featureContent);
    if ("api".equals(slice)) {
      for (Map.Entry<String, String> spec : requestSpecs(apiFlow).entrySet()) {
        artifacts.add(ArtifactFiles.writeJson(root, spec.getKey(), spec.getValue(), overwrite).line());
        references.append("\n").append(spec.getValue());
      }
    }
    String serviceConfig = serviceConfigBlock(slice, domain);
    if (serviceConfig != null) {
      artifacts.add(ArtifactFiles.mergeProperties(root, CONFIGURATION_FILES, serviceConfig).line());
    }
    if ("api".equals(slice)) {
      String scriptFolderWarning = ArtifactFiles.scriptFolderWarning(root);
      if (scriptFolderWarning != null) artifacts.add(scriptFolderWarning);
    }
    String applicationValues = applicationValuesBlock(slice, domain, intent, apiFlow, references.toString());
    if (!applicationValues.isBlank()) {
      artifacts.add(ArtifactFiles.mergeProperties(root, APPLICATION_FILES, applicationValues).line());
    }
    return new PlanWrite(feature, List.copyOf(artifacts));
  }

  /** Scenario names that collide with other features in the target directory (never blocks the write). */
  private List<String> placementWarnings(Path root, String featurePath, String featureContent, String intent,
      TestaraProjectProfile profile) {
    Path target = root.resolve(featurePath).toAbsolutePath().normalize();
    List<FeatureIndex> siblings = profile.features().stream()
        .filter(f -> {
          Path path = root.resolve(f.path()).toAbsolutePath().normalize();
          return !path.equals(target) && target.getParent().equals(path.getParent());
        })
        .toList();
    List<ScenarioIndex> scenarios = Arrays.stream(featureContent.split("\n"))
        .map(String::strip)
        .filter(line -> line.startsWith("Scenario:"))
        .map(line -> new ScenarioIndex(line.substring("Scenario:".length()).strip(), ScenarioType.SCENARIO,
            List.of(), List.of(), List.of()))
        .toList();
    FeatureIndex generated = new FeatureIndex(target, toFeatureName(intent), List.of(), scenarios, List.of());
    return FeaturePlacementGuard.validatePlacement(generated, siblings).errors();
  }

  private String executeBatch(Input input, AgentContext context) {
    List<FeatureBatchSpec> features = parseFeatureBatch(input);
    if (features.isEmpty()) {
      return "needs_input: testara_plan_batch\n"
          + "reason: featureFiles must be a JSON array with featureName/path/scenarios.\n";
    }

    List<String> createdFeatureFiles = new ArrayList<>();
    List<String> blockedFeatureFiles = new ArrayList<>();
    List<String> usedActions = new ArrayList<>();
    List<String> unresolvedActions = new ArrayList<>();
    Set<String> tagIndex = new LinkedHashSet<>();
    List<String> fileSummaries = new ArrayList<>();
    StringBuilder preview = new StringBuilder();
    Map<String, String> actionCatalog = actionCatalog(context.projectRoot());
    boolean write = context.allowsWrite()
        && (input.createFiles() || "true".equals(context.options().get("write")));
    boolean uiBackground = input.slice() == null || "ui".equalsIgnoreCase(input.slice());
    List<FlavorEntry> linkCatalog = linkCatalog(context.profile());
    boolean overwrite = ArtifactFiles.overwrite(context);

    for (FeatureBatchSpec feature : features) {
      String featureText = buildBatchFeature(feature, actionCatalog, usedActions, unresolvedActions, tagIndex,
          uiBackground);
      preview.append("\n--- ").append(feature.path()).append(" ---\n").append(featureText).append("\n");
      List<String> unlinked = StepLinker.linkFeature(featureText, linkCatalog, context.profile().stepDefinitions())
          .stream()
          .filter(link -> !link.matched())
          .map(link -> "line " + link.lineNumber() + ": " + link.stepLine())
          .toList();
      int missing = countMissing(featureText);
      if (!unlinked.isEmpty() || missing > 0) {
        blockedFeatureFiles.add(feature.path() + " (" + unlinked.size() + " unlinked, " + missing + " missing"
            + (unlinked.isEmpty() ? "" : ": " + String.join("; ", unlinked)) + ")");
        continue;
      }
      if (!write) continue;
      try {
        ArtifactFiles.Written written = ArtifactFiles.writeFeature(context.projectRoot(), feature.path(),
            featureText + "\n", overwrite);
        if (written.changed()) createdFeatureFiles.add(written.path());
        String scenarioNames = feature.scenarios().stream()
            .map(ScenarioBatchSpec::name)
            .collect(Collectors.joining(","));
        String tags = feature.tags().isEmpty() ? "" : "; tags:" + String.join(" ", feature.tags());
        fileSummaries.add(written.status().label() + " " + written.path() + " [feature:" + feature.featureName()
            + "; scenarios:" + scenarioNames + tags + "]");
      } catch (IOException e) {
        LOG.warning("Cannot write feature file " + feature.path() + ": " + e.getMessage());
        blockedFeatureFiles.add(feature.path() + " (write failed: " + e.getMessage() + ")");
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
    sb.append("blockedFeatureFiles:\n");
    if (blockedFeatureFiles.isEmpty()) sb.append("- none\n");
    else blockedFeatureFiles.forEach(path -> sb.append("- ").append(path).append("\n"));
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
      LOG.warning("featureFiles is not valid JSON: " + e.getMessage());
      return List.of();
    }
  }

  private String buildBatchFeature(FeatureBatchSpec feature, Map<String, String> actionCatalog,
      List<String> usedActions, List<String> unresolvedActions, Set<String> tagIndex, boolean uiBackground) {
    StringBuilder sb = new StringBuilder();
    sb.append("# Generated by Testara Agent — review before committing.\n\n");
    appendTags(sb, feature.tags(), tagIndex, false);
    sb.append("Feature: ").append(feature.featureName()).append("\n\n");
    if (uiBackground) {
      sb.append("  Background:\n");
      sb.append("    Given user using chrome in desktop\n\n");
    }
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
              Matcher matcher = ACTION_ANNOTATION.matcher(Files.readString(path, StandardCharsets.UTF_8));
              while (matcher.find()) {
                String action = matcher.group(1);
                actions.put(action.toLowerCase(Locale.ROOT), action);
              }
            } catch (IOException e) {
              LOG.warning("Cannot read " + path + " for @Action names: " + e.getMessage());
            }
          });
    } catch (IOException e) {
      LOG.warning("Cannot scan " + root + " for @Action names: " + e.getMessage());
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
      String slice, List<FlavorEntry> flavorSteps, ApiFlow apiFlow) {
    StringBuilder sb = new StringBuilder();
    sb.append("# Generated by Testara Agent — review before committing.\n\n");
    sb.append(String.join(" ", tags)).append("\n");
    sb.append("Feature: ").append(toFeatureName(intent)).append("\n\n");

    if ("ui".equals(slice)) {
      sb.append(buildStandardUiFeature(intent, domain));
      return sb.toString();
    }

    if ("api".equals(slice)) {
      StringBuilder background = new StringBuilder();
      appendStep(background, "Given", flavorSteps, USING_SERVICE, API_ACTOR, apiFlow.alias());
      sb.append("  Background:\n").append(background).append("\n");
    }

    // Positive scenario
    sb.append("  @P1 @positive\n");
    sb.append("  Scenario: ").append(toFeatureName(intent)).append(" — happy path\n");
    sb.append(buildScenarioSteps(intent, domain, slice, flavorSteps, apiFlow, false));
    sb.append("\n");

    // Negative scenario
    sb.append("  @P2 @negative\n");
    sb.append("  Scenario: ").append(toFeatureName(intent)).append(" — failure case\n");
    sb.append(buildScenarioSteps(intent, domain, slice, flavorSteps, apiFlow, true));

    return sb.toString();
  }

  private String buildScenarioSteps(String intent, String domain, String slice,
      List<FlavorEntry> flavorSteps, ApiFlow apiFlow, boolean negative) {
    return switch (slice) {
      case "api" -> buildApiSteps(apiFlow, flavorSteps, negative);
      case "sql", "database" -> buildSqlSteps(domain, flavorSteps, negative);
      case "mongo" -> buildMongoSteps(domain, flavorSteps, negative);
      case "kafka", "streaming" -> buildKafkaSteps(domain, flavorSteps, negative);
      case "elastic" -> buildElasticSteps(domain, flavorSteps, negative);
      default    -> buildGenericSteps(intent, domain, negative);
    };
  }

  /**
   * Appends one built-in step rendered with typed values. When the step is not indexed, or a value
   * does not fit its parameter type, a {@code # MISSING} comment line is emitted instead so the plan
   * stays valid Gherkin and the write is blocked.
   */
  private void appendStep(StringBuilder sb, String keyword, List<FlavorEntry> catalog, String expression,
      String actor, String... args) {
    Optional<FlavorEntry> entry = FlavorStepFiller.find(catalog, expression);
    if (entry.isEmpty()) {
      sb.append("    # MISSING: no indexed step definition for \"").append(expression).append("\"\n");
      return;
    }
    try {
      String text = FlavorStepFiller.fill(entry.get(), actor, args);
      sb.append("    ").append(keyword).append(" ").append(text).append("\n");
    } catch (IllegalArgumentException e) {
      sb.append("    # MISSING: ").append(e.getMessage()).append("\n");
    }
  }

  // ── API scenario steps ────────────────────────────────────────────────────

  /**
   * One API flow derived from the intent: the service alias, HTTP method and the request spec(s)
   * the feature references. Payload methods get a second spec with an invalid payload for the
   * failure case, because a spec's payload replaces any body prepared by an earlier step.
   */
  private record ApiFlow(String domain, String alias, String method, String flow, boolean usesId, boolean usesQuery) {
    boolean hasPayload() { return List.of("POST", "PUT", "PATCH").contains(method); }
    String specPath() { return "files/" + domain + "/request/" + flow; }
    String invalidSpecPath() { return specPath() + "-invalid"; }
    int failureStatus() { return hasPayload() ? 400 : 404; }
    String endpoint() { return usesId ? "/" + domain + "/{id}" : "/" + domain; }
  }

  private ApiFlow apiFlow(String intent, String domain) {
    String verb = extractVerb(intent);
    String method = switch (verb) {
      case "get", "fetch", "search" -> "GET";
      case "update" -> "PUT";
      case "delete" -> "DELETE";
      default -> "POST";
    };
    boolean usesQuery = "search".equals(verb);
    boolean usesId = !"POST".equals(method) && !usesQuery;
    return new ApiFlow(domain, PropertyKeys.apiAlias(domain), method, verb + "-" + domain, usesId, usesQuery);
  }

  private String buildApiSteps(ApiFlow flow, List<FlavorEntry> flavorSteps, boolean negative) {
    StringBuilder sb = new StringBuilder();
    String domain = flow.domain();
    if (flow.usesId()) {
      String idField = negative && !flow.hasPayload() ? "invalid-id" : "id";
      appendStep(sb, "Given", flavorSteps, PATH_PARAM, API_ACTOR, "id",
          propertiesOf(PropertyKeys.testData(domain, idField)));
    }
    if (flow.usesQuery()) {
      String queryField = negative ? "invalid-query" : "query";
      appendStep(sb, "Given", flavorSteps, QUERY_PARAM, API_ACTOR, "query",
          propertiesOf(PropertyKeys.testData(domain, queryField)));
    }
    String spec = negative && flow.hasPayload() ? flow.invalidSpecPath() : flow.specPath();
    appendStep(sb, "When", flavorSteps, PROCESS_REQUEST, API_ACTOR, spec);
    if (negative) {
      appendStep(sb, "Then", flavorSteps, STATUS_CODE, API_ACTOR, String.valueOf(flow.failureStatus()));
    } else {
      appendStep(sb, "Then", flavorSteps, STATUS_CODE, API_ACTOR, "200");
      appendStep(sb, "Then", flavorSteps, ASSIGN_RESPONSE, API_ACTOR, camel(domain) + "Response");
    }
    return sb.toString();
  }

  /** Request spec JSON files (relative path → content) the API feature references. */
  private Map<String, String> requestSpecs(ApiFlow flow) {
    Map<String, String> specs = new LinkedHashMap<>();
    String base = "src/test/resources/" + flow.specPath();
    specs.put(base + ".json", requestSpec(flow, "{\"field\": \"" + propertiesOf(PropertyKeys.testData(flow.domain(), "field")) + "\"}"));
    if (flow.hasPayload()) {
      specs.put(base + "-invalid.json", requestSpec(flow, "{\"field\": null}"));
    }
    return specs;
  }

  private String requestSpec(ApiFlow flow, String payload) {
    StringBuilder json = new StringBuilder();
    json.append("{\n");
    json.append("  \"specification\": \"").append(flow.alias()).append("\",\n");
    json.append("  \"httpMethod\": \"").append(flow.method()).append("\",\n");
    json.append("  \"url\": \"").append(propertiesOf(PropertyKeys.apiEndpoint(flow.domain()))).append("\",\n");
    json.append("  \"contentType\": \"application/json\"");
    if (flow.hasPayload()) json.append(",\n  \"payload\": ").append(payload);
    json.append("\n}\n");
    return json.toString();
  }

  // ── UI scenario steps ─────────────────────────────────────────────────────

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

  private String uiBaseActionSteps(String pageName, String actionName, boolean invalid) {
    UiActionParameters params = uiActionParameters(pageName, actionName, invalid);
    // RULE 3: 3+ operations on same page → user do "action" in "page" page with parameter
    // Always generate the UserAction step with | key | value | DataTable — never individual type/click steps
    return "    When user do \"" + actionName + "\" in \"" + pageName + "\" page with parameter\n"
        + params.toFeatureTable(6) + "\n";
  }

  // ── SQL scenario steps ────────────────────────────────────────────────────

  private String buildSqlSteps(String domain, List<FlavorEntry> flavorSteps, boolean negative) {
    StringBuilder sb = new StringBuilder();
    String idField = negative ? "invalid-id" : "id";
    appendStep(sb, "Given", flavorSteps, SQL_CONNECT, null, PropertyKeys.databaseAlias(domain));
    appendStep(sb, "Given", flavorSteps, SQL_QUERY, null);
    sb.append("      \"\"\"\n");
    sb.append("      select *\n      from ").append(domain).append("\n");
    // Use properties() for test data values — IDs are test-specific
    sb.append("      where id = '").append(propertiesOf(PropertyKeys.testData(domain, idField))).append("'\n");
    sb.append("      \"\"\"\n");
    appendStep(sb, "When", flavorSteps, SQL_EXECUTE, null);
    appendStep(sb, "Then", flavorSteps, SQL_ASSIGN, null, camel(domain) + "Rows");
    return sb.toString();
  }

  // ── Mongo scenario steps ──────────────────────────────────────────────────

  private String buildMongoSteps(String domain, List<FlavorEntry> flavorSteps, boolean negative) {
    StringBuilder sb = new StringBuilder();
    String idField = negative ? "invalid-id" : "id";
    appendStep(sb, "Given", flavorSteps, MONGO_CONNECT, null, PropertyKeys.databaseAlias(domain));
    appendStep(sb, "Given", flavorSteps, MONGO_COLLECTION, null, domain);
    appendStep(sb, "When", flavorSteps, MONGO_SELECT, null);
    // The query DataTable is read as a map, so it needs the | key | value | header row.
    sb.append("      | key   | value |\n");
    sb.append("      | query | {\"_id\":\"").append(propertiesOf(PropertyKeys.testData(domain, idField))).append("\"} |\n");
    sb.append("      | limit | 1 |\n");
    appendStep(sb, "Then", flavorSteps, MONGO_ASSIGN, null, camel(domain) + "Rows");
    return sb.toString();
  }

  // ── Kafka scenario steps ──────────────────────────────────────────────────

  private String buildKafkaSteps(String domain, List<FlavorEntry> flavorSteps, boolean negative) {
    StringBuilder sb = new StringBuilder();
    String payloadField = negative ? "invalid-payload" : "payload";
    appendStep(sb, "Given", flavorSteps, KAFKA_START, KAFKA_ACTOR, PropertyKeys.kafkaAlias(domain));
    appendStep(sb, "When", flavorSteps, KAFKA_SEND, KAFKA_ACTOR, PropertyKeys.kafkaTopicAlias(domain),
        propertiesOf(PropertyKeys.testData(domain, "id")), propertiesOf(PropertyKeys.testData(domain, payloadField)));
    appendStep(sb, "Then", flavorSteps, KAFKA_STOP, KAFKA_ACTOR);
    return sb.toString();
  }

  // ── Elastic scenario steps ────────────────────────────────────────────────

  private String buildElasticSteps(String domain, List<FlavorEntry> flavorSteps, boolean negative) {
    StringBuilder sb = new StringBuilder();
    String idField = negative ? "invalid-id" : "id";
    String alias = camel(domain) + "Results";
    appendStep(sb, "Given", flavorSteps, ELASTIC_CONNECT, null, domain);
    appendStep(sb, "When", flavorSteps, ELASTIC_SEARCH, null, alias, domain);
    // The query DataTable is read as a map, so it needs the | key | value | header row.
    sb.append("      | key         | value |\n");
    sb.append("      | luceneQuery | {\"term\":{\"id\":\"").append(propertiesOf(PropertyKeys.testData(domain, idField)))
        .append("\"}} |\n");
    sb.append("      | size        | 10 |\n");
    appendStep(sb, "Then", flavorSteps, ELASTIC_ASSIGN, null, alias);
    return sb.toString();
  }

  // ── Generic fallback steps ────────────────────────────────────────────────

  private String buildGenericSteps(String intent, String domain, boolean negative) {
    String verb = extractVerb(intent);
    StringBuilder sb = new StringBuilder();
    // Comments on their own line: an inline "# MISSING" would become part of the step text.
    sb.append("    # MISSING: no Testara built-in step prepares ").append(domain).append("\n");
    sb.append("    # MISSING: no Testara built-in step performs the ").append(verb).append(" operation on ")
        .append(domain).append("\n");
    sb.append("    # MISSING: no Testara built-in step asserts ").append(negative ? "an error" : "success").append("\n");
    return sb.toString();
  }

  // ── Properties the plan needs ─────────────────────────────────────────────

  /** Service config the plan's steps connect to (written to configuration.properties). */
  private String serviceConfigBlock(String slice, String domain) {
    return switch (slice) {
      case "api" -> TestaraApiSkill.serviceConfigBlock(domain);
      case "sql", "database" -> TestaraDbSkill.sqlConfigBlock(domain);
      case "mongo" -> TestaraDbSkill.mongoConfigBlock(domain);
      case "kafka", "streaming" -> TestaraDbSkill.kafkaConfigBlock(domain);
      case "elastic" -> TestaraDbSkill.elasticConfigBlock(domain);
      default -> null;
    };
  }

  /**
   * Application values (application.properties): known defaults for the slice plus an env
   * placeholder for every other {@code properties(key)} the feature or its request specs reference.
   */
  private String applicationValuesBlock(String slice, String domain, String intent, ApiFlow apiFlow,
      String references) {
    Map<String, String> values = new LinkedHashMap<>();
    if ("api".equals(slice)) {
      values.put(PropertyKeys.apiEndpoint(domain), apiFlow.endpoint());
      values.put(PropertyKeys.testData(domain, "field"), "sample-value");
      values.put(PropertyKeys.testData(domain, "query"), "sample");
      values.put(PropertyKeys.testData(domain, "invalid-query"), "invalid");
    }
    if ("ui".equals(slice)) {
      String pageName = toPropertyKey(inferUiPage(intent, domain));
      String successPage = toPropertyKey(inferUiSuccessPage(intent, domain, pageName));
      for (String page : new LinkedHashSet<>(List.of(pageName, successPage))) {
        TestaraUiSkill.pageUrlEntries(page).forEach(entry -> {
          int separator = entry.indexOf('=');
          values.put(entry.substring(0, separator), entry.substring(separator + 1));
        });
      }
    }
    values.put(PropertyKeys.testData(domain, "id"), "00000000-0000-0000-0000-000000000001");
    values.put(PropertyKeys.testData(domain, "invalid-id"), "00000000-0000-0000-0000-000000000000");
    values.put(PropertyKeys.testData(domain, "payload"), "{\"id\":\"00000000-0000-0000-0000-000000000001\"}");
    values.put(PropertyKeys.testData(domain, "invalid-payload"), "{\"id\":null}");

    Set<String> referenced = new LinkedHashSet<>();
    Matcher matcher = PROPERTY_REFERENCE.matcher(references);
    while (matcher.find()) referenced.add(matcher.group(1).strip());

    StringBuilder block = new StringBuilder();
    for (String key : referenced) {
      String value = values.get(key);
      if (value == null) value = PropertyKeys.envValue(key, "");
      block.append(key).append("=").append(value).append("\n");
    }
    if ("ui".equals(slice)) {
      values.entrySet().stream()
          .filter(entry -> entry.getKey().startsWith("web.page."))
          .forEach(entry -> block.append(entry.getKey()).append("=").append(entry.getValue()).append("\n"));
    }
    if (block.isEmpty()) return "";
    return "# Values referenced by the " + domain + " " + slice + " test plan\n" + block;
  }

  private static String propertiesOf(String key) {
    return "properties(" + key + ")";
  }

  /** Lower camel case of a domain slug, for {word} data aliases such as {@code orderResponse}. */
  private static String camel(String value) {
    String[] parts = value.split("[^A-Za-z0-9]+");
    StringBuilder sb = new StringBuilder();
    for (String part : parts) {
      if (part.isEmpty()) continue;
      if (sb.isEmpty()) {
        sb.append(Character.toLowerCase(part.charAt(0))).append(part.substring(1));
      } else {
        sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
      }
    }
    if (sb.isEmpty()) return "generated";
    return sb.toString();
  }

  // ── Utilities ─────────────────────────────────────────────────────────────

  /** Runtime Context Score: measures correct use of properties(), request specs, UserAction. */
  private int computeRuntimeContextScore(String feature, String slice) {
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
    if (slug.isEmpty()) slug = "generated-feature";
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
    String slug = intent.replaceAll("[^a-zA-Z0-9]+", "-").toLowerCase(Locale.ROOT)
        .replaceAll("^-|-$", "");
    // Truncate on the slug's own length — the slug is shorter than the intent once punctuation is removed.
    slug = slug.substring(0, Math.min(20, slug.length())).replaceAll("-$", "");
    if (slug.isEmpty()) return "generated";
    return slug;
  }

  /** Keeps a caller-supplied domain usable as a tag, path segment and {word} step value. */
  private String sanitizeDomain(String domain) {
    String slug = domain.strip().replaceAll("[^A-Za-z0-9_]+", "-").replaceAll("^-|-$", "");
    if (slug.isEmpty()) return "generated";
    return slug;
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
