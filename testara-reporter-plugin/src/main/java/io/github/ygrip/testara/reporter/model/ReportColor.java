package io.github.ygrip.testara.reporter.model;

import org.apache.commons.lang3.StringUtils;

public enum ReportColor {
  PASSED(0, "rgb(97, 189, 118)"),
  FAILED(1, "rgb(243, 52, 70)"),
  SKIPPED(2, "rgb(172, 177, 185)"),
  PENDING(3, "rgb(146, 220, 206)"),
  UNDEFINED(4,"rgb(252, 177, 80)");

  private final String colorCode;
  private final int ordinal;

  ReportColor(int ordinal, String colorCode) {
    this.colorCode = colorCode;
    this.ordinal = ordinal;
  }

  public int getOrdinal() {
    return ordinal;
  }

  public String getColorCode() {
    return this.colorCode;
  }

  public String getCapitalizedName() {
    return StringUtils.capitalize(name().toLowerCase());
  }
}
