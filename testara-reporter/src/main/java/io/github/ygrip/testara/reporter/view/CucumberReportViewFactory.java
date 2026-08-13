package io.github.ygrip.testara.reporter.view;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

import io.github.ygrip.testara.reporter.branding.BrandingLogoResolver;
import io.github.ygrip.testara.reporter.config.ReportConfiguration;
import io.github.ygrip.testara.reporter.cucumber.Element;
import io.github.ygrip.testara.reporter.cucumber.Feature;
import io.github.ygrip.testara.reporter.cucumber.Status;
import io.github.ygrip.testara.reporter.cucumber.Step;
import io.github.ygrip.testara.reporter.cucumber.Tag;
import io.github.ygrip.testara.reporter.support.CommonUtil;
import io.github.ygrip.testara.reporter.view.ReportView.ReportBranding;
import io.github.ygrip.testara.reporter.view.ReportView.ReportCoverageGroup;
import io.github.ygrip.testara.reporter.view.ReportView.ReportCoverageItem;
import io.github.ygrip.testara.reporter.view.ReportView.ReportDurationItem;
import io.github.ygrip.testara.reporter.view.ReportView.ReportFailure;
import io.github.ygrip.testara.reporter.view.ReportView.ReportField;
import io.github.ygrip.testara.reporter.view.ReportView.ReportMetadata;
import io.github.ygrip.testara.reporter.view.ReportView.ReportScenario;
import io.github.ygrip.testara.reporter.view.ReportView.ReportScenarioStep;
import io.github.ygrip.testara.reporter.view.ReportView.ReportStatusMetric;
import io.github.ygrip.testara.reporter.view.ReportView.ReportStatusSummary;
import io.github.ygrip.testara.reporter.view.ReportView.ReportUnstableFeature;

public class CucumberReportViewFactory {
  private static final int SLOWEST_LIMIT = 10;
  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
  private final BrandingLogoResolver logoResolver;

  public CucumberReportViewFactory() {
    this(new BrandingLogoResolver());
  }

  CucumberReportViewFactory(BrandingLogoResolver logoResolver) {
    this.logoResolver = logoResolver;
  }

  public ReportView create(List<Feature> input, String reportName, ReportConfiguration configuration) {
    List<Feature> features = input == null ? List.of() : List.copyOf(input);
    ReportConfiguration config = configuration == null ? new ReportConfiguration() : configuration;

    int totalScenarios = features.stream().mapToInt(Feature::getScenarios).sum();
    int totalSteps = features.stream().mapToInt(Feature::getSteps).sum();
    List<ReportStatusMetric> metrics = statusMetrics(features, totalScenarios);

    return new ReportView(
      metadata(features, reportName, totalScenarios, totalSteps),
      branding(config),
      statusSummary(features, totalScenarios, metrics),
      customFields(config),
      coverage(features),
      frequentFailures(features),
      unstableFeatures(features),
      longestScenarios(features),
      longestSteps(features),
      scenarioViews(features)
    );
  }

  private ReportMetadata metadata(List<Feature> features, String reportName, int totalScenarios, int totalSteps) {
    List<Element> scenarios = scenarios(features);
    LocalDateTime start = features.stream().map(Feature::getStartTime).filter(value -> value != null)
      .min(LocalDateTime::compareTo).orElse(null);
    LocalDateTime end = features.stream().map(Feature::getStopTime).filter(value -> value != null)
      .max(LocalDateTime::compareTo).orElse(null);
    long scenarioDuration = scenarios.stream().mapToLong(Element::getDuration).sum();
    long elapsedDuration = start != null && end != null ? Math.max(0L, start.until(end, ChronoUnit.NANOS)) : scenarioDuration;
    long averageDuration = totalScenarios == 0 ? 0L : scenarioDuration / totalScenarios;
    Element fastest = scenarios.stream().min(Comparator.comparingLong(Element::getDuration)).orElse(null);
    Element slowest = scenarios.stream().max(Comparator.comparingLong(Element::getDuration)).orElse(null);

    return new ReportMetadata(
      isBlank(reportName) ? "Application In Test" : reportName.trim(),
      formatDate(start),
      formatDate(end),
      DATE_FORMAT.format(LocalDateTime.now()),
      System.getProperty("custom.report.link"),
      features.size(),
      totalScenarios,
      totalSteps,
      formatDuration(elapsedDuration, totalScenarios > 0),
      formatDuration(averageDuration, totalScenarios > 0),
      fastest == null ? "-" : CommonUtil.formatDuration(fastest.getDuration()),
      slowest == null ? "-" : CommonUtil.formatDuration(slowest.getDuration())
    );
  }

