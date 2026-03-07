package io.github.ygrip.testara.reporter.cucumber;


import java.io.Serializable;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Set;

public class StatusCounter implements Serializable {
  private final EnumMap<Status, Integer> counter;
  private Status finalStatus;
  private int size;

  public StatusCounter(Resultsable[] resultsables) {
    this(resultsables, Collections.emptySet());
  }

  public StatusCounter(Resultsable[] resultsables, Set<Status> notFailingStatuses) {
    this();
    for (Resultsable result : resultsables) {
      Status status = result.getResult().getStatus();
      if (notFailingStatuses != null && notFailingStatuses.contains(status)) {
        this.incrementFor(Status.PASSED);
      } else {
        this.incrementFor(status);
      }
    }

  }

  public StatusCounter() {
    this.counter = new EnumMap<>(Status.class);
    this.finalStatus = Status.PASSED;
    this.size = 0;
    Status[] statuses = Status.values();

    for (Status status : statuses) {
      this.counter.put(status, 0);
    }

  }

  public void incrementFor(Status status) {
    int statusCounter = this.getValueFor(status) + 1;
    this.counter.put(status, statusCounter);
    ++this.size;
    if (this.finalStatus == Status.PASSED && status != Status.PASSED) {
      this.finalStatus = Status.FAILED;
    }

  }

  public int getValueFor(Status status) {
    if (this.counter == null) {
      return 0;
    }
    return this.counter.get(status);
  }

  public int size() {
    return this.size;
  }

  public Status getFinalStatus() {
    return this.finalStatus;
  }
}
