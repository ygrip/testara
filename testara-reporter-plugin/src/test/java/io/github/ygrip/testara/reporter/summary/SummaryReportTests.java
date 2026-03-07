package io.github.ygrip.testara.reporter.summary;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.ygrip.testara.core.BaseTests;
import io.github.ygrip.testara.core.TestWith;
import io.github.ygrip.testara.core.file.FileHelper;
import io.github.ygrip.testara.reporter.cucumber.CucumberSummaryReportGenerator;

@Tag("reporter")
@Tag("summary")
@TestWith(properties = {"classpath:application.properties", "classpath:configuration.properties"})
public class SummaryReportTests extends BaseTests {

  @Test
  public void summaryWithPassedStatus() throws Exception {
    String dir = System.getProperty("user.dir");
    String path = "/src/test/resources/cucumber/multiple/";
    String outputPath = "/target/destination/";
    String targetPath = "/target/destination/multiple/temp";
    FileHelper.copyFiles(dir + path, dir + targetPath, ".json");
    CucumberSummaryReportGenerator.fromLocation(targetPath)
      .withReportName("Automation Testing")
      .withOutputFileName("index")
      .toLocation(outputPath)
      .withReportTemplate("testara-simple-report")
      .generateReport();
    FileHelper.deleteFile(dir + targetPath);
  }

  @Test
  public void summaryWithErrorStatus() throws Exception {
    String dir = System.getProperty("user.dir");
    String path = "/src/test/resources/cucumber/multiple/cucumber-chrome-desktop.json";
    String outputPath = "/target/destination/";
    String targetPath = "/target/destination/multiple/error";
    FileHelper.copyFiles(dir + path, dir + targetPath, ".json");
    CucumberSummaryReportGenerator.fromLocation(targetPath)
      .withReportName("Automation Testing")
      .withOutputFileName("error")
      .toLocation(outputPath)
      .generateReport();
    FileHelper.deleteFile(dir + targetPath);
  }

  @Test
  public void summaryWithBrokenStatus() throws Exception {
    String dir = System.getProperty("user.dir");
    String path = "/src/test/resources/cucumber/cucumber.json";
    String outputPath = "/target/destination/";
    String targetPath = "/target/destination/pending";
    FileHelper.copyFiles(dir + path, dir + targetPath, ".json");
    CucumberSummaryReportGenerator.fromLocation(targetPath)
      .withReportName("Automation Testing")
      .withOutputFileName("broken")
      .toLocation(outputPath)
      .generateReport();
    FileHelper.deleteFile(dir + targetPath);
  }

  @Test
  public void summaryWithBackground() throws Exception {
    String dir = System.getProperty("user.dir");
    String path = "/src/test/resources/cucumber/commerce";
    String outputPath = "/target/destination/";
    String targetPath = "/target/destination/commerce";
    FileHelper.copyFiles(dir + path, dir + targetPath, ".json");
    CucumberSummaryReportGenerator.fromLocation(targetPath)
      .withReportName("Automation Testing")
      .withOutputFileName("commerce")
      .toLocation(outputPath)
      .generateReport();
    FileHelper.deleteFile(dir + targetPath);
  }

  @Test
  public void summaryWithEmbedding() throws Throwable {
    String dir = System.getProperty("user.dir");
    String path = "/src/test/resources/cucumber/embeddings";
    String outputPath = "/target/destination/embeddings";
    String targetPath = "/target/destination/";
    FileHelper.copyFiles(dir + path, dir + targetPath, ".json");
    CucumberSummaryReportGenerator.fromLocation(targetPath)
      .withReportName("Automation Testing")
      .withOutputFileName("embeddings")
      .toLocation(outputPath)
      .generateReport();
    FileHelper.deleteFile(dir + targetPath);
  }
}