  private ReportBranding branding(ReportConfiguration configuration) {
    String configuredName = configuration.getOrganizationName();
    String name = isBlank(configuredName) ? "Testara" : configuredName.trim();
    String detail = isBlank(configuration.getOrganizationDetail()) ? null : configuration.getOrganizationDetail().trim();
    String logo = logoResolver.resolve(configuration.getOrganizationLogo());
    boolean whiteLabeled = !"Testara".equals(name) || detail != null || logo != null;
    return new ReportBranding(name, logo, detail, whiteLabeled);
  }

  private ReportStatusSummary statusSummary(List<Feature> features, int totalScenarios, List<ReportStatusMetric> metrics) {
    int failed = count(features, Feature::getFailedScenarios);
    int passed = count(features, Feature::getPassedScenarios);
    int passRate = totalScenarios == 0 ? 0 : (int) Math.round((double) passed * 100D / totalScenarios);
    String overall;
    if (totalScenarios == 0 || features.stream().allMatch(feature -> feature.getStatus().isPassed())) {
      overall = "PASSED";
    } else {
      overall = totalScenarios > 0 && ((double) failed / totalScenarios) > 0.15 ? "FAILED" : "PENDING";
    }
    String color = switch (overall) {
      case "FAILED" -> "#EF4444";
      case "PENDING" -> "#F59E0B";
      default -> "#22C55E";
    };
    String background = switch (overall) {
      case "FAILED" -> "#FEF2F2";
      case "PENDING" -> "#FFFBEB";
      default -> "#F0FDF4";
    };
    return new ReportStatusSummary(overall, color, background, passRate, List.copyOf(metrics));
  }

  private List<ReportStatusMetric> statusMetrics(List<Feature> features, int total) {
    return List.of(
      metric("Passed", "✓", "#16A34A", "#F0FDF4", count(features, Feature::getPassedScenarios), total),
      metric("Failed", "×", "#DC2626", "#FEF2F2", count(features, Feature::getFailedScenarios), total),
      metric("Skipped", "–", "#64748B", "#F8FAFC", count(features, Feature::getSkippedScenarios), total),
      metric("Pending", "◷", "#D97706", "#FFFBEB", count(features, Feature::getPendingScenarios), total),
      metric("Undefined", "?", "#EA580C", "#FFF7ED", count(features, Feature::getUndefinedScenarios), total)
    );
  }

  private ReportStatusMetric metric(String label, String icon, String color, String background, int count, int total) {
    int percentage = total == 0 ? 0 : (int) Math.round((double) count * 100D / total);
    return new ReportStatusMetric(label, count, percentage, color, background, icon);
  }

  private int count(List<Feature> features, ToIntFunction<Feature> counter) {
    return features.stream().mapToInt(counter).sum();
  }

  private List<ReportField> customFields(ReportConfiguration configuration) {
    List<ReportField> result = new ArrayList<>();
    result.add(new ReportField("OS", System.getProperty("os.name", "-")));
    result.add(new ReportField("User", System.getProperty("user.name", "-")));
    Map<String, Object> values = configuration.getCustomFields();
    if (values != null) {
      values.forEach((key, value) -> {
        if (!"link".equalsIgnoreCase(key) && value != null) {
          result.add(new ReportField(toLabel(key), String.valueOf(value)));
        }
      });
    }
    return List.copyOf(result);
  }

