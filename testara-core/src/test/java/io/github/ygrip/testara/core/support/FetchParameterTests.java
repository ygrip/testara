package io.github.ygrip.testara.core.support;

import io.github.ygrip.testara.core.BaseTests;
import io.github.ygrip.testara.core.TestWith;
import org.apache.commons.lang3.ObjectUtils;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

@Tag("fetchParameter")
@TestWith(properties = {"classpath:application.properties", "classpath:configuration.properties"})
public class FetchParameterTests extends BaseTests {

  @Test
  public void withMatchingInputAndPattern() {
    String pattern = "user has (\\d+) data to (validate|save)";
    String input = "user has 5 data to save";
    FetchParameter patternExample = new FetchParameter(pattern);
    List<String> parameters = patternExample.fromInput(input).getParameters();
    assertThat(ObjectUtils.isEmpty(parameters), equalTo(false));
    assertThat(parameters.size(), equalTo(2));
    assertThat(parameters.get(0), equalTo("5"));
    assertThat(parameters.get(1), equalTo("save"));
  }

  @Test
  public void withNonMatchingInputAndPattern() {
    String pattern = "user has (\\d+) data to (validate|save)";
    String input = "user has some data to delete";
    FetchParameter patternExample = new FetchParameter(pattern);
    List<String> parameters = patternExample.fromInput(input).getParameters();
    assertThat(ObjectUtils.isEmpty(parameters), equalTo(true));
    assertThat(parameters.size(), equalTo(0));
  }

  @Test
  public void withNegativeLookaround() {
    String pattern = "(?:.*) has (\\d+) data to (validate|save)";
    String input = "user has 5 data to save";
    FetchParameter patternExample = new FetchParameter(pattern);
    List<String> parameters = patternExample.fromInput(input).getParameters();
    assertThat(ObjectUtils.isEmpty(parameters), equalTo(false));
    assertThat(parameters.size(), equalTo(2));
  }
}
