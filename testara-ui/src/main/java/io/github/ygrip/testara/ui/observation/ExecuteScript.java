package io.github.ygrip.testara.ui.observation;

import io.github.ygrip.testara.ui.executor.Actor;
import io.github.ygrip.testara.ui.interaction.InteractionContext;
import io.github.ygrip.testara.ui.page.Element;

/**
 * Screenplay-style observation: get result of a script.
 *
 * @see Actor#observe(Observation)
 */
public final class ExecuteScript implements Observation<Object> {
  private final Object[] args;
  private final String script;

  private ExecuteScript(String script, Object... args) {
    this.script = script;
    this.args = args;
  }

  public static TheScriptValue of(String propertyName) {
    return new TheScriptValue(propertyName);
  }

  @Override
  public Observation<Object> root(Element root) {
    Object arg = null;
    try {
      arg = root.one();
    } catch (Exception ignored) {

    }
    return new ExecuteScript(script, arg, args);
  }

  @Override
  public Object perform(InteractionContext context) {
    return context.observation()
      .fromScript(script, args);
  }

  public static class TheScriptValue {
    private final String script;

    public TheScriptValue(String script) {
      this.script = script;
    }

    public ExecuteScript withArguments(Object... args) {
      return new ExecuteScript(script, args);
    }

    public ExecuteScript withNoArguments() {
      return new ExecuteScript(script);
    }
  }
}
