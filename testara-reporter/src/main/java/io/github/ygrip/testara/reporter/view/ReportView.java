package io.github.ygrip.testara.reporter.view;

import java.util.List;

public record ReportView(
    ReportMetadata metadata,
    ReportBranding branding,
    ReportStatusSummary status,
    List<ReportField> customFields,
    List<ReportCoverageGroup> coverage,
    List<ReportFailure> frequentFailures,
    List<ReportUnstableFeature> unstableFeatures,
    List<ReportDurationItem> longestScenarios,
    List<ReportDurationItem> longestSteps,
    List<ReportScenario> scenarios
) {
  public record ReportMetadata(
      String title,
      String startTime,
      String endTime,
      String generatedAt,
      String reportLink,
      int totalFeatures,
      int totalScenarios,
      int totalSteps,
      String totalExecutionTime,
      String averageExecutionTime,
      String fastestTest,
      String slowestTest
  ) {}

  public record ReportBranding(
      String organizationName,
      String logoUri,
      String organizationDetail,
      boolean whiteLabeled
  ) {}

  public record ReportStatusSummary(
      String overallStatus,
      String overallColor,
      String overallBackground,
      int passRate,
      List<ReportStatusMetric> metrics
  ) {}

  public record ReportStatusMetric(
      String label,
      int count,
      int percentage,
      String color,
      String background,
      String icon
  ) {}

  public record ReportField(String label, String value) {}

  public record ReportCoverageGroup(
      String title,
      String duration,
      List<ReportCoverageItem> items
  ) {}

  public record ReportCoverageItem(
      String name,
      int testCount,
      String successRate,
      int passPercentage,
      String duration
  ) {}

  public record ReportFailure(
      String name,
      String error,
      int count,
      int relativePercentage,
      String severity
  ) {}

  public record ReportUnstableFeature(String name, int failurePercentage) {}

  public record ReportDurationItem(
      String label,
      String duration,
      int percentage,
      Integer count
  ) {}

  public record ReportScenario(
      String id,
      String name,
      String feature,
      String suite,
      String tags,
      String status,
      String duration,
      long durationNanos,
      String error,
      List<ReportScenarioStep> steps
  ) {}

  public record ReportScenarioStep(
      String keyword,
      String name,
      String status,
      String duration,
      String error
  ) {}
}
