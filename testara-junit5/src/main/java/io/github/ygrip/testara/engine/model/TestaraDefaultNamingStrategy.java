package io.github.ygrip.testara.engine.model;

import io.cucumber.core.gherkin.Pickle;
import io.cucumber.plugin.event.Node;

import java.util.Locale;
import java.util.function.Supplier;

public enum TestaraDefaultNamingStrategy implements TestaraNamingStrategy {
  LONG {
    public String name(Node node) {
      StringBuilder builder = new StringBuilder();
      builder.append(TestaraDefaultNamingStrategy.nameOrKeyword(node));

      for (
          node = node.getParent().orElse(null);
          node != null; node = node.getParent().orElse(null)) {
        builder.insert(0, " - ");
        builder.insert(0, TestaraDefaultNamingStrategy.nameOrKeyword(node));
      }

      return builder.toString();
    }

    @Override
    public String nameExample(Node node, Pickle pickle) {
      return TestaraDefaultNamingStrategy.pickleNameIfParameterized(node, pickle);
    }
  }, CUSTOM {
    public String name(Node node) {
      return TestaraDefaultNamingStrategy.nameOrKeyword(node);
    }
    @Override
    public String nameExample(Node node, Pickle pickle) {
      return TestaraDefaultNamingStrategy.pickleNameIfParameterized(node, pickle);
    }
  }, SHORT {
    public String name(Node node) {
      return TestaraDefaultNamingStrategy.nameOrKeyword(node);
    }
    @Override
    public String nameExample(Node node, Pickle pickle) {
      return TestaraDefaultNamingStrategy.pickleNameIfParameterized(node, pickle);
    }
  };

  private TestaraDefaultNamingStrategy() {
  }

  public static TestaraDefaultNamingStrategy getStrategy(String s) {
    return valueOf(s.toUpperCase(Locale.ROOT));
  }

  private static String nameOrKeyword(Node node) {
    Supplier<String> keyword = () -> (String) node.getKeyword().orElse("Unknown");
    return node.getName().orElseGet(keyword);
  }

  private static String pickleNameIfParameterized(Node node, Pickle pickle) {
    if (node instanceof Node.Example) {
      String pickleName = pickle.getName();
      boolean parameterized = !node.getParent()
          .flatMap(Node::getParent)
          .flatMap(Node::getName)
          .filter(pickleName::equals)
          .isPresent();
      if (parameterized) {
        return ": " + pickleName;
      }
    }
    return "";
  }
}
