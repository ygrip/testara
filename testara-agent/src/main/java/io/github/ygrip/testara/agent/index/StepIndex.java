package io.github.ygrip.testara.agent.index;

import java.util.List;

public record StepIndex(
    String keyword,
    String text,
    List<List<String>> dataTable
) {
  public StepIndex(String keyword, String text) {
    this(keyword, text, List.of());
  }
}
