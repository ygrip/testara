package io.github.ygrip.testara.ui.context;

public final class StepContext {

  private static final ThreadLocal<String> STEP_NAME = new ThreadLocal<>();
  private static final ThreadLocal<String> SCENARIO_NAME = new ThreadLocal<>();

  public static String getScenarioName() {
    return SCENARIO_NAME.get();
  }

  public static void setScenarioName(String step) {
    SCENARIO_NAME.set(step);
  }

  public static String getStepName() {
    return STEP_NAME.get();
  }

  public static void setStepName(String step) {
    STEP_NAME.set(step);
  }

  public static void clear() {
    STEP_NAME.remove();
    SCENARIO_NAME.remove();
  }
}
