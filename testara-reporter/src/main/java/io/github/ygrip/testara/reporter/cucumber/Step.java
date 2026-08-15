package io.github.ygrip.testara.reporter.cucumber;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import io.github.ygrip.testara.reporter.parser.CommentsDeserializer;
import io.github.ygrip.testara.reporter.parser.EmbeddingDeserializer;
import io.github.ygrip.testara.reporter.parser.OutputsDeserializer;

public class Step implements Serializable, Resultsable {
  @JsonProperty("keyword")
  private final String keyword = null;
  @JsonProperty("result")
  private final Result result = new Result();
  @JsonProperty("rows")
  private final List<Row> rows = new ArrayList<>();
  @JsonProperty("arguments")
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  private final List<Argument> arguments = new ArrayList<>();
  @JsonProperty("match")
  private final Match match = null;
  @JsonProperty("embeddings")
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  @JsonDeserialize(using = EmbeddingDeserializer.class)
  private final List<Embedding> embeddings = new ArrayList<>();
  @JsonDeserialize(using = OutputsDeserializer.class)
  @JsonProperty("output")
  @JsonAlias("outputs")
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  private final List<Output> outputs = new ArrayList<>();
  @JsonProperty("doc_string")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private final DocString docString = null;
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  private final List<Hook> before = new ArrayList<>();
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  private final List<Hook> after = new ArrayList<>();
  @JsonProperty("name")
  private final String name = null;
  @JsonProperty("line")
  private final Integer line = null;
  @JsonProperty("comments")
  @JsonDeserialize(using = CommentsDeserializer.class)
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  private final List<String> comments = new ArrayList<>();
  @JsonIgnore
  private Status beforeStatus;
  @JsonIgnore
  private Status afterStatus;

  public Step() {
  }

  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  public List<Row> getRows() {
    int argumentsSize = this.arguments.size();
    if (argumentsSize == 1) {
      return this.arguments.get(0).getRows();
    } else if (argumentsSize > 1) {
      throw new UnsupportedOperationException("'arguments' length should be equal to 1");
    } else {
      return this.rows;
    }
  }

  public String getName() {
    return this.name;
  }

  public String getKeyword() {
    return this.keyword.trim();
  }

  public Integer getLine() {
    return this.line;
  }

  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  public List<Output> getOutputs() {
    return this.outputs;
  }

  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  public List<Argument> getArguments() {
    return this.arguments;
  }

  public Match getMatch() {
    return this.match;
  }

  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  public List<Embedding> getEmbeddings() {
    return this.embeddings;
  }

  public Result getResult() {
    return this.result;
  }

  @JsonIgnore
  public long getDuration() {
    return this.result.getDuration();
  }

  @JsonProperty("doc_string")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public DocString getDocString() {
    return this.docString;
  }

  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  public List<Hook> getBefore() {
    return this.before;
  }

  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  public List<Hook> getAfter() {
    return this.after;
  }

  @JsonIgnore
  public Status getBeforeStatus() {
    if (this.beforeStatus == null) {
      setMetaData();
    }
    return this.beforeStatus;
  }

  @JsonIgnore
  public Status getAfterStatus() {
    if (this.afterStatus == null) {
      setMetaData();
    }
    return this.afterStatus;
  }

  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  public List<String> getComments() {
    return this.comments;
  }

  public void setMetaData() {
    this.beforeStatus =
        (new StatusCounter(this.before.toArray(new Resultsable[0]))).getFinalStatus();
    this.afterStatus =
        (new StatusCounter(this.after.toArray(new Resultsable[0]))).getFinalStatus();
  }
}
