package io.github.ygrip.testara.reporter.cucumber;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.extras.java8time.dialect.Java8TimeDialect;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import com.google.common.base.Stopwatch;

import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.core.file.FileHelper;
import io.github.ygrip.testara.reporter.config.ReportConfiguration;
import io.github.ygrip.testara.reporter.formatter.CucumberJsonFormatter;
import io.github.ygrip.testara.reporter.model.AggregateSummary;
import io.github.ygrip.testara.reporter.model.ReportColor;
import io.github.ygrip.testara.reporter.reader.CucumberReportReader;
import io.github.ygrip.testara.reporter.support.ChartUtil;
import io.github.ygrip.testara.reporter.support.CommonUtil;

public class CucumberSummaryReportGenerator {
  private static final Logger LOG = Logger.getLogger(CucumberSummaryReportGenerator.class.getName());
  private final String DIR = System.getProperty("user.dir");
  private final String REPORT_PATH;
  private final String FULLPATH;
  private final Integer SLOWEST_STEPS_NUMBER_LIMIT = 10;
  private final Integer SLOWEST_SCENARIOS_NUMBER_LIMIT = 10;
  private final Integer MAX_CUSTOM_FIELDS_COLUMN = 3;
  private final Integer MAX_CUSTOM_FIELDS_ROWS = 5;
  private String DEFAULT_REPORT_TEMPLATE;
  private String OUTPUT_PATH;
  private String OUTPUT_NAME = "summary";
  private String reportName;


  public CucumberSummaryReportGenerator(String reportPath) {
    this.reportName = "Application In Test";
    REPORT_PATH = reportPath;
    FULLPATH = DIR + REPORT_PATH;
    OUTPUT_PATH = FULLPATH;
    this.DEFAULT_REPORT_TEMPLATE = "testara-style-report";
  }

  public static CucumberSummaryReportGenerator fromLocation(String reportPath) {
    return new CucumberSummaryReportGenerator(reportPath);
  }

  public CucumberSummaryReportGenerator withReportName(String reportName) {
    if (reportName != null && !reportName.trim()
      .isEmpty()) {
      this.reportName = reportName;
    }
    return this;
  }

  public CucumberSummaryReportGenerator withReportTemplate(String template) {
    if (template != null && !template.trim()
      .isEmpty()) {
      this.DEFAULT_REPORT_TEMPLATE = template;
    }
    return this;
  }

  public CucumberSummaryReportGenerator withOutputFileName(String outputFileName) {
    if (outputFileName != null && !outputFileName.trim()
      .isEmpty()) {
      this.OUTPUT_NAME = outputFileName;
    }
    return this;
  }

  public CucumberSummaryReportGenerator toLocation(String outputPath) {
    if (outputPath != null && !outputPath.trim()
      .isEmpty()) {
      this.OUTPUT_PATH = DIR + outputPath;
    }
    return this;
  }

  public String mergeReportAs(String fileName, boolean needCleanUp) throws Exception {
    if (needCleanUp) {
      CucumberJsonFormatter.fromTargetLocation(REPORT_PATH)
        .overwrite(true)
        .rewriteScenarioWithOutlines();
    }
    return CucumberReportMergeFactory.Builder.using(CucumberReportReader.getReportPaths(FULLPATH))
      .mergeAs(FULLPATH + File.separator + fileName);
  }

  public String mergeReportAs(String fileName) throws Exception {
    return mergeReportAs(fileName, true);
  }

  public AggregateSummary aggregate(String fileName, boolean needCleanUp) throws Exception {
    if (needCleanUp) {
      CucumberJsonFormatter.fromTargetLocation(REPORT_PATH)
        .overwrite(true)
        .rewriteScenarioWithOutlines();
    }
    return CucumberReportMergeFactory.Builder.using(CucumberReportReader.getReportPaths(FULLPATH))
      .aggregate(FULLPATH + File.separator + fileName);
  }

  public AggregateSummary aggregate(String fileName) throws Exception {
    return aggregate(fileName, true);
  }

  public void generateReport(boolean needCleanUp) throws Exception {
    if (needCleanUp) {
      CucumberJsonFormatter.fromTargetLocation(REPORT_PATH)
        .overwrite(true)
        .rewriteScenarioWithOutlines();
    }
    writeReport(this.reportName, FULLPATH, OUTPUT_NAME);
  }

