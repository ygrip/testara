package io.github.ygrip.testara.engine.model;

import io.cucumber.core.gherkin.Pickle;
import io.cucumber.plugin.event.Node;

public interface TestaraNamingStrategy {
  String name(Node node);

  String nameExample(Node node, Pickle pickle);
}
