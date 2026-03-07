package io.github.ygrip.testara.database.model;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class MultiResultSubscriber<T> implements Subscriber<T> {
  private final CompletableFuture<List<T>> future;
  private final List<T> results = new ArrayList<>();

  public MultiResultSubscriber(CompletableFuture<List<T>> future) {
    this.future = future;
  }

  @Override
  public void onSubscribe(Subscription s) {
    // Request an unbounded number of items (safe because Mongo reactive streams are finite)
    s.request(Long.MAX_VALUE);
  }

  @Override
  public void onNext(T item) {
    results.add(item);
  }

  @Override
  public void onError(Throwable t) {
    future.completeExceptionally(t);
  }

  @Override
  public void onComplete() {
    future.complete(results);
  }
}