  public void generateReport() throws Exception {
    generateReport(true);
  }

  public void writeReport(String reportName, String reportPath, String fileName) throws Exception {
    Stopwatch stopwatch = Stopwatch.createStarted();
    this.reportName = reportName;
    ClassLoaderTemplateResolver resolver =
      new ClassLoaderTemplateResolver(CucumberSummaryReportGenerator.class.getClassLoader());
    resolver.setTemplateMode(TemplateMode.HTML);
    resolver.setCharacterEncoding("UTF-8");
    resolver.setCacheable(false);
    resolver.setPrefix("template/");
    resolver.setSuffix(".html");
    resolver.setOrder(1);
    resolver.setName(reportName);

    List<Feature> features = CucumberReportMergeFactory.Builder.using(CucumberReportReader.getReportPaths(reportPath))
      .getMergedFeatures();
    Context context = generateContext(features);

    TemplateEngine templateEngine = new TemplateEngine();
    templateEngine.addDialect(new Java8TimeDialect());
    templateEngine.setTemplateResolver(resolver);

    String result = templateEngine.process(DEFAULT_REPORT_TEMPLATE, context);
    String output = FileHelper.writeToFile(result, OUTPUT_PATH + File.separator + fileName + ".html");
    templateEngine.clearTemplateCacheFor(reportName);
    LOG.info(String.format(
      "Generating report %s took %s ms\nReport location : %s",
      reportName,
      stopwatch.stop()
        .elapsed(TimeUnit.MILLISECONDS),
      output
    ));
  }

  private Context generateContext(List<Feature> features) throws Exception {
    Context context = new Context();

    // Generate core data
    Map<String, Object> reportSummary = generateReportSummary(features);
    Map<String, Object> reportResults = CommonUtil.generateReportResultsSummary(features);

    // Generate all required data for comprehensive clean template
    List<Map<String, Object>> customFields = generateCustomFields();
    List<Coverage> coverage = generateCoverage(features);
    List<FrequentFailure> frequentFailures = getFrequentFailures(features);
    List<FailedFeature> failedFeatures = getFailedFeatures(features);
    List<LongestStep> longestSteps = getLongestSteps(SLOWEST_STEPS_NUMBER_LIMIT, features);
    List<LongestScenario> longestScenarios = getLongestScenarios(SLOWEST_SCENARIOS_NUMBER_LIMIT, features);

    boolean testFailuresPresent = !frequentFailures.isEmpty();

    // Set all variables for both templates
    context.setVariable("report", reportSummary);
    context.setVariable("results", reportResults);
    context.setVariable("customFields", customFields);
    context.setVariable("coverage", coverage);
    context.setVariable("frequentFailures", frequentFailures);
    context.setVariable("unstableFeatures", failedFeatures);
    context.setVariable("testFailuresPresent", testFailuresPresent);
    context.setVariable("longestScenarios", longestScenarios);
    context.setVariable("longestSteps", longestSteps);

    return context;
  }

  private List<LongestScenario> getLongestScenarios(Integer limit, List<Feature> features) {
    Map<String, LongestScenario> result = new HashMap<>();
    for (Feature feature : features) {
      List<Element> elements = feature.getElements();
      for (Element element : elements) {
        if (element.isScenario()) {
          LongestScenario longestStep = new LongestScenario();
          longestStep.setName(element.getName());
          longestStep.setDuration(element.getDuration());
          result.put(element.getName(), longestStep);
        }
      }
    }

    List<LongestScenario> list = result.values()
      .stream()
      .sorted(Collections.reverseOrder())
      .limit(limit)
      .collect(Collectors.toList());
    Long longestDuration = list.get(0)
      .getDuration();
    for (LongestScenario scenario : list) {
      int percentage = (int) Math.round(Double.valueOf(scenario.getDuration()) / Double.valueOf(longestDuration) * 100);
      percentage = Math.max(5, percentage);
      scenario.setPercentage(percentage);
    }
    return list;
  }

