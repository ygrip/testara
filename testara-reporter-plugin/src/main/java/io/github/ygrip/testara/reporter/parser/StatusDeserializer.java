package io.github.ygrip.testara.reporter.parser;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.ygrip.testara.reporter.cucumber.Status;

public class StatusDeserializer extends CucumberJsonDeserializer<Status> {
  static final List<String> UNKNOWN_STATUSES = Arrays.asList("ambiguous");

  public StatusDeserializer() {
  }

  public Status deserialize(JsonNode rootNode) {
    String status = rootNode.asText();
    return UNKNOWN_STATUSES.contains(status) ?
        Status.UNDEFINED :
        Status.valueOf(status.toUpperCase(Locale.ENGLISH));
  }
}
