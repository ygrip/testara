package io.github.ygrip.testara.ui.page;

import java.util.Map;

public record ParameterizedElementMatch(
    String elementName,
    Map<String, Object> parameters
) {
}