  private List<LongestStep> getLongestSteps(int limit, List<Feature> features) {
    Map<String, LongestStep> result = new HashMap<>();
    for (Feature feature : features) {
      List<Element> elements = feature.getElements();
      for (Element element : elements) {
        List<Step> steps = element.getSteps();
        for (Step step : steps) {
          LongestStep longestStep = result.getOrDefault(step.getName(), new LongestStep());
          longestStep.addCount();
          longestStep.setName(step.getName());
          longestStep.setDuration(step.getDuration());
          result.put(step.getName(), longestStep);
        }
      }
    }

    List<LongestStep> list = result.values()
      .stream()
      .sorted(Collections.reverseOrder())
      .limit(limit)
      .collect(Collectors.toList());
    Long longestDuration = list.get(0)
      .getMaxDuration();
    for (LongestStep step : list) {
      int percentage = (int) Math.round(Double.valueOf(step.getMaxDuration()) / Double.valueOf(longestDuration) * 100);
      percentage = Math.max(5, percentage);
      step.setPercentage(percentage);
    }
    return list;
  }

  private List<FailedFeature> getFailedFeatures(List<Feature> features) {
    return features.stream()
      .filter(feature -> feature.getFailedScenarios() > 0)
      .map(item -> {
        FailedFeature failedFeature = new FailedFeature();
        failedFeature.setName(item.getName());
        int failedPercentage =
          (int) Math.round((double) item.getFailedScenarios() / (double) item.getScenarios() * 100);
        failedFeature.setFailurePercentage(failedPercentage);
        return failedFeature;
      })
      .sorted(Collections.reverseOrder())
      .collect(Collectors.toList());
  }

  private List<FrequentFailure> getFrequentFailures(List<Feature> features) {
    // Use parallel stream for better performance with large datasets
    Map<String, Map<String, Integer>> failureCount = features.parallelStream()
      .flatMap(feature -> feature.getElements()
        .stream())
      .flatMap(element -> element.getSteps()
        .stream())
      .map(Step::getResult)
      .filter(result -> !result.getStatus()
        .isPassed())
      .filter(result -> result.getErrorMessageTitle() != null && !result.getErrorMessageTitle()
        .trim()
        .isEmpty())
      .collect(Collectors.groupingBy(
        result -> result.getErrorMessageTitle()
          .trim(), Collectors.groupingBy(
          result -> result.getStatus()
            .equals(Status.FAILED) ? "failure" : "error", Collectors.summingInt(result -> 1)
        )
      ));

    // Convert to FrequentFailure objects and sort by total count
    return failureCount.entrySet()
      .stream()
      .map(entry -> {
        FrequentFailure failure = new FrequentFailure();
        String errorKey = entry.getKey();
        Map<String, Integer> statusCounts = entry.getValue();

        // Extract simple name from full error message
        String name = sanitize(Arrays.stream(errorKey.split("\\."))
          .reduce((first, last) -> last)
          .orElse(errorKey));

        int totalCount = statusCounts.values()
          .stream()
          .mapToInt(Integer::intValue)
          .sum();

        // Get the most frequent status type
        String resultClass = statusCounts.entrySet()
          .stream()
          .max(Map.Entry.comparingByValue())
          .map(Map.Entry::getKey)
          .orElse("failure");

        failure.setName(name);
        failure.setError(errorKey);
        failure.setCount(totalCount);
        failure.setResultClass(resultClass);
        return failure;
      })
      .sorted(Collections.reverseOrder())
      .limit(10) // Limit to top 10 failures for email
      .collect(Collectors.toList());
  }

