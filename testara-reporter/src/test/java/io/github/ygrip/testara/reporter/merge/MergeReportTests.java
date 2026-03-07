package io.github.ygrip.testara.reporter.merge;

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
@Tag("merge")
@TestWith(properties = {"classpath:application.properties", "classpath:configuration.properties"})
public class MergeReportTests extends BaseTests {

  @Test
  public void mergeReportFromRawCucumberJson() throws Throwable {
    String dir = System.getProperty("user.dir");
    String fileName = "cucumber.json";
    String sourcePath = "/src/test/resources/cucumber/multiple";
    String targetPath = "/target/destination/cucumber/replacable/multiple";
    FileHelper.copyFiles(dir + sourcePath, dir + targetPath);
    String mergedPath = CucumberReportMergeFactory.Builder.using(CucumberReportReader.getReportPaths(dir + targetPath))
      .mergeAs(dir + targetPath + "/" + fileName);
    List<Feature> features = CucumberReportMergeFactory.Builder.using(CucumberReportReader.getReportPaths(mergedPath))
      .getMergedFeatures();
    assertThat(features, notNullValue());
    assertThat(features.size(), equalTo(2));
    assertThat(
      features.stream()
        .map(Feature::getScenarios)
        .reduce(0, Integer::sum), equalTo(2)
    );
    assertThat(
      features.stream()
        .map(feature -> feature.getElements()
          .stream()
          .map(Element::getStatus)
          .collect(Collectors.toList()))
        .flatMap(Collection::stream)
        .collect(Collectors.toList()), Every.everyItem(equalTo(Status.PASSED))
    );
    FileHelper.deleteFile(dir + "/target/destination/cucumber/replacable/multiple");
  }

  @Test
  public void mergeReportFromCleanCucumberJson() throws Throwable {
    String dir = System.getProperty("user.dir");
    String fileName = "cucumber.json";
    String sourcePath = "/src/test/resources/cucumber/multiple";
    String targetPath = "/target/destination/cucumber/replacable/multiple";
    FileHelper.copyFiles(dir + sourcePath, dir + targetPath);
    CucumberJsonFormatter.fromTargetLocation(targetPath)
      .overwrite(true)
      .rewriteScenarioWithOutlines();
    String mergedPath = CucumberReportMergeFactory.Builder.using(CucumberReportReader.getReportPaths(dir + targetPath))
      .mergeAs(dir + targetPath + "/" + fileName);
    List<Feature> features = CucumberReportMergeFactory.Builder.using(CucumberReportReader.getReportPaths(mergedPath))
      .getMergedFeatures();
    assertThat(features, notNullValue());
    assertThat(features.size(), equalTo(2));
    assertThat(
      features.stream()
        .map(Feature::getScenarios)
        .reduce(0, Integer::sum), equalTo(8)
    );
    assertThat(
      features.stream()
        .map(feature -> feature.getElements()
          .stream()
          .map(Element::getStatus)
          .collect(Collectors.toList()))
        .flatMap(Collection::stream)
        .collect(Collectors.toList()), Every.everyItem(equalTo(Status.PASSED))
    );
    FileHelper.deleteFile(dir + "/target/destination/cucumber/replacable/multiple");
  }

  @Test
  public void mergeReportWithEmbedding() throws Throwable {
    String dir = System.getProperty("user.dir");
    String fileName = "cucumber.json";
    String sourcePath = "/src/test/resources/cucumber/embeddings";
    String targetPath = "/target/destination/cucumber/replacable/embeddings";
    FileHelper.copyFiles(dir + sourcePath, dir + targetPath);
    CucumberJsonFormatter.fromTargetLocation(targetPath)
      .overwrite(true)
      .rewriteScenarioWithOutlines();
    String mergedPath = CucumberReportMergeFactory.Builder.using(CucumberReportReader.getReportPaths(dir + targetPath))
      .mergeAs(dir + targetPath + "/" + fileName);
    List<Feature> features = CucumberReportMergeFactory.Builder.using(CucumberReportReader.getReportPaths(mergedPath))
      .getMergedFeatures();
    assertThat(features, notNullValue());

    FileHelper.deleteFile(dir + "/target/destination/cucumber/replacable/embeddings");
  }
}
