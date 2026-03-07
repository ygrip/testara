package io.github.ygrip.testara.reporter.clean;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.hamcrest.core.Every;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.ygrip.testara.core.BaseTests;
import io.github.ygrip.testara.core.TestWith;
import io.github.ygrip.testara.core.file.FileHelper;
import io.github.ygrip.testara.reporter.cucumber.CucumberReportMergeFactory;
import io.github.ygrip.testara.reporter.cucumber.Element;
import io.github.ygrip.testara.reporter.cucumber.Feature;
import io.github.ygrip.testara.reporter.cucumber.Status;
import io.github.ygrip.testara.reporter.formatter.CucumberJsonFormatter;
import io.github.ygrip.testara.reporter.reader.CucumberReportReader;

@Tag("reporter")
@Tag("clean")
@TestWith(properties = {"classpath:application.properties", "classpath:configuration.properties"})
public class CleanReportTests extends BaseTests {

  @Test
  public void createNewScenarioOutlinesFromSingleReports() throws Throwable {
    String dir = System.getProperty("user.dir");
    String path = "/src/test/resources/cucumber/desktop.json";
    CucumberJsonFormatter.fromTargetLocation(path).overwrite(false).rewriteScenarioWithOutlines();
    FileHelper.deleteFile(dir + "/src/test/resources/cucumber/custom-report");
  }

  @Test
  public void createNewScenarioOutlinesFromReportsWithBackground() throws Throwable {
    String dir = System.getProperty("user.dir");
    String path = "/src/test/resources/cucumber/commerce/";
    List<Feature> features =
      CucumberReportMergeFactory.Builder.using(CucumberReportReader.getReportPaths(dir + path))
        .getMergedFeatures();
    assertThat(features, notNullValue());
    FileHelper.deleteFile(dir + "/src/test/resources/cucumber/commerce/custom-report");
  }

  @Test
  public void createNewScenarioOutlinesFromMultipleReports() throws Throwable {
    String dir = System.getProperty("user.dir");
    String path = "/src/test/resources/cucumber/multiple/";
    CucumberJsonFormatter.fromTargetLocation(path).overwrite(false).rewriteScenarioWithOutlines();
    FileHelper.deleteFile(dir + "/src/test/resources/cucumber/multiple/custom-report");
  }

  @Test
  public void overwriteScenarioOutlinesFromSingleReports() throws Throwable {
    String dir = System.getProperty("user.dir");
    String sourcePath = "/src/test/resources/cucumber/desktop.json";
    String targetPath = "/target/destination/cucumber/replacable/desktop.json";
    FileHelper.copyFile(dir + sourcePath, dir + targetPath);
    CucumberJsonFormatter.fromTargetLocation(targetPath)
      .overwrite(true)
      .rewriteScenarioWithOutlines();
    FileHelper.deleteFile(dir + "/src/test/resources/cucumber/replacable");
  }

  @Test
  public void mergeReportFromUncleanCucumberJson() throws Throwable {
    String dir = System.getProperty("user.dir");
    String path = "/src/test/resources/cucumber/multiple/";
    List<Feature> features =
      CucumberReportMergeFactory.Builder.using(CucumberReportReader.getReportPaths(dir + path))
        .getMergedFeatures();
    assertThat(features, notNullValue());
    assertThat(features.size(), equalTo(2));
    assertThat(features.stream().map(Feature::getScenarios).reduce(0, Integer::sum), equalTo(2));
    assertThat(features.stream()
      .map(feature -> feature.getElements()
        .stream()
        .map(Element::getStatus)
        .collect(Collectors.toList()))
      .flatMap(Collection::stream)
      .collect(Collectors.toList()), Every.everyItem(equalTo(Status.PASSED)));
  }

  @Test
  public void mergeReportFromCleanCucumberJson() throws Throwable {
    String dir = System.getProperty("user.dir");
    String path = "/src/test/resources/cucumber/multiple/";
    String targetPath = "/src/test/resources/cucumber/multiple/custom-report";
    CucumberJsonFormatter.fromTargetLocation(path).overwrite(false).rewriteScenarioWithOutlines();
    List<Feature> features =
      CucumberReportMergeFactory.Builder.using(CucumberReportReader.getReportPaths(dir + targetPath))
        .getMergedFeatures();
    assertThat(features, notNullValue());
    assertThat(features.size(), equalTo(2));
    assertThat(features.stream().map(Feature::getScenarios).reduce(0, Integer::sum), equalTo(8));
    assertThat(features.stream()
      .map(feature -> feature.getElements()
        .stream()
        .map(Element::getStatus)
        .collect(Collectors.toList()))
      .flatMap(Collection::stream)
      .collect(Collectors.toList()), Every.everyItem(equalTo(Status.PASSED)));
    FileHelper.deleteFile(dir + targetPath);
  }

  @Test
  public void createNewScenarioOutlinesFromMultipleWeirdReports() throws Throwable {
    String dir = System.getProperty("user.dir");
    String path = "/src/test/resources/cucumber/parallel";
    CucumberJsonFormatter.fromTargetLocation(path).overwrite(false).rewriteScenarioWithOutlines();
    FileHelper.deleteFile(dir + "/src/test/resources/cucumber/parallel/custom-report");
  }

  @Test
  public void mergeDuplicateElements() throws Throwable {
    String dir = System.getProperty("user.dir");
    String path = "/src/test/resources/cucumber/combine/";
    List<Feature> features =
      CucumberReportMergeFactory.Builder.using(CucumberReportReader.getReportPaths(dir + path))
        .getMergedFeatures();
    assertThat(features, notNullValue());
    assertThat(features.stream().map(Feature::getScenarios).reduce(0, Integer::sum), equalTo(2));
    assertThat(features.stream().map(Feature::getPassedScenarios).reduce(0, Integer::sum),
      equalTo(2));
    FileHelper.deleteFile(dir + "/src/test/resources/cucumber/combine/custom-report");
  }
}
