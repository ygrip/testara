package io.github.ygrip.testara.reporter.summary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import io.github.ygrip.testara.reporter.model.ReportStyle;

class ReportStyleTest {
  @Test
  void defaultsToModern() {
    assertEquals(ReportStyle.MODERN, ReportStyle.from(null));
    assertEquals(ReportStyle.MODERN, ReportStyle.from(" "));
  }

  @Test
  void mapsLegacyTemplateIdsToJteStyles() {
    assertEquals(ReportStyle.CLASSIC, ReportStyle.from("testara-style-report"));
    assertEquals(ReportStyle.SIMPLE, ReportStyle.from("testara-simple-report"));
    assertEquals(ReportStyle.SINGLE_PAGE, ReportStyle.from("testara-single-page-report"));
  }

  @Test
  void rejectsUnknownStyles() {
    assertThrows(IllegalArgumentException.class, () -> ReportStyle.from("custom-thymeleaf-template"));
  }
}
