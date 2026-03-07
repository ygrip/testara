package io.github.ygrip.testara.database.support;

import io.github.ygrip.testara.core.model.ValueUnit;
import io.github.ygrip.testara.core.time.DurationParser;
import io.github.ygrip.testara.database.model.MultiResultSubscriber;
import io.github.ygrip.testara.database.model.SingleResultSubscriber;
import org.reactivestreams.Publisher;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class AwaitStream {
  private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

  public static <T> T one(Publisher<T> publisher) {
    return one(publisher, DEFAULT_TIMEOUT);
  }

  public static <T> T one(Publisher<T> publisher, Duration timeout) {
    CompletableFuture<T> future = new CompletableFuture<>();
    publisher.subscribe(new SingleResultSubscriber<>(future));
    try {
      ValueUnit valueUnit = DurationParser.toValueUnit(timeout);
      return future.get(valueUnit.getValue(), valueUnit.getUnit());
    } catch (Exception ignored) {
      return null;
    }
  }

  public static <T> List<T> many(Publisher<T> publisher) {
    return many(publisher, DEFAULT_TIMEOUT);
  }

  public static <T> List<T> many(Publisher<T> publisher, Duration timeout) {
    CompletableFuture<List<T>> future = new CompletableFuture<>();
    publisher.subscribe(new MultiResultSubscriber<>(future));
    try {
      ValueUnit valueUnit = DurationParser.toValueUnit(timeout);
      return future.get(valueUnit.getValue(), valueUnit.getUnit());
    } catch (Exception e) {
      return null;
    }
  }
}
