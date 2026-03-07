package io.github.ygrip.testara.command.generator;

import io.github.ygrip.testara.command.model.CommandLogic;
import io.github.ygrip.testara.command.model.CommandTag;
import net.datafaker.Faker;
import org.apache.commons.lang3.ObjectUtils;

import java.util.List;

/**
 * <p>FakeEmailCommand class.</p>
 *
 * Generates a fake email address.
 * Usage: fakeemail() or fakeemail(domain)
 */
@CommandTag(command = "fakeemail", overwrite = true)
public class FakeEmailCommand implements CommandLogic<String> {
  @Override
  public boolean preProcessParameters() {
    return true;
  }

  @Override
  public String execute(List<Object> parameters) {
    Faker faker = new Faker();

    if (ObjectUtils.isNotEmpty(parameters)) {
      String domain = String.valueOf(parameters.get(0));
      String localPart = faker.internet().emailAddress().split("@")[0];
      return localPart + "@" + domain;
    }
    return faker.internet().emailAddress();
  }
}
