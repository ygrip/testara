package io.github.ygrip.testara.core.support;

import io.github.ygrip.testara.core.BaseTests;
import io.github.ygrip.testara.core.TestWith;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

@Tag("stringUtils")
@TestWith(properties = {"classpath:application.properties", "classpath:configuration.properties"})
public class StringHelperTests extends BaseTests {

  @Test
  public void prettyPrint() {
    Map<String, String> input = Map.of("name", "automation", "age", "10");
    String output = StringHelper.prettyPrint(input);
    assertThat(output, not(blankOrNullString()));
  }

  @Test
  public void capitalize() {
    String input = "this string should be capitalized";
    String expected = "This string should be capitalized";
    String output = StringHelper.capitalize(input);
    assertThat(output, equalTo(expected));
  }

  @Test
  public void ellipsis() {
    String input = "this string should be elipsized";
    String expected = "this string should be...";
    String output = StringHelper.ellipsize(input, 22);
    assertThat(output, equalTo(expected));
  }
}
