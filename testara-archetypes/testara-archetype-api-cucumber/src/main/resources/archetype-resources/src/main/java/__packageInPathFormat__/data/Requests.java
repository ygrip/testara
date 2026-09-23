package ${package}.data;

import io.github.ygrip.testara.core.model.DefaultData;
import io.github.ygrip.testara.core.model.RequestData;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Mutable request data store for the generated API automation project. */
@Data
@EqualsAndHashCode(callSuper = true)
@RequestData
public class Requests extends DefaultData {
  private String sampleValue;
}
