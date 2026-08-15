package io.github.ygrip.testara.reporter.summary;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.ygrip.testara.reporter.config.ReportConfiguration;

class ReportConfigurationTest {

  @Test
  void interactiveReportIsDisabledByDefault() {
    ReportConfiguration configuration = new ReportConfiguration();

    assertFalse(configuration.getInteractive().isEnabled());
  }

  @Test
  void interactiveReportCanBeEnabledExplicitly() {
    ReportConfiguration configuration = new ReportConfiguration();

    configuration.getInteractive().setEnabled(true);

    assertTrue(configuration.getInteractive().isEnabled());
  }
}
