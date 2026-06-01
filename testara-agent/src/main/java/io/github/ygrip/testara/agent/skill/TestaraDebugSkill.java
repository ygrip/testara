package io.github.ygrip.testara.agent.skill;

import io.github.ygrip.testara.agent.catalog.StepLinker;
import io.github.ygrip.testara.agent.flavor.FlavorEntry;
import io.github.ygrip.testara.agent.index.StepDefinitionIndex;
import io.github.ygrip.testara.agent.knowledge.FrameworkKnowledgeStore;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Concise root-cause helper for failed Testara step/report snippets. */
public class TestaraDebugSkill implements AgentSkill<TestaraDebugSkill.Input, String> {

  public record Input(String snippet, String failedStep) {}

  @Override
  public String name() { return "testara-debug"; }

  @Override
  public String execute(Input input, AgentContext context) {
    String snippet = input.snippet() == null ? "" : input.snippet();
    String failedStep = input.failedStep() != null ? input.failedStep() : extractStep(snippet);
    String lower = (snippet + "\n" + failedStep).toLowerCase(Locale.ROOT);
    StringBuilder sb = new StringBuilder("debug_result:\n");

    if (failedStep != null && !failedStep.isBlank()) {
      sb.append("failed_step: ").append(failedStep.strip()).append("\n");
      StepLinker.Link link = linkStep(failedStep, context);
      sb.append("step_link: ").append(link.matched() ? link.source() + " " + link.owner() : "unmatched").append("\n");
      if (!link.matched()) {
        sb.append("likely_cause: unmatched_step_text\n");
        sb.append("why: the step text does not match indexed Testara built-ins or project glue.\n");
        sb.append("next: call testara_guide section=steps, rewrite the step to an exact built-in pattern, or create a project step/UserAction.\n");
        return sb.toString();
      }
    }

    if (containsAny(lower, "duplicate engine", "testengine", "unsupportedclassversionerror", "java 21",
        "testara-reporter-plugin")) {
      sb.append("likely_cause: runner_or_java_configuration\n");
      sb.append("why: the snippet points to engine selection or Maven JVM/runtime mismatch.\n");
      sb.append("next: use generated Junit4RunnerTests/Junit5RunnerTests, keep surefire skipped, run mvn verify, and confirm mvn -version uses Java 21+.\n");
    } else if (containsAny(lower, "nosuchelement", "not found", "timeout", "not visible", "locator")) {
      sb.append("likely_cause: selector_or_visibility\n");
      sb.append("why: the step matched glue, but the page element was missing, ambiguous, or not visible at runtime.\n");
      sb.append("next: call testara_ui mode=validate-page with the pageName and an HTML snapshot; verify Locator fields and add WaitUntil/SeeThat only for visible elements.\n");
    } else if (containsAny(lower, "pagecontext", "current page", "session=null", "no page", "is in")) {
      sb.append("likely_cause: page_context_or_session\n");
      sb.append("why: UIBaseSteps require driver setup and active page context.\n");
      sb.append("next: ensure Background has `Given user using chrome in desktop`, pair `When user open \"page\" page` with `Then user is in \"page\" page`, and switch page context after navigation.\n");
    } else if (containsAny(lower, "scan-locations", "classnotfound", "no action", "action not found",
        "validator", "command.executor")) {
      sb.append("likely_cause: scan_location_or_artifact_registration\n");
      sb.append("why: Testara could not discover a project command, validation, page, or UserAction.\n");
      sb.append("next: check class.loader.default-scan-locations plus command/validator/page/action scan-locations include io.github.ygrip.testara and the project base package.\n");
    } else {
      sb.append("likely_cause: needs_more_context\n");
      sb.append("why: no known Testara failure signature was detected.\n");
      sb.append("next: provide failed step text, stack trace lines, target/cucumber.json excerpt, and related Page/UserAction source.\n");
    }

    sb.append("project_root: ").append(context.projectRoot()).append("\n");
    return sb.toString();
  }

  private StepLinker.Link linkStep(String failedStep, AgentContext context) {
    List<FlavorEntry> flavorSteps = context.profile() != null && !context.profile().flavorSteps().isEmpty()
        ? context.profile().flavorSteps()
        : FrameworkKnowledgeStore.instance().flavorCatalog();
    List<StepDefinitionIndex> projectSteps = context.profile() != null
        ? context.profile().stepDefinitions()
        : List.of();
    return StepLinker.linkFeature(failedStep, flavorSteps, projectSteps).stream()
        .findFirst()
        .orElse(new StepLinker.Link(0, "", failedStep, StepLinker.Source.UNMATCHED, "", ""));
  }

  private String extractStep(String snippet) {
    if (snippet == null) return null;
    var matcher = Pattern.compile("(?m)^\\s*(Given|When|Then|And|But)\\s+.+$").matcher(snippet);
    return matcher.find() ? matcher.group().strip() : null;
  }

  private boolean containsAny(String text, String... needles) {
    for (String needle : needles) {
      if (text.contains(needle)) return true;
    }
    return false;
  }
}
