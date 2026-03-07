package io.github.ygrip.testara.reporter.aggregate;

import static org.hamcrest.MatcherAssert.assertThat;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.ygrip.testara.core.BaseTests;
import io.github.ygrip.testara.core.TestWith;
import io.github.ygrip.testara.core.file.FileHelper;
import io.github.ygrip.testara.reporter.cucumber.CucumberReportMergeFactory;
import io.github.ygrip.testara.reporter.formatter.CucumberJsonFormatter;
import io.github.ygrip.testara.reporter.model.AggregateSummary;
import io.github.ygrip.testara.reporter.reader.CucumberReportReader;

@Tag("reporter")
@Tag("aggregate")
@TestWith(properties = {"classpath:application.properties", "classpath:configuration.properties"})
public class AggregateReportTests extends BaseTests {

  @Test
  public void fromRawCucumberJson() throws Throwable {
    String dir = System.getProperty("user.dir");
    String fileName = "aggregate-summary.json";
    String sourcePath = "/src/test/resources/cucumber/multiple";
    String targetPath = "/target/destination/cucumber/replacable/multiple";
    FileHelper.copyFiles(dir + sourcePath, dir + targetPath);
    AggregateSummary summary =
      CucumberReportMergeFactory.Builder.using(CucumberReportReader.getReportPaths(dir + targetPath))
        .aggregate(dir + targetPath + "/" + fileName);
    assertThat(summary, Matchers.notNullValue());
    FileHelper.deleteFile(dir + targetPath + "/" + fileName);
    FileHelper.deleteFile(dir + "/target/destination/cucumber/replacable/multiple");
  }

  @Test
  public void fromCleanCucumberJson() throws Throwable {
    String dir = System.getProperty("user.dir");
    String fileName = "aggregate-summary.json";
    String sourcePath = "/src/test/resources/cucumber/multiple";
    String targetPath = "/target/destination/cucumber/replacable/multiple";
    FileHelper.copyFiles(dir + sourcePath, dir + targetPath);
    CucumberJsonFormatter.fromTargetLocation(targetPath)
      .overwrite(true)
      .rewriteScenarioWithOutlines();
    AggregateSummary summary =
      CucumberReportMergeFactory.Builder.using(CucumberReportReader.getReportPaths(dir + targetPath))
        .aggregate(dir + targetPath + "/" + fileName);
    assertThat(summary, Matchers.notNullValue());

    FileHelper.deleteFile(dir + "/target/destination/cucumber/replacable/multiple");
  }
}
