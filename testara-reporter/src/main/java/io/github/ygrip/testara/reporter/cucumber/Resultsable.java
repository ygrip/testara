package io.github.ygrip.testara.reporter.cucumber;

import java.util.List;

public interface Resultsable {
  Result getResult();

  Match getMatch();

  List<Output> getOutputs();
}
