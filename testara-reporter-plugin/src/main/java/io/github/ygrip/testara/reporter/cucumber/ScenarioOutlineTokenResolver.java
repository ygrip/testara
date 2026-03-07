package io.github.ygrip.testara.reporter.cucumber;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import io.github.ygrip.testara.reporter.reader.TokenResolver;

public class ScenarioOutlineTokenResolver extends TokenResolver {
  private final Map<String, LinkedList<String>> tokenMap = new HashMap<>();

  public ScenarioOutlineTokenResolver(String startMarker, String endMarker) {
    super(startMarker, endMarker);
  }

  @Override
  public boolean hasMapping() {
    return !tokenMap.isEmpty();
  }

  @Override
  public ScenarioOutlineTokenResolver addToken(String key, String value) {
    LinkedList<String> linkedList = this.tokenMap.getOrDefault(key, new LinkedList<>());
    linkedList.add(value);
    this.tokenMap.put(key, linkedList);
    return this;
  }

  public ScenarioOutlineTokenResolver addToken(Map<String, String> input) {
    input.forEach(this::addToken);
    return this;
  }

  @Override
  public String resolveToken(String token) throws IOException {
    LinkedList<String> list = this.tokenMap.getOrDefault(token, new LinkedList<>());
    return list.isEmpty() ? "" : list.removeFirst();
  }
}
