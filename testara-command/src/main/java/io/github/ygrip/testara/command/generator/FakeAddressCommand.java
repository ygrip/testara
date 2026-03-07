package io.github.ygrip.testara.command.generator;

import io.github.ygrip.testara.command.model.CommandLogic;
import io.github.ygrip.testara.command.model.CommandTag;
import net.datafaker.Faker;
import net.datafaker.providers.base.Address;
import org.apache.commons.lang3.ObjectUtils;

import java.util.List;
import java.util.Locale;

/**
 * <p>FakeAddressCommand class.</p>
 *
 * Generates a fake address as a structured {@link FakeAddressModel}.
 * Usage: fakeaddress() or fakeaddress(locale)
 */
@CommandTag(command = "fakeaddress", overwrite = true)
public class FakeAddressCommand implements CommandLogic<FakeAddressModel> {
  @Override
  public boolean preProcessParameters() {
    return true;
  }

  @Override
  public FakeAddressModel execute(List<Object> parameters) {
    Faker faker = ObjectUtils.isEmpty(parameters)
        ? new Faker()
        : new Faker(Locale.of(String.valueOf(parameters.get(0))));

    Address address = faker.address();
    return new FakeAddressModel(
        address.fullAddress(),
        address.streetAddress(),
        address.city(),
        address.state(),
        address.country(),
        address.zipCode()
    );
  }
}
