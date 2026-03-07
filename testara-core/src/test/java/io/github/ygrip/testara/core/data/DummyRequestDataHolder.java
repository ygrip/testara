package io.github.ygrip.testara.core.data;

import java.util.Collections;
import java.util.List;

import io.github.ygrip.testara.core.context.TestComponent;
import io.github.ygrip.testara.core.model.DefaultData;
import io.github.ygrip.testara.core.model.RequestData;
import io.github.ygrip.testara.core.registry.RegistryScope;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@RequestData
@TestComponent(scope = RegistryScope.TEST)
public class DummyRequestDataHolder extends DefaultData {
  private long number;
  private String name;
  private NestedDummyRequestData complex = new NestedDummyRequestData();


  @Data
  public static class NestedDummyRequestData {
    private List<InnerDummyRequestData> attributes = Collections.singletonList(new InnerDummyRequestData());
  }


  @Data
  public static class InnerDummyRequestData {
    private String name;
    private String location;
  }
}
