package io.github.ygrip.testara.reporter.model;

import java.util.Locale;

public enum ReportStyle {
  MODERN("report/modern.jte"),
  CLASSIC("report/classic.jte"),
  SIMPLE("report/simple.jte"),
  SINGLE_PAGE("report/single-page.jte");

  private final String template;

  ReportStyle(String template) {
    this.template = template;
  }

  public String template() {
    return template;
  }

  public static ReportStyle from(String value) {
    if (value == null || value.isBlank()) {
      return MODERN;
    }

    String normalized = value.trim().toLowerCase(Locale.ROOT).replace('_', '-');
    return switch (normalized) {
      case "modern", "testara-modern-report" -> MODERN;
      case "classic", "testara-style-report" -> CLASSIC;
      case "simple", "testara-simple-report" -> SIMPLE;
      case "single-page", "singlepage", "testara-single-page-report" -> SINGLE_PAGE;
      default -> throw new IllegalArgumentException(
        "Unsupported Testara report style '" + value
          + "'. Supported values: modern, classic, simple, single-page"
      );
    };
  }
}