  private List<ReportCoverageGroup> coverage(List<Feature> features) {
    Map<String, List<Feature>> grouped = new LinkedHashMap<>();
    for (Feature feature : features) {
      grouped.computeIfAbsent(suiteTitle(feature), ignored -> new ArrayList<>()).add(feature);
    }
    if (grouped.isEmpty()) {
      return List.of();
    }

    return grouped.entrySet().stream().map(entry -> {
      long duration = entry.getValue().stream().mapToLong(Feature::getDuration).sum();
      List<ReportCoverageItem> items = entry.getValue().stream().map(feature -> {
        int total = feature.getScenarios();
        int passed = feature.getPassedScenarios();
        int percentage = total == 0 ? 0 : (int) Math.round((double) passed * 100D / total);
        return new ReportCoverageItem(
          feature.getName(),
          total,
          percentage + "%",
          percentage,
          CommonUtil.formatDuration(feature.getDuration())
        );
      }).toList();
      return new ReportCoverageGroup(entry.getKey(), CommonUtil.formatDuration(duration), items);
    }).toList();
  }

  private String suiteTitle(Feature feature) {
    try {
      if (isBlank(feature.getUri())) {
        return "Features";
      }
      URI uri = URI.create(feature.getUri());
      List<String> nodes = CommonUtil.getTreeNode(uri.getSchemeSpecificPart());
      if (nodes.size() <= 1) {
        return "Features";
      }
      return nodes.subList(0, nodes.size() - 1).stream().map(this::toLabel).collect(Collectors.joining(" > "));
    } catch (RuntimeException ignored) {
      return "Features";
    }
  }

  private List<ReportFailure> frequentFailures(List<Feature> features) {
    Map<String, Map<String, Integer>> grouped = features.stream()
      .flatMap(feature -> feature.getElements().stream())
      .flatMap(element -> element.getSteps().stream())
      .map(Step::getResult)
      .filter(result -> !result.getStatus().isPassed())
      .filter(result -> !isBlank(result.getErrorMessageTitle()))
      .collect(Collectors.groupingBy(
        result -> result.getErrorMessageTitle().trim(),
        Collectors.groupingBy(result -> result.getStatus() == Status.FAILED ? "failure" : "error", Collectors.summingInt(value -> 1))
      ));

    List<FailureSeed> seeds = grouped.entrySet().stream().map(entry -> {
      int total = entry.getValue().values().stream().mapToInt(Integer::intValue).sum();
      String severity = entry.getValue().entrySet().stream().max(Map.Entry.comparingByValue())
        .map(Map.Entry::getKey).orElse("failure");
      String[] parts = entry.getKey().split("\\.");
      return new FailureSeed(toLabel(parts[parts.length - 1]), entry.getKey(), total, severity);
    }).sorted(Comparator.comparingInt(FailureSeed::count).reversed()).limit(10).toList();

    int max = seeds.stream().mapToInt(FailureSeed::count).max().orElse(0);
    return seeds.stream().map(seed -> new ReportFailure(
      seed.name(), seed.error(), seed.count(), relative(seed.count(), max), seed.severity()
    )).toList();
  }

  private List<ReportUnstableFeature> unstableFeatures(List<Feature> features) {
    return features.stream().filter(feature -> feature.getFailedScenarios() > 0).map(feature -> {
      int total = feature.getScenarios();
      int percentage = total == 0 ? 0 : (int) Math.round((double) feature.getFailedScenarios() * 100D / total);
      return new ReportUnstableFeature(feature.getName(), percentage);
    }).sorted(Comparator.comparingInt(ReportUnstableFeature::failurePercentage).reversed()).toList();
  }

  private List<ReportDurationItem> longestScenarios(List<Feature> features) {
    List<Element> scenarios = scenarios(features).stream()
      .sorted(Comparator.comparingLong(Element::getDuration).reversed()).limit(SLOWEST_LIMIT).toList();
    long max = scenarios.stream().mapToLong(Element::getDuration).max().orElse(0L);
    return scenarios.stream().map(element -> new ReportDurationItem(
      element.getName(), CommonUtil.formatDuration(element.getDuration()), relative(element.getDuration(), max), null
    )).toList();
  }

