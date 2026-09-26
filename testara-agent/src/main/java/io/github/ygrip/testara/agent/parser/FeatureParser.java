package io.github.ygrip.testara.agent.parser;

import io.cucumber.gherkin.GherkinDialectProvider;
import io.cucumber.gherkin.GherkinParser;
import io.cucumber.messages.types.Background;
import io.cucumber.messages.types.DataTable;
import io.cucumber.messages.types.Envelope;
import io.cucumber.messages.types.Examples;
import io.cucumber.messages.types.Feature;
import io.cucumber.messages.types.FeatureChild;
import io.cucumber.messages.types.Rule;
import io.cucumber.messages.types.RuleChild;
import io.cucumber.messages.types.Scenario;
import io.cucumber.messages.types.Step;
import io.cucumber.messages.types.TableRow;
import io.cucumber.messages.types.Tag;
import io.github.ygrip.testara.agent.index.ExamplesIndex;
import io.github.ygrip.testara.agent.index.FeatureIndex;
import io.github.ygrip.testara.agent.index.ScenarioIndex;
import io.github.ygrip.testara.agent.index.ScenarioType;
import io.github.ygrip.testara.agent.index.StepIndex;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Parses Gherkin feature files through Cucumber's parser and adapts the AST
 * into Testara's lightweight index model.
 */
public class FeatureParser {

  private static final GherkinDialectProvider DIALECTS = new GherkinDialectProvider();

  private final GherkinParser parser = GherkinParser.builder()
      .includeSource(false)
      .includePickles(false)
      .build();

  public FeatureIndex parse(Path featurePath) throws IOException {
    List<Envelope> envelopes;
    try (var stream = parser.parse(featurePath)) {
      envelopes = stream.toList();
    }

    var parseError = envelopes.stream()
        .flatMap(e -> e.getParseError().stream())
        .findFirst();
    if (parseError.isPresent()) {
      throw new IOException("Invalid Gherkin in " + featurePath + ": " + parseError.get().getMessage());
    }

    Feature feature = envelopes.stream()
        .flatMap(e -> e.getGherkinDocument().stream())
        .flatMap(d -> d.getFeature().stream())
        .findFirst()
        .orElseThrow(() -> new IOException("No Feature found in " + featurePath));

    List<ScenarioIndex> scenarios = new ArrayList<>();
    List<StepIndex> backgroundSteps = new ArrayList<>();
    List<String> outlineKeywords = outlineKeywords(feature.getLanguage());

    for (FeatureChild child : feature.getChildren()) {
      child.getBackground().ifPresent(bg -> backgroundSteps.addAll(toSteps(bg)));
      child.getScenario().ifPresent(scenario ->
          scenarios.add(toScenario(scenario, List.of(), List.of(), outlineKeywords)));
      child.getRule().ifPresent(rule -> addRuleScenarios(rule, scenarios, outlineKeywords));
    }

    return new FeatureIndex(
        featurePath,
        feature.getName(),
        tags(feature.getTags()),
        List.copyOf(scenarios),
        List.copyOf(backgroundSteps));
  }

  /** A Rule's Background applies only to that Rule's scenarios, so it stays on each of them. */
  private void addRuleScenarios(Rule rule, List<ScenarioIndex> scenarios, List<String> outlineKeywords) {
    List<String> ruleTags = tags(rule.getTags());
    List<StepIndex> ruleBackground = new ArrayList<>();
    for (RuleChild child : rule.getChildren()) {
      child.getBackground().ifPresent(bg -> ruleBackground.addAll(toSteps(bg)));
      child.getScenario().ifPresent(scenario ->
          scenarios.add(toScenario(scenario, ruleTags, List.copyOf(ruleBackground), outlineKeywords)));
    }
  }

  private ScenarioIndex toScenario(Scenario scenario, List<String> inheritedRuleTags,
      List<StepIndex> ruleBackgroundSteps, List<String> outlineKeywords) {
    Set<String> scenarioTags = new LinkedHashSet<>(inheritedRuleTags);
    scenarioTags.addAll(tags(scenario.getTags()));

    List<ExamplesIndex> examples = scenario.getExamples().stream()
        .map(this::toExamples)
        .toList();

    // Examples make any scenario an outline; the dialect keyword covers outlines without Examples.
    ScenarioType type = ScenarioType.SCENARIO;
    if (!examples.isEmpty() || outlineKeywords.contains(scenario.getKeyword())) {
      type = ScenarioType.SCENARIO_OUTLINE;
    }

    return new ScenarioIndex(
        scenario.getName(),
        type,
        List.copyOf(scenarioTags),
        scenario.getSteps().stream().map(this::toStep).toList(),
        examples,
        ruleBackgroundSteps);
  }

  private List<String> outlineKeywords(String language) {
    return DIALECTS.getDialect(language)
        .orElseGet(DIALECTS::getDefaultDialect)
        .getScenarioOutlineKeywords();
  }

  private ExamplesIndex toExamples(Examples examples) {
    List<String> headers = examples.getTableHeader()
        .map(this::cells)
        .orElse(List.of());
    return new ExamplesIndex(
        tags(examples.getTags()),
        headers,
        examples.getTableBody().size());
  }

  private List<StepIndex> toSteps(Background background) {
    return background.getSteps().stream().map(this::toStep).toList();
  }

  private StepIndex toStep(Step step) {
    List<List<String>> dataTable = step.getDataTable()
        .map(DataTable::getRows)
        .orElse(List.of())
        .stream()
        .map(this::cells)
        .toList();
    return new StepIndex(step.getKeyword().strip(), step.getText(), dataTable);
  }

  private List<String> cells(TableRow row) {
    return row.getCells().stream().map(cell -> cell.getValue()).toList();
  }

  private List<String> tags(List<Tag> tags) {
    return tags.stream().map(Tag::getName).distinct().toList();
  }
}
