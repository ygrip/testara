package io.github.ygrip.testara.ui.populator;

import static io.github.ygrip.testara.core.support.CommonHelper.isBlank;

import java.time.Duration;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import io.github.ygrip.testara.core.concurrency.ExecutorFactory;
import io.github.ygrip.testara.core.mapper.MapperHelper;
import io.github.ygrip.testara.ui.driver.DriverInstances;
import io.github.ygrip.testara.ui.driver.DriverSession;
import io.github.ygrip.testara.ui.driver.DriverSessionManager;
import io.github.ygrip.testara.ui.executor.Actor;
import io.github.ygrip.testara.ui.executor.ActorManager;
import io.github.ygrip.testara.ui.page.Element;

import lombok.extern.log4j.Log4j2;

/**
 * <p>MultipleElementPopulator class.</p>
 *
 * @author yunaz.ramadhan on 12/29/2019
 * @version $Id: $Id
 */
@Log4j2
public final class MultipleElementPopulator extends BasicElementPopulator<MultipleElementPopulator> {

  /**
   * Parallelism: min 1, max 2 * CPU cores.
   */
  private static final int PARALLELISM = Math.max(
    1,
    2 * Runtime.getRuntime()
      .availableProcessors()
  );

  /**
   * Dedicated ForkJoinPool for per-element resolution. Not shared with BasicElementPopulator.
   * Parallelism is min 1, max 2 * CPU cores.
   */
  private static final ForkJoinPool ELEMENT_POOL =
    ExecutorFactory.createSafeForkJoinPool(PARALLELISM, "multi-element-populator");

  private final static Duration DEFAULT_TIMEOUT = Duration.ofSeconds(3);
  private final AtomicInteger processIndex;
  private Integer skip;
  private Integer limit;

  MultipleElementPopulator(Actor actor, DriverSession<?> session, Element target) {
    super(actor, session, target);
    this.skip = 0;
    this.limit = 0;
    processIndex = new AtomicInteger(0);
  }

  /**
   * <p>limitBy.</p>
   *
   * @param limit a int.
   * @return a {@link MultipleElementPopulator} object.
   */
  public MultipleElementPopulator limitBy(int limit) {
    this.limit = limit;
    return this;
  }

  /**
   * <p>skipBy.</p>
   *
   * @param skip a int.
   * @return a {@link MultipleElementPopulator} object.
   */
  public MultipleElementPopulator skipBy(int skip) {
    this.skip = skip;
    return this;
  }

  @Override
  List<Object> resolve() throws Exception {
    Element scope = target();
    List<?> elements = scope.all(DEFAULT_TIMEOUT);
    elements = elements.stream()
      .skip(Math.max(this.skip, 0))
      .limit(this.limit > 0 ? this.limit : elements.size())
      .collect(Collectors.toList());
    List<Object> results = new ArrayList<>();
    if (!isBlank(elements)) {
      performPreConditionActions();
      List<Map.Entry<Integer, ?>> elementsWithId = elements.stream()
        .map(this::addIdentifierToItem)
        .collect(Collectors.toList());

      // Resolve each element in parallel using dedicated ForkJoinPool (not shared executor).
      // Parallelism is bounded by ELEMENT_POOL (min 1, max 2 * CPU cores).
      // Propagate DriverSessionManager and ActorManager ThreadLocal context to worker threads.
      final DriverInstances callerInstances = DriverSessionManager.getInstances();
      final Map<String, Actor> callerActors = ActorManager.getActors();
      List<CompletableFuture<AbstractMap.SimpleEntry<Integer, Object>>> futures = elementsWithId.stream()
        .map(item -> CompletableFuture.supplyAsync(
          () -> {
            DriverInstances prev = DriverSessionManager.getInstances();
            Map<String, Actor> prevActors = ActorManager.getActors();
            DriverSessionManager.bindToCurrentThread(callerInstances);
            ActorManager.bindToCurrentThread(callerActors);
            try {
              return new AbstractMap.SimpleEntry<>(
                item.getKey(),
                process(Element.instance(finder(), pageContext(), item.getValue()))
              );
            } finally {
              DriverSessionManager.bindToCurrentThread(prev);
              ActorManager.bindToCurrentThread(prevActors);
            }
          }, ELEMENT_POOL
        ))
        .toList();
      CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
        .join();

      results.addAll(futures.stream()
        .map(CompletableFuture::join)
        .sorted(Map.Entry.comparingByKey())
        .map(Map.Entry::getValue)
        .toList());

      performPostConditionActions();
    }
    return results;
  }

  private Map.Entry<Integer, ?> addIdentifierToItem(Object item) {
    return new AbstractMap.SimpleEntry<>(processIndex.getAndIncrement(), item);
  }

  /**
   * <p>resolveAs.</p>
   *
   * @param reference a {@link Class} object.
   * @param <T>       a T object.
   * @return a {@link List} object.
   */
  public <T> List<T> resolveAs(Class<T> reference) throws Exception {
    return MapperHelper.toObject(
      resolve(), new TypeReference<>() {
      }
    );
  }

  /**
   * <p>resolveAs.</p>
   *
   * @param reference a {@link TypeReference} object.
   * @param <T>       a T object.
   * @return a {@link List} object.
   */
  public <T> List<T> resolveAs(TypeReference<T> reference) throws Exception {
    return MapperHelper.toObject(
      resolve(), new TypeReference<>() {
      }
    );
  }
}
