package io.github.ygrip.testara.reporter.reader;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;

import java.io.File;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.ygrip.testara.reporter.formatter.CucumberJsonFormatter;

public class CucumberReportReaderTests {

  @Test
  public void missingReportLocationYieldsNoReports() throws Exception {
    String missing = System.getProperty("user.dir") + "/target/no-report-" + UUID.randomUUID() + "/";

    assertThat(CucumberReportReader.getReportFiles(missing), empty());
  }

  @Test
  public void formatterDoesNotTurnMissingReportDirectoryIntoFile() throws Exception {
    String relative = "/target/no-report-" + UUID.randomUUID() + "/";

    CucumberJsonFormatter.fromTargetLocation(relative).rewriteScenarioWithOutlines();

    assertThat(new File(System.getProperty("user.dir") + relative).exists(), equalTo(false));
  }
}
