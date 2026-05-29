package io.github.ygrip.testara.agent.index;

import java.util.List;

public record ExamplesIndex(
    List<String> headers,
    int rowCount
) {}