  private List<ReportDurationItem> longestSteps(List<Feature> features) {
    Map<String, StepDuration> grouped = new LinkedHashMap<>();
    features.stream().flatMap(feature -> feature.getElements().stream()).flatMap(element -> element.getSteps().stream())
      .forEach(step -> grouped.computeIfAbsent(step.getName(), ignored -> new StepDuration()).add(step.getDuration()));
    List<Map.Entry<String, StepDuration>> entries = grouped.entrySet().stream()
      .sorted(Map.Entry.<String, StepDuration>comparingByValue().reversed()).limit(SLOWEST_LIMIT).toList();
    long max = entries.stream().mapToLong(entry -> entry.getValue().max).max().orElse(0L);
    return entries.stream().map(entry -> new ReportDurationItem(
      entry.getKey(), entry.getValue().range(), relative(entry.getValue().max, max), entry.getValue().count
    )).toList();
  }

  private List<ReportScenario> scenarioViews(List<Feature> features) {
    List<ReportScenario> result = new ArrayList<>();
    int scenarioIndex = 0;
    for (Feature feature : features) {
      String suite = suiteTitle(feature);
      for (Element element : feature.getElements()) {
        if (!element.isScenario()) {
          continue;
        }
        scenarioIndex++;
        List<ReportScenarioStep> steps = element.getSteps().stream().map(this::scenarioStep).toList();
        String error = steps.stream().map(ReportScenarioStep::error).filter(value -> !isBlank(value)).findFirst().orElse(null);
        result.add(new ReportScenario(
          "scenario-" + scenarioIndex,
          element.getName(),
          feature.getName(),
          suite,
          tags(feature, element),
          element.getStatus().name(),
          CommonUtil.formatDuration(element.getDuration()),
          element.getDuration(),
          error,
          steps
        ));
      }
    }
    return List.copyOf(result);
  }

  private ReportScenarioStep scenarioStep(Step step) {
    String error = step.getResult().getErrorMessage();
    return new ReportScenarioStep(
      stepKeyword(step),
      step.getName(),
      step.getResult().getStatus().name(),
      CommonUtil.formatDuration(step.getDuration()),
      isBlank(error) ? null : error
    );
  }

  private String tags(Feature feature, Element element) {
    Set<String> tags = new LinkedHashSet<>();
    feature.getTags().stream().map(Tag::getName).filter(value -> !isBlank(value)).forEach(tags::add);
    element.getTags().stream().map(Tag::getName).filter(value -> !isBlank(value)).forEach(tags::add);
    return String.join(", ", tags);
  }

  private String stepKeyword(Step step) {
    try {
      String keyword = step.getKeyword();
      return isBlank(keyword) ? "Step" : keyword.trim();
    } catch (RuntimeException ignored) {
      return "Step";
    }
  }

  private List<Element> scenarios(List<Feature> features) {
    return features.stream().map(Feature::getElements).flatMap(Collection::stream).filter(Element::isScenario).toList();
  }

  private int relative(long value, long max) {
    return max <= 0 ? 0 : Math.max(5, (int) Math.round((double) value * 100D / max));
  }

  private String formatDuration(long duration, boolean available) {
    return available ? CommonUtil.formatDuration(duration) : "-";
  }

  private String formatDate(LocalDateTime value) {
    return value == null ? "-" : DATE_FORMAT.format(value);
  }

  private String toLabel(String input) {
    if (input == null) {
      return "";
    }
    String value = input.replaceAll("[_|:-]", " ").replaceAll("\\.", "").replaceFirst("^\\d+", "");
    value = String.join(" ", value.split("(?=\\p{Upper})")).trim().replaceAll("\\s+", " ");
    if (value.isEmpty()) {
      return value;
    }
    return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private record FailureSeed(String name, String error, int count, String severity) {}

  private static final class StepDuration implements Comparable<StepDuration> {
    private int count;
    private long min = Long.MAX_VALUE;
    private long max;

    void add(long duration) {
      count++;
      min = Math.min(min, duration);
      max = Math.max(max, duration);
    }

    String range() {
      if (count == 0) {
        return "-";
      }
      if (min == max) {
        return CommonUtil.formatDuration(max);
      }
      return CommonUtil.formatDuration(min) + " - " + CommonUtil.formatDuration(max);
    }

    @Override
    public int compareTo(StepDuration other) {
      return Long.compare(max, other.max);
    }
  }
}
