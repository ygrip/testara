package io.github.ygrip.testara.command.generator;

import io.github.ygrip.testara.command.model.CommandLogic;
import io.github.ygrip.testara.command.model.CommandTag;
import net.datafaker.Faker;
import org.apache.commons.lang3.ObjectUtils;

import java.util.List;
import java.util.Locale;

/**
 * <p>FakeFirstNameCommand class.</p>
 *
 * Generates a fake first name.
 * Usage: fakefirstname() or fakefirstname(locale)
 */
@CommandTag(command = "fakefirstname", overwrite = true)
public class FakeFirstNameCommand implements CommandLogic<String> {
  @Override
  public boolean preProcessParameters() {
    return true;
  }

  @Override
  public String execute(List<Object> parameters) {
    Faker faker = ObjectUtils.isEmpty(parameters)
        ? new Faker()
        : new Faker(Locale.of(String.valueOf(parameters.get(0))));
    return faker.name().firstName();
  }
}
