package io.github.ygrip.testara.cucumber.steps;

import io.github.ygrip.testara.core.context.TestComponent;
import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.core.json.SchemaHelper;
import io.github.ygrip.testara.core.model.RetryableMethod;
import io.github.ygrip.testara.core.registry.RegistryScope;
import io.cucumber.java.en.And;

@TestComponent(scope = RegistryScope.TEST)
public class SchemaValidatorSteps {

  @RetryableMethod
  @And("^(.+) data \"([^\"]*)\" should satisfy schema \"([^\"]*)\"$")
  public void dataShouldSatisfyJsonSchema(String identifier, String data, String schemaName) throws Throwable {
    Object obj = TestFramework.context().converter().convert(data);
    SchemaHelper.loadSchema(schemaName).validate(obj);
  }
}
