package ${package}.data;

import io.github.ygrip.testara.core.model.DefaultData;
import io.github.ygrip.testara.core.model.RequestData;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Mutable request data store shared by generated API and UI samples. */
@Data
@EqualsAndHashCode(callSuper = true)
@RequestData
public class Requests extends DefaultData {
  private String sampleValue;
}
