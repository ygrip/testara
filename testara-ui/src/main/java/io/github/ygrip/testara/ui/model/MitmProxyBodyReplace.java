package io.github.ygrip.testara.ui.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Substring find-and-replace inside a request or response body.
 * Maps to {@code BodyReplaceSchema} in the MitmProxy Grid API.
 * <p>
 * The API uses {@code from_} (with underscore) because {@code from}
 * is a reserved keyword in Python.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MitmProxyBodyReplace {
  @JsonProperty("from_")
  private String from;
  private String to;
}
