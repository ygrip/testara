package io.github.ygrip.testara.reporter.model;

public enum ReportTheme {
  MODERN("#0B1220", "#00AFA5", "#F8FAFC", "#FFFFFF", "#0B1220", "#64748B", "#CBD5E1"),
  CLASSIC("#1E3A5F", "#2F80ED", "#F3F6FA", "#FFFFFF", "#1F2937", "#6B7280", "#DBE3EC"),
  SIMPLE("#111827", "#475569", "#FFFFFF", "#FFFFFF", "#111827", "#6B7280", "#E5E7EB"),
  SINGLE_PAGE("#0B1220", "#00AFA5", "#F8FAFC", "#FFFFFF", "#0B1220", "#64748B", "#CBD5E1");

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
