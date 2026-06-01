package io.github.ygrip.testara.agent.skill.run;

import java.util.regex.Pattern;

/**
 * Builds safe, template-based Maven commands for Cucumber test execution.
 * Rejects any input that could lead to shell injection.
 */
public class MavenCommandBuilder {

  private static final Pattern SAFE_TAG_EXPR = Pattern.compile(
      "^[@\\w\\s()\\-and not]*$");
  private static final Pattern SAFE_MODULE   = Pattern.compile("^[\\w-]+$");

  /** Build `mvn verify -Dcucumber.filter.tags="expr"` for Failsafe-based Testara runners. */
  public String build(String tagExpression) {
    return build(tagExpression, null, true);
  }

  /** Build with optional module and verify flag. */
  public String build(String tagExpression, String module, boolean useVerify) {
    validate(tagExpression, module);
    String goal = useVerify ? "verify" : "test";
    String pl = (module != null && !module.isBlank()) ? "-pl " + module + " " : "";
    return String.format("mvn %s%s -Dcucumber.filter.tags=\"%s\"", pl, goal, tagExpression);
  }

  private void validate(String tagExpression, String module) {
    if (tagExpression == null || tagExpression.isBlank())
      throw new IllegalArgumentException("Tag expression must not be blank");
    if (!SAFE_TAG_EXPR.matcher(tagExpression).matches())
      throw new IllegalArgumentException("Tag expression contains unsafe characters: " + tagExpression);
    if (module != null && !module.isBlank() && !SAFE_MODULE.matcher(module).matches())
      throw new IllegalArgumentException("Module name contains unsafe characters: " + module);
  }
}
