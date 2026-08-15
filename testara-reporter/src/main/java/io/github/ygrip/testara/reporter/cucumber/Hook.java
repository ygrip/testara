package io.github.ygrip.testara.reporter.cucumber;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import io.github.ygrip.testara.reporter.parser.EmbeddingDeserializer;
import io.github.ygrip.testara.reporter.parser.OutputsDeserializer;

public class Hook implements Resultsable, Serializable {
  @JsonProperty("result")
  private final Result result = new Result();
  @JsonProperty("match")
  private final Match match = new Match();
  @JsonDeserialize(using = OutputsDeserializer.class)
  @JsonProperty("output")
  @JsonAlias("outputs")
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  private final List<Output> outputs = new ArrayList<>();
  @JsonProperty("embeddings")
  @JsonDeserialize(using = EmbeddingDeserializer.class)
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  private final List<Embedding> embeddings = new ArrayList<>();

  public Hook() {
  }

  public Result getResult() {
    return this.result;
  }

  public Match getMatch() {
    return this.match;
  }

  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  public List<Output> getOutputs() {
    return this.outputs;
  }

  @JsonProperty("embeddings")
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  public List<Embedding> getEmbeddings() {
    return this.embeddings;
  }

  @JsonIgnore
  public boolean hasContent() {
    if (!this.embeddings.isEmpty() || !this.outputs.isEmpty()) {
      return true;
    } else {
      return this.result.getErrorMessage() != null && !this.result.getErrorMessage()
          .trim()
          .isEmpty();
    }
  }
}
