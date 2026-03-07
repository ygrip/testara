package io.github.ygrip.testara.core.support;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <p>FetchParameter class.</p>
 *
 * @author yunaz.ramadhan on 8/12/2020
 * @version $Id: $Id
 */
public class FetchParameter {
  private final Pattern PATTERN;
  private String INPUT;

  /**
   * <p>Constructor for FetchParameter.</p>
   *
   * @param pattern a {@link String} object.
   */
  public FetchParameter(String pattern) {
    PATTERN = Pattern.compile(pattern, Pattern.DOTALL);
  }

  /**
   * <p>matchPattern.</p>
   *
   * @return a boolean.
   */
  public boolean matchPattern() {
    Matcher groups = PATTERN.matcher(INPUT);
    return groups.find();
  }

  /**
   * <p>getParameters.</p>
   *
   * @return a {@link List} object.
   */
  public List<String> getParameters() {
    List<String> result = new ArrayList<>();
    if (!CommonHelper.isBlank(INPUT) && !CommonHelper.isBlank(PATTERN)) {
      Matcher groups = PATTERN.matcher(INPUT);
      if (groups.find()) {
        int counter = groups.groupCount();
        for (int i = 1; i <= counter; i++) {
          result.add(groups.group(i));
        }
      }
    }
    return result;
  }

  /**
   * <p>fromInput.</p>
   *
   * @param input a {@link String} object.
   * @return a {@link FetchParameter} object.
   */
  public FetchParameter fromInput(String input) {
    INPUT = input;
    return this;
  }
}
