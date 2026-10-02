package io.github.ygrip.testara.agent.index;

import java.util.List;

public record ExamplesIndex(
    List<String> tags,
    List<String> headers,
    int rowCount
) {
  public ExamplesIndex(List<String> headers, int rowCount) {
    this(List.of(), headers, rowCount);
  }
}