  private List<Coverage> generateCoverage(List<Feature> features) {
    List<Coverage> result = new ArrayList<>();
    Map<String, List<Feature>> mappedFeatures = splitFeatureBySuite(features);
    Set<String> coverageTitles = mappedFeatures.keySet();
    for (String tagTitle : coverageTitles) {
      Coverage coverage = new Coverage();
      coverage.setTagTitle(tagTitle);
      List<TagCoverage> tagCoverages = new ArrayList<>();
      List<Feature> featureList = mappedFeatures.get(tagTitle);
      Long featuresDuration = 0L;
      for (Feature feature : featureList) {
        TagCoverage tagCoverage = new TagCoverage();
        tagCoverage.setTagName(feature.getName());
        int total = feature.getScenarios();
        tagCoverage.setTestCount(total);
        LinkedHashMap<String, Integer> count = getFeatureSummaryCount(feature);
        Map<String, Integer> percentage = getFeatureSummaryPercentage(feature);
        String passRate =
          String.format("%s", percentage.getOrDefault(ReportColor.PASSED.getCapitalizedName(), 0)) + "%";
        featuresDuration += feature.getDuration();
        tagCoverage.setDuration(feature.getDuration());
        tagCoverage.setSuccessRate(passRate);
        tagCoverage.setCountByResult(count);
        tagCoverage.setPercentageByResult(percentage);
        tagCoverages.add(tagCoverage);
      }
      coverage.setDuration(featuresDuration);
      coverage.setTagCoverage(tagCoverages);
      result.add(coverage);
    }
    return result;
  }

  private Map<String, List<Feature>> splitFeatureBySuite(List<Feature> features) {
    Map<String, List<Feature>> result = new HashMap<>();
    for (Feature feature : features) {
      URI uri = URI.create(feature.getUri());
      List<String> nodes = CommonUtil.getTreeNode(uri.getSchemeSpecificPart());
      String nodesAsString = nodes.stream()
        .limit(nodes.size() - 1)
        .map(this::sanitize)
        .collect(Collectors.joining(" > "));
      List<Feature> item = result.getOrDefault(nodesAsString, new ArrayList<>());
      item.add(feature);
      result.put(nodesAsString, item);
    }
    if (result.isEmpty()) {
      result.put("Features", features);
    }
    return result;
  }

  private String sanitize(String input) {
    input = input.replaceAll("[_|-]", " ");
    input = input.replaceAll("\\.", "");
    input = input.replaceAll(":", "");
    input = input.replaceFirst("^\\d+", "");
    input = String.join(" ", input.split("(?=\\p{Upper})"));
    return StringUtils.capitalize(input.trim());
  }

  private List<Map<String, Object>> generateCustomFields() {
    List<Map<String, Object>> result = new ArrayList<>();
    try {

      ReportConfiguration configuration = TestFramework.configuration()
        .get(ReportConfiguration.class);

      Map<String, Object> props = configuration.getCustomFields();
      int row = 0;
      Map<String, Object> item = new HashMap<>();
      List<Map.Entry<String, Object>> set = new ArrayList<>(props.entrySet());
      for (int i = 0; i < set.size(); i++) {
        Map.Entry<String, Object> entry = set.get(i);
        if (row == MAX_CUSTOM_FIELDS_ROWS) {
          break;
        }
        if (row == 0) {
          item.put("OS", System.getProperty("os.name"));
          item.put("User", System.getProperty("user.name"));
        }
        String id = sanitize(entry.getKey());
        if (!id.equalsIgnoreCase("link")) {
          item.put(id, entry.getValue());
        }
        if (item
          .size() == MAX_CUSTOM_FIELDS_COLUMN || i == set.size() - 1) {
          result.add(item);
          item = new HashMap<>();
        }
        row = result.size();
      }
    } catch (Exception ignored) {

    }
    return result;
  }

  private Map<String, Object> generateReportSummary(List<Feature> features) throws UnsupportedEncodingException {
    HashMap<String, Object> result = new HashMap<>();
    result.put("title", this.reportName);
    result.put("totalFeatures", features.size());
    result.put("overallStatus", getOverallStatus(features));
    result.put(
      "date",
      features.stream()
        .min(Comparator.comparing(Feature::getStartTime))
        .map(Feature::getStartTime)
        .orElse(null)
    );
    result.put(
      "endDate",
      features.stream()
        .max(Comparator.comparing(Feature::getStopTime))
        .map(Feature::getStopTime)
        .orElse(null)
    );
    result.put("reportDate", LocalDateTime.now());
    result.put("link", getReportLink());
    Map<String, Integer> chartData = CommonUtil.getTotalSummaryCount(features);
    result.put("countByResult", new HashMap<>(chartData));
    result.put("chartUrl", generateChartLink(chartData));
    Integer totalScenarios = features.stream()
      .map(Feature::getScenarios)
      .reduce(0, Integer::sum);
    Map<String, Integer> chartDataInPercentage = CommonUtil.roundTallyTo(
      chartData.entrySet()
        .stream()
        .collect(Collectors.toMap(
          Map.Entry::getKey,
          entry -> (Double.valueOf(entry.getValue()) / Double.valueOf(totalScenarios) * 100)
        )), 100
    );
    result.put("percentageByResult", chartDataInPercentage);
    return result;
  }

