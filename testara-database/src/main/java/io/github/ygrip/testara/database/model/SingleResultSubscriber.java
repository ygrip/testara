package io.github.ygrip.testara.database.model;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.concurrent.CompletableFuture;

public class SingleResultSubscriber<T> implements Subscriber<T> {
  private final CompletableFuture<T> future;
  private T result;

  public SingleResultSubscriber(CompletableFuture<T> future) {
    this.future = future;
  }

  @Override
  public void onSubscribe(Subscription s) {
    s.request(1);
  }

  @Override
  public void onNext(T t) {
    result = t;
  }

  @Override
  public void onError(Throwable t) {
    future.completeExceptionally(t);
  }

  @Override
  public void onComplete() {
    future.complete(result);
  }
}
