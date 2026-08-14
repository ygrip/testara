package io.github.ygrip.testara.reporter.summary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.ygrip.testara.reporter.config.ReportConfiguration;
import io.github.ygrip.testara.reporter.cucumber.Status;
import io.github.ygrip.testara.reporter.model.ReportStyle;
import io.github.ygrip.testara.reporter.reader.CucumberReportReader;
import io.github.ygrip.testara.reporter.render.JteReportRenderer;
import io.github.ygrip.testara.reporter.view.CucumberReportViewFactory;

class InteractiveReportRichCucumberTest {
  @TempDir Path tempDir;

  @Test
  void preservesCompleteCucumberStepAndHookDetailInInteractiveReport() throws Exception {
    ReportConfiguration configuration = new ReportConfiguration();
    configuration.getInteractive().setEnabled(true);
    String reportPath = System.getProperty("user.dir") + "/src/test/resources/cucumber/rich/cucumber-rich.json";
    var features = CucumberReportReader.readReports(reportPath);

    var element = features.getFirst().getElements().getFirst();
    var firstStep = element.getSteps().getFirst();
    var docString = element.getSteps().get(1).getDocString();

    assertEquals(1, element.getBefore().size());
    assertEquals(1, element.getAfter().size());
    element.setMetaData();
    assertEquals(Status.PASSED, element.getBeforeStatus());
    assertEquals(Status.FAILED, element.getAfterStatus());

    assertEquals(1, firstStep.getBefore().size());
    assertEquals(1, firstStep.getAfter().size());
    firstStep.setMetaData();
    assertEquals(Status.PASSED, firstStep.getBeforeStatus());
    assertEquals(Status.FAILED, firstStep.getAfterStatus());

    assertFalse(firstStep.getOutputs().isEmpty());
    assertEquals("step output", firstStep.getOutputs().getFirst().getMessages().getFirst());
    assertEquals("after step output", firstStep.getAfter().getFirst().getOutputs().getFirst().getMessages().getFirst());
    assertEquals(1, firstStep.getAfter().getFirst().getEmbeddings().size());
    assertEquals("after-step.png", firstStep.getAfter().getFirst().getEmbeddings().getFirst().getName());
    assertEquals("application/json", docString.getClass().getMethod("getContentType").invoke(docString));
    assertEquals(6, docString.getClass().getMethod("getLine").invoke(docString));

    var view = new CucumberReportViewFactory().create(features, "Automation", configuration);
    var scenario = view.scenarios().getFirst();
    assertTrue(scenario.exampleExecution());
    assertEquals("Example #2", scenario.exampleLabel());
    assertEquals(2, scenario.steps().getFirst().dataTable().size());
    assertEquals(2, scenario.steps().get(2).attachments().size());

    Path output = tempDir.resolve("report.html");
    JteReportRenderer.INSTANCE.render(ReportStyle.SINGLE_PAGE, view, output);
    String html = Files.readString(output);

    assertTrue(html.contains("Example #2"));
    assertTrue(html.contains("step-data-table"));
    assertTrue(html.contains("step-doc-string"));
    assertTrue(html.contains("attachment-image"));
    assertTrue(html.contains("attachment-video"));
    assertTrue(html.contains("data:image/png;base64,aGVsbG8="));
    assertTrue(html.contains("data:video/mp4;base64,aGVsbG8="));

    assertTrue(html.contains("Before scenario"));
    assertTrue(html.contains("After scenario"));
    assertTrue(html.contains("Before step"));
    assertTrue(html.contains("After step"));
    assertTrue(html.contains("hooks.RichHooks.afterStep()"));
    assertTrue(html.contains("hooks.RichHooks.afterScenario()"));
    assertTrue(html.contains("after-step.png"));
    assertTrue(html.contains("data:image/png;base64,d29ybGQ="));
    assertTrue(html.contains("before scenario output"));
    assertTrue(html.contains("step output"));
    assertTrue(html.contains("AfterStepException"));
    assertTrue(html.contains("AfterHookException"));
    assertTrue(html.contains("data-error-container"));
    assertTrue(html.contains("data-error-content"));
    assertTrue(html.contains("<details"));
  }
}