  private String getReportLink() {
    return System.getProperty("custom.report.link");
  }

  private String getOverallStatus(List<Feature> features) {
    List<Status> check = features.stream()
      .map(Feature::getStatus)
      .filter(notPass -> !notPass.isPassed())
      .collect(Collectors.toList());
    if (check.isEmpty()) {
      return Status.PASSED.name()
        .toUpperCase();
    } else {
      Integer totalScenarios = features.stream()
        .map(Feature::getScenarios)
        .reduce(0, Integer::sum);
      Integer failedScenarios = features.stream()
        .map(Feature::getFailedScenarios)
        .reduce(0, Integer::sum);
      double failedPercentage = Double.valueOf(failedScenarios) / Double.valueOf(totalScenarios);
      return failedPercentage > 0.15 ?
        Status.FAILED.name()
          .toUpperCase() :
        Status.PENDING.name()
          .toUpperCase();
    }
  }

  private String generateChartLink(Map<String, Integer> chartData) throws UnsupportedEncodingException {
    List<Integer> data = Arrays.stream(ReportColor.values())
      .sorted(Comparator.comparing(ReportColor::getOrdinal))
      .map(report -> chartData.get(report.getCapitalizedName()))
      .collect(Collectors.toList());
    List<String> colors = Arrays.stream(ReportColor.values())
      .sorted(Comparator.comparing(ReportColor::getOrdinal))
      .map(ReportColor::getColorCode)
      .collect(Collectors.toList());
    return ChartUtil.generateChartUrl(data, colors, "doughnut", "png", 500, 300);
  }

  private LinkedHashMap<String, Integer> getFeatureSummaryCount(Feature feature) {
    LinkedHashMap<String, Integer> result = new LinkedHashMap<>();
    Arrays.stream(ReportColor.values())
      .sorted(Comparator.comparing(ReportColor::getOrdinal))
      .forEach(status -> {
        switch (status) {
          case PASSED:
            result.put(status.getCapitalizedName(), feature.getPassedScenarios());
            break;
          case FAILED:
            result.put(status.getCapitalizedName(), feature.getFailedScenarios());
            break;
          case SKIPPED:
            result.put(status.getCapitalizedName(), feature.getSkippedScenarios());
            break;
          case PENDING:
            result.put(status.getCapitalizedName(), feature.getPendingScenarios());
            break;
          case UNDEFINED:
            result.put(status.getCapitalizedName(), feature.getUndefinedScenarios());
            break;
          default:
            break;
        }
      });
    return result;
  }

  private Map<String, Integer> getFeatureSummaryPercentage(Feature feature) {
    Map<String, Double> result = new HashMap<>();
    Arrays.stream(ReportColor.values())
      .sorted(Comparator.comparing(ReportColor::getOrdinal))
      .forEach(status -> {
        double percentage;
        switch (status) {
          case PASSED:
            percentage = ((double) feature.getPassedScenarios() / (double) feature.getScenarios()) * 100;
            result.put(status.getCapitalizedName(), percentage);
            break;
          case FAILED:
            percentage = ((double) feature.getFailedScenarios() / (double) feature.getScenarios()) * 100;
            result.put(status.getCapitalizedName(), percentage);
            break;
          case SKIPPED:
            percentage = ((double) feature.getSkippedScenarios() / (double) feature.getScenarios()) * 100;
            result.put(status.getCapitalizedName(), percentage);
            break;
          case PENDING:
            percentage = ((double) feature.getPendingScenarios() / (double) feature.getScenarios()) * 100;
            result.put(status.getCapitalizedName(), percentage);
            break;
          case UNDEFINED:
            percentage = ((double) feature.getUndefinedScenarios() / (double) feature.getScenarios()) * 100;
            result.put(status.getCapitalizedName(), percentage);
            break;
          default:
            break;
        }
      });
    return CommonUtil.roundTallyTo(result, 100);
  }
}
