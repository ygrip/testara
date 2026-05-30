package io.github.ygrip.testara.agent.parser;

import io.github.ygrip.testara.agent.index.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Line-based parser for Gherkin .feature files.
 * Handles Feature, Background, Scenario, Scenario Outline, Examples, and tags.
 */
public class FeatureParser {

  private static final Pattern TAG_LINE = Pattern.compile("(@\\w[\\w-]*)");
  private static final Pattern STEP_LINE = Pattern.compile(
      "^\\s*(Given|When|Then|And|But)\\s+(.+)$");
  private static final Pattern EXAMPLES_HEADER = Pattern.compile("^\\s*\\|(.+)\\|\\s*$");

  public FeatureIndex parse(Path featurePath) throws IOException {
    List<String> lines = Files.readAllLines(featurePath, StandardCharsets.UTF_8);

    String featureName = "";
    List<String> featureTags = new ArrayList<>();
    List<ScenarioIndex> scenarios = new ArrayList<>();
    List<StepIndex> backgroundSteps = new ArrayList<>();

    List<String> pendingTags = new ArrayList<>();
    String currentScenarioName = null;
    ScenarioType currentType = null;
    List<StepIndex> currentSteps = new ArrayList<>();
    List<ExamplesIndex> currentExamples = new ArrayList<>();
    boolean inBackground = false;
    boolean inExamples = false;
    boolean inStepTable = false;
    List<List<String>> currentDataTable = new ArrayList<>();
    List<String> exampleHeaders = new ArrayList<>();
    int exampleRowCount = 0;

    for (String raw : lines) {
      String line = raw.strip();

      if (line.isEmpty() || line.startsWith("#")) continue;

      // Tag line
      if (line.startsWith("@")) {
        Matcher m = TAG_LINE.matcher(line);
        while (m.find()) pendingTags.add(m.group(1));
        continue;
      }

      // Feature header
      if (line.startsWith("Feature:")) {
        featureName = line.substring(8).strip();
        featureTags.addAll(pendingTags);
        pendingTags.clear();
        inBackground = false;
        continue;
      }

      // Background
      if (line.startsWith("Background:")) {
        flushScenario(currentScenarioName, currentType, pendingTags, currentSteps,
            currentExamples, scenarios, backgroundSteps, inBackground);
        currentScenarioName = "";
        currentType = ScenarioType.BACKGROUND;
        currentSteps = new ArrayList<>();
        currentExamples = new ArrayList<>();
        inBackground = true;
        inExamples = false;
        currentDataTable = new ArrayList<>();
        inStepTable = false;
        pendingTags = new ArrayList<>();
        continue;
      }

      // Scenario
      if (line.startsWith("Scenario Outline:") || line.startsWith("Scenario Template:")) {
        flushScenario(currentScenarioName, currentType, pendingTags, currentSteps,
            currentExamples, scenarios, backgroundSteps, inBackground);
        currentScenarioName = line.contains(":") ? line.substring(line.indexOf(':') + 1).strip() : "";
        currentType = ScenarioType.SCENARIO_OUTLINE;
        currentSteps = new ArrayList<>();
        currentExamples = new ArrayList<>();
        inBackground = false;
        inExamples = false;
        currentDataTable = new ArrayList<>();
        inStepTable = false;
        pendingTags = new ArrayList<>();
        continue;
      }
      if (line.startsWith("Scenario:") || line.startsWith("Example:")) {
        flushScenario(currentScenarioName, currentType, pendingTags, currentSteps,
            currentExamples, scenarios, backgroundSteps, inBackground);
        currentScenarioName = line.substring(line.indexOf(':') + 1).strip();
        currentType = ScenarioType.SCENARIO;
        currentSteps = new ArrayList<>();
        currentExamples = new ArrayList<>();
        inBackground = false;
        inExamples = false;
        currentDataTable = new ArrayList<>();
        inStepTable = false;
        pendingTags = new ArrayList<>();
        continue;
      }

      // Examples block
      if (line.startsWith("Examples:") || line.startsWith("Scenarios:")) {
        flushStepDataTable(currentSteps, currentDataTable);
        inStepTable = false;
        currentDataTable = new ArrayList<>();
        inExamples = true;
        exampleHeaders = new ArrayList<>();
        exampleRowCount = 0;
        continue;
      }

      // Table rows in examples
      if (inExamples && line.startsWith("|")) {
        Matcher m = EXAMPLES_HEADER.matcher(line);
        if (m.matches()) {
          if (exampleHeaders.isEmpty()) {
            for (String cell : m.group(1).split("\\|")) {
              exampleHeaders.add(cell.strip());
            }
          } else {
            exampleRowCount++;
          }
        }
        continue;
      } else if (inExamples && !line.startsWith("|")) {
        currentExamples.add(new ExamplesIndex(List.copyOf(exampleHeaders), exampleRowCount));
        inExamples = false;
      }

      // Step data table rows (pipe line after a step, not in Examples)
      if (!inExamples && line.startsWith("|")) {
        Matcher m = EXAMPLES_HEADER.matcher(line);
        if (m.matches()) {
          List<String> cells = new ArrayList<>();
          for (String cell : m.group(1).split("\\|")) {
            cells.add(cell.strip());
          }
          currentDataTable.add(List.copyOf(cells));
          inStepTable = true;
          continue;
        }
      }

      // Step lines
      Matcher stepMatcher = STEP_LINE.matcher(line);
      if (stepMatcher.matches()) {
        // Flush any pending data table from previous step
        flushStepDataTable(currentSteps, currentDataTable);
        inStepTable = false;
        currentDataTable = new ArrayList<>();
        currentSteps.add(new StepIndex(stepMatcher.group(1), stepMatcher.group(2)));
        continue;
      }

      // Non-table, non-step line — flush pending step data table
      if (inStepTable) {
        flushStepDataTable(currentSteps, currentDataTable);
        inStepTable = false;
        currentDataTable = new ArrayList<>();
      }
    }

    // flush remaining data table and examples
    flushStepDataTable(currentSteps, currentDataTable);
    if (inExamples && !exampleHeaders.isEmpty()) {
      currentExamples.add(new ExamplesIndex(List.copyOf(exampleHeaders), exampleRowCount));
    }
    flushScenario(currentScenarioName, currentType, pendingTags, currentSteps,
        currentExamples, scenarios, backgroundSteps, inBackground);

    return new FeatureIndex(featurePath, featureName, List.copyOf(featureTags),
        List.copyOf(scenarios), List.copyOf(backgroundSteps));
  }

  private void flushScenario(String name, ScenarioType type, List<String> tags,
      List<StepIndex> steps, List<ExamplesIndex> examples,
      List<ScenarioIndex> scenarios, List<StepIndex> backgroundSteps, boolean inBackground) {
    if (type == null) return;
    if (type == ScenarioType.BACKGROUND) {
      backgroundSteps.addAll(steps);
    } else {
      scenarios.add(new ScenarioIndex(
          name != null ? name : "",
          type,
          List.copyOf(tags),
          List.copyOf(steps),
          List.copyOf(examples)));
    }
  }

  /** If the last step has a pending data table, rebuild the step with the table attached. */
  private void flushStepDataTable(List<StepIndex> steps, List<List<String>> dataTable) {
    if (dataTable.isEmpty() || steps.isEmpty()) return;
    int lastIdx = steps.size() - 1;
    StepIndex last = steps.get(lastIdx);
    steps.set(lastIdx, new StepIndex(last.keyword(), last.text(), List.copyOf(dataTable)));
  }
}
