package io.github.ygrip.testara.agent.parser;

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

    for (FeatureChild child : feature.getChildren()) {
      child.getBackground().ifPresent(bg -> backgroundSteps.addAll(toSteps(bg)));
      child.getScenario().ifPresent(scenario ->
          scenarios.add(toScenario(scenario, List.of())));
      child.getRule().ifPresent(rule -> addRuleScenarios(rule, scenarios, backgroundSteps));
    }

    return new FeatureIndex(
        featurePath,
        feature.getName(),
        tags(feature.getTags()),
        List.copyOf(scenarios),
        List.copyOf(backgroundSteps));
  }

  private void addRuleScenarios(Rule rule, List<ScenarioIndex> scenarios,
      List<StepIndex> backgroundSteps) {
    List<String> ruleTags = tags(rule.getTags());
    for (RuleChild child : rule.getChildren()) {
      child.getBackground().ifPresent(bg -> backgroundSteps.addAll(toSteps(bg)));
      child.getScenario().ifPresent(scenario ->
          scenarios.add(toScenario(scenario, ruleTags)));
    }
  }

  private ScenarioIndex toScenario(Scenario scenario, List<String> inheritedRuleTags) {
    Set<String> scenarioTags = new LinkedHashSet<>(inheritedRuleTags);
    scenarioTags.addAll(tags(scenario.getTags()));

    List<ExamplesIndex> examples = scenario.getExamples().stream()
        .map(this::toExamples)
        .toList();

    ScenarioType type = scenario.getKeyword().startsWith("Scenario Outline")
        || scenario.getKeyword().startsWith("Scenario Template")
        ? ScenarioType.SCENARIO_OUTLINE : ScenarioType.SCENARIO;

    return new ScenarioIndex(
        scenario.getName(),
        type,
        List.copyOf(scenarioTags),
        scenario.getSteps().stream().map(this::toStep).toList(),
        examples);
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
