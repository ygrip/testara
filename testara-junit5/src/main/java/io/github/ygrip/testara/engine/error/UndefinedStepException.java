package io.github.ygrip.testara.engine.error;

import io.cucumber.core.runtime.TestCaseResultObserver;
import org.opentest4j.IncompleteExecutionException;

import java.util.Collection;
import java.util.stream.Collectors;

public final class UndefinedStepException extends IncompleteExecutionException {
  private static final long serialVersionUID = 1L;

  public UndefinedStepException(Collection<TestCaseResultObserver.Suggestion> suggestions) {
    super(createMessage(suggestions));
  }

  private static String createMessage(Collection<TestCaseResultObserver.Suggestion> suggestions) {
    if (suggestions.isEmpty()) {
      return "This step is undefined";
    } else {
      TestCaseResultObserver.Suggestion
          first = suggestions.iterator().next();
      StringBuilder sb = new StringBuilder("The step '" + first.getStep() + "'");
      if (suggestions.size() == 1) {
        sb.append(" is undefined.");
      } else {
        sb.append(" and ").append(suggestions.size() - 1).append(" other step(s) are undefined.");
      }

      sb.append("\n");
      if (suggestions.size() == 1) {
        sb.append("You can implement this step using the snippet(s) below:\n\n");
      } else {
        sb.append("You can implement these steps using the snippet(s) below:\n\n");
      }

      String snippets = suggestions.stream().map(TestCaseResultObserver.Suggestion::getSnippets).flatMap(Collection::stream).distinct().collect(
          Collectors.joining("\n", "", "\n"));
      sb.append(snippets);
      return sb.toString();
    }
  }
}

