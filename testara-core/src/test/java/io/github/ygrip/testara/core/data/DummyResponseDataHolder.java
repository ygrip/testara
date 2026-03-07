package io.github.ygrip.testara.core.data;

import io.github.ygrip.testara.core.context.TestComponent;
import io.github.ygrip.testara.core.model.DefaultData;
import io.github.ygrip.testara.core.model.ResponseData;
import io.github.ygrip.testara.core.registry.RegistryScope;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@ResponseData
@TestComponent(scope = RegistryScope.TEST)
public class DummyResponseDataHolder extends DefaultData {
  private long number;
  private String name;
}
