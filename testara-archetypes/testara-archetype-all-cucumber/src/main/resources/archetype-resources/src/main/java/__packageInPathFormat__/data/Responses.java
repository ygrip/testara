package ${package}.data;

import io.github.ygrip.testara.core.model.DefaultData;
import io.github.ygrip.testara.core.model.ResponseData;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Mutable response data store shared by generated API and UI samples. */
@Data
@EqualsAndHashCode(callSuper = true)
@ResponseData
public class Responses extends DefaultData {
  private String sampleStatus;
}
