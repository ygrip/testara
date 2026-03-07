package io.github.ygrip.testara.command.generator;

import io.github.ygrip.testara.command.model.CommandLogic;
import io.github.ygrip.testara.command.model.CommandTag;
import net.datafaker.Faker;
import org.apache.commons.lang3.ObjectUtils;

import java.util.List;

/**
 * <p>FakePhoneNumberCommand class.</p>
 *
 * Generates a fake phone number.
 * Usage: fakephone() or fakephone(prefix)
 */
@CommandTag(command = "fakephone", overwrite = true)
public class FakePhoneNumberCommand implements CommandLogic<String> {
  @Override
  public boolean preProcessParameters() {
    return true;
  }

  @Override
  public String execute(List<Object> parameters) {
    Faker faker = new Faker();
    String digits = faker.phoneNumber().subscriberNumber(10);

    if (ObjectUtils.isNotEmpty(parameters)) {
      String prefix = String.valueOf(parameters.get(0));
      return prefix + digits;
    }
    return digits;
  }
}
