package io.github.ygrip.testara.reporter.model;

public enum ReportTheme {
  MODERN("#0f172a", "#2563eb", "#f8fafc", "#ffffff", "#0f172a", "#64748b", "#e2e8f0"),
  CLASSIC("#1e3a5f", "#2f80ed", "#f3f6fa", "#ffffff", "#1f2937", "#6b7280", "#dbe3ec"),
  SIMPLE("#111827", "#475569", "#ffffff", "#ffffff", "#111827", "#6b7280", "#e5e7eb"),
  SINGLE_PAGE("#172554", "#1d4ed8", "#f8fafc", "#ffffff", "#111827", "#64748b", "#dbeafe");

  private final String headerBackground;
  private final String accent;
  private final String pageBackground;
  private final String cardBackground;
  private final String textColor;
  private final String mutedColor;
  private final String borderColor;

  ReportTheme(String headerBackground, String accent, String pageBackground, String cardBackground,
      String textColor, String mutedColor, String borderColor) {
    this.headerBackground = headerBackground;
    this.accent = accent;
    this.pageBackground = pageBackground;
    this.cardBackground = cardBackground;
    this.textColor = textColor;
    this.mutedColor = mutedColor;
    this.borderColor = borderColor;
  }

  public String headerBackground() {
    return headerBackground;
  }

  public String accent() {
    return accent;
  }

  public String pageBackground() {
    return pageBackground;
  }

  public String cardBackground() {
    return cardBackground;
  }

  public String textColor() {
    return textColor;
  }

  public String mutedColor() {
    return mutedColor;
  }

  public String borderColor() {
    return borderColor;
  }
}
