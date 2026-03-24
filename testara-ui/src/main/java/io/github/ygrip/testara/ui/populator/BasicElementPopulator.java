package io.github.ygrip.testara.ui.populator;

import static io.github.ygrip.testara.core.support.CommonHelper.isBlank;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.commons.lang3.ObjectUtils;

import io.github.ygrip.testara.ui.driver.DriverInstances;
import io.github.ygrip.testara.ui.driver.DriverSession;
import io.github.ygrip.testara.ui.driver.DriverSessionManager;
import io.github.ygrip.testara.ui.executor.Actor;
import io.github.ygrip.testara.ui.executor.ActorManager;
import io.github.ygrip.testara.ui.interaction.Interaction;
import io.github.ygrip.testara.ui.observation.Observation;
import io.github.ygrip.testara.ui.page.Element;
import io.github.ygrip.testara.ui.page.PageContext;
import io.github.ygrip.testara.ui.page.PageFinder;
import jakarta.validation.constraints.NotNull;
import lombok.extern.log4j.Log4j2;

/**
 * <p>Abstract BasicElementPopulator class.</p>
 *
 * @author yunaz.ramadhan on 12/29/2019
 * @version $Id: $Id
 */
@Log4j2
public abstract class BasicElementPopulator<P extends BasicElementPopulator<?>> {

  /**
   * Max concurrent resolution tasks. Caps load on the browser and JDK HttpClient
   * to avoid "unable to create native thread" / "selector manager closed" when
   * resolving large lists (generations × cards × keys).
   */
  private static final int POPULATOR_CONCURRENCY = Math.min(
    16,
    Runtime.getRuntime()
      .availableProcessors() * 4
  );

  /**
   * Bounded virtual-thread executor: fixed pool of virtual threads so only
   * POPULATOR_CONCURRENCY tasks run at once. Keeps parallelism without overwhelming
   * the browser or native thread usage.
   */
  private static final ExecutorService POPULATOR_EXECUTOR =
    Executors.newFixedThreadPool(POPULATOR_CONCURRENCY, virtualThreadFactory());
  private final Map<String, Populator> mappedFunction;
  private final Map<ActionState, List<Action>> actions;
  private final Actor actor;
  @SuppressWarnings("rawtypes")
  private final Element target;
  private PageFinder<?, Object, ?> finder;
  private PageContext<?> pageContext;
  private Populator function;
  @SuppressWarnings("rawtypes")
  private Element parent;
  @SuppressWarnings("rawtypes")
  private Element current;

  @SuppressWarnings({"rawtypes"})
  BasicElementPopulator(@NotNull Actor actor, @NotNull DriverSession<?> session, Element target) {
    this.target = target;
    this.mappedFunction = new HashMap<>();
    this.actions = new HashMap<>();
    this.actor = actor;
    this.finder = session.finder();
    this.pageContext = finder.getCurrentPage();
  }

  private static ThreadFactory virtualThreadFactory() {
    return Thread.ofVirtual()
      .name("populator-", 0)
      .factory();
  }

  /**
   * Shared executor for use by subclasses (e.g. parallel per-element resolution).
   */
  protected static ExecutorService populatorExecutor() {
    return POPULATOR_EXECUTOR;
  }

  protected PageFinder<?, Object, ?> finder() {
    return finder;
  }

  protected PageContext<?> pageContext() {
    return pageContext;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  protected Element target() {
    if (ObjectUtils.isNotEmpty(parent)) {
      return parent
        .withChild(target.using(finder)
          .onPage(pageContext))
        .child();
    }
    return target.using(finder)
      .onPage(pageContext);
  }

  @SuppressWarnings("rawtypes")
  protected Element current() {
    return current;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  protected P setCurrent(Element current) {
    this.current = current.using(finder)
      .onPage(pageContext);
    return (P) this;
  }

  protected Actor actor() {
    return this.actor;
  }

  void setFunction(Populator populator) {
    this.function = populator;
  }

  void addMappedFunction(String key, Populator populator) {
    this.mappedFunction.put(key, populator);
  }

  /**
   * <p>Getter for the field <code>currentElement</code>.</p>
   *
   * @return a {@link Object} object.
   */
  @SuppressWarnings("rawtypes")
  protected Element getParent() {
    return this.parent;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  protected P setParent(Element element) {
    this.parent = element.using(finder)
      .onPage(pageContext);
    return (P) this;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private Object processPopulator(Element element, String mappedKey, Populator entry) {
    Object result = null;
    boolean useFallback = false;
    String keyLabel = mappedKey != null ? "'" + mappedKey + "'" : "(single)";
    try {
      result = doResolvePopulator(element, entry.getPopulator());
    } catch (Exception e) {
      log.warn(
        "Cannot fetch the result for key {} populator {}: {}",
        keyLabel,
        entry.getPopulator()
          .getClass()
          .getSimpleName(),
        e.getMessage(),
        e
      );
      useFallback = true;
    }

    if (useFallback && !isBlank(entry.getFallback())) {
      log.debug(
        "Try fallback {} for key {}",
        entry.getFallback()
          .getClass()
          .getSimpleName(),
        keyLabel
      );
      try {
        result = doResolvePopulator(element, entry.getFallback());
      } catch (Exception e) {
        log.warn(
          "Cannot fetch the result from fallback for key {} populator {}: {}",
          keyLabel,
          entry.getFallback()
            .getClass()
            .getSimpleName(),
          e.getMessage(),
          e
        );
      }
    }

    if (!isBlank(entry.getTransformer())) {
      try {
        if (entry.getTransformer() instanceof Function) {
          result = ((Function<Object, ?>) entry.getTransformer()).apply(result);
        } else if (entry.getTransformer() instanceof Supplier) {
          result = ((Supplier<?>) entry.getTransformer()).get();
        }
      } catch (Exception error) {
        log.warn("Error when applying transformer for key {}: {}", keyLabel, error.getMessage(), error);
        result = null;
      }
    }
    return result;
  }

  private BasicElementPopulator<P> withPage(PageContext<?> pageContext) {
    this.pageContext = pageContext;
    return this;
  }

  private BasicElementPopulator<P> withFinder(PageFinder<?, Object, ?> finder) {
    this.finder = finder;
    return this;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private Object doResolvePopulator(Element element, Object entry) throws Exception {
    if (entry instanceof ElementResolver resolver) {
      return resolver.setTarget(element.using(finder)
          .onPage(pageContext))
        .result(actor);
    } else if (entry instanceof BasicElementPopulator populator) {
      try {
        return populator.withFinder(finder)
          .withPage(pageContext)
          .setParent(element)
          .resolve();
      } catch (Exception e) {
        e.printStackTrace();
        throw new IllegalStateException(
          "Nested " + populator.getClass()
            .getSimpleName() + " failed with parent scope (parent has locator=" + (element != null
            && element.getLocator() != null) + ")", e
        );
      }
    } else if (entry instanceof Observation<?> observation) {
      return actor.observe(observation.root(element.using(finder)
        .onPage(pageContext)));
    }
    return null;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  protected Object process(Element element) {
    setCurrent(element.using(finder)
      .onPage(pageContext));
    performActions();
    if (!isBlank(this.mappedFunction)) {
      return processMappedFunctionsInParallel(element.using(finder)
        .onPage(pageContext));
    }
    if (!isBlank(this.function)) {
      return processPopulator(element.using(finder)
        .onPage(pageContext), null, this.function);
    }
    return null;
  }

  /**
   * Resolves all mapped keys in parallel using virtual threads.
   * Preserves key order in the result map.
   * Propagates DriverSessionManager and ActorManager ThreadLocal context to worker threads.
   */
  @SuppressWarnings("rawtypes")
  private Map<String, Object> processMappedFunctionsInParallel(Element element) {
    List<Map.Entry<String, Populator>> entries = new ArrayList<>(this.mappedFunction.entrySet());
    final DriverInstances callerInstances = DriverSessionManager.getInstances();
    final Map<String, Actor> callerActors = ActorManager.getActors();
    List<CompletableFuture<Map.Entry<String, Object>>> futures = entries.stream()
      .map(entry -> CompletableFuture.<Map.Entry<String, Object>>supplyAsync(
        () -> {
          DriverInstances prev = DriverSessionManager.getInstances();
          Map<String, Actor> prevActors = ActorManager.getActors();
          DriverSessionManager.bindToCurrentThread(callerInstances);
          ActorManager.bindToCurrentThread(callerActors);
          try {
            return new AbstractMap.SimpleEntry<>(
              entry.getKey(),
              processPopulator(element, entry.getKey(), entry.getValue())
            );
          } finally {
            DriverSessionManager.bindToCurrentThread(prev);
            ActorManager.bindToCurrentThread(prevActors);
          }
        }, POPULATOR_EXECUTOR
      ))
      .toList();
    CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
      .join();
    Map<String, Object> result = new HashMap<>(entries.size());
    for (CompletableFuture<Map.Entry<String, Object>> f : futures) {
      Map.Entry<String, Object> e = f.join();
      if (e != null) {
        result.put(e.getKey(), e.getValue());
      }
    }
    return result;
  }

  void performPreConditionActions() {
    final var actions = getActions(ActionState.PRE_ACTION);
    if (!isBlank(actions)) {
      actor.attemptsTo(actions.stream()
        .map(Action::getAction)
        .map(interaction -> interaction.root(target()))
        .toArray(Interaction[]::new));
    }
  }

  void performActions() {
    final var actions = getActions(ActionState.DEFAULT);
    if (!isBlank(actions)) {
      actor.attemptsTo(actions.stream()
        .map(Action::getAction)
        .map(interaction -> interaction.root(target()))
        .toArray(Interaction[]::new));
    }
  }

  void performPostConditionActions() {
    final var actions = getActions(ActionState.POST_ACTION);
    if (!isBlank(actions)) {
      actor.attemptsTo(actions.stream()
        .map(Action::getAction)
        .map(interaction -> interaction.root(target()))
        .toArray(Interaction[]::new));
    }
  }

  abstract Object resolve() throws Exception;

  void addAction(Action action) {
    if (!isBlank(action) && !isBlank(action.getAction())) {
      ActionState state = isBlank(action.getState()) ? ActionState.DEFAULT : action.getState();
      List<Action> listedAction = this.actions.getOrDefault(state, new ArrayList<>());
      listedAction.add(action);
      this.actions.put(state, listedAction);
    }
  }

  /**
   * <p>performBefore.</p>
   *
   * @return a {@link P} object.
   */
  public P performBefore(Interaction interaction) {
    return new Action(ActionState.PRE_ACTION, interaction).build();
  }

  /**
   * <p>perform.</p>
   *
   * @return a {@link P} object.
   */
  public P perform(Interaction interaction) {
    return new Action(interaction).build();
  }

  /**
   * <p>performAfter.</p>
   *
   * @return a {@link P} object.
   */
  public P performAfter(Interaction interaction) {
    return new Action(ActionState.POST_ACTION, interaction).build();
  }

  /**
   * <p>set.</p>
   *
   * @return a {@link Populator} object.
   */
  public Populator set() {
    return new Populator();
  }

  /**
   * <p>set.</p>
   *
   * @param identifier a {@link String} object.
   * @return a {@link Populator} object.
   */
  public Populator set(String identifier) {
    return new Populator(identifier);
  }

  /**
   * Set a single value (no key). Use for populators that resolve to one value per element.
   * Equivalent to {@code set().with(value).build()}.
   *
   * @param value resolver, observation, or nested populator
   * @return this builder
   */
  public P set(Object value) {
    Populator p = new Populator();
    assignValue(p, value);
    return p.build();
  }

  /**
   * Set one key-value pair. Equivalent to {@code set(key).with(value).build()}.
   *
   * @param key   result key
   * @param value resolver, observation, or nested populator
   * @return this builder
   */
  public P set(String key, Object value) {
    Populator p = new Populator(key);
    assignValue(p, value);
    return p.build();
  }

  /**
   * Set multiple key-value pairs. Keys and values must alternate; total args must be even.
   * Example: {@code set("name", nameResolver, "link", linkResolver, "image", imageResolver)}.
   *
   * @param k1   first key
   * @param v1   first value
   * @param k2   second key
   * @param v2   second value
   * @param rest alternating key, value (must be even length)
   * @return this builder
   */
  @SuppressWarnings("unchecked")
  public P set(String k1, Object v1, String k2, Object v2, Object... rest) {
    set(k1, v1);
    set(k2, v2);
    if (rest != null && rest.length > 0) {
      if (rest.length % 2 != 0) {
        throw new IllegalArgumentException("rest must be alternating key, value (even length), got " + rest.length);
      }
      for (int i = 0; i < rest.length; i += 2) {
        if (!(rest[i] instanceof String)) {
          throw new IllegalArgumentException("Key at index " + i + " must be String, got " + (rest[i] == null ?
            "null" :
            rest[i].getClass()
              .getName()));
        }
        set((String) rest[i], rest[i + 1]);
      }
    }
    return (P) this;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private void assignValue(Populator p, Object value) {
    switch (value) {
      case BasicElementPopulator populator -> p.with((BasicElementPopulator<?>) populator.withFinder(this.finder)
        .withPage(this.pageContext));
      case ElementResolver resolver -> p.with(resolver);
      case Observation observation -> p.with(observation);
      default -> throw new IllegalArgumentException(
        "value must be ElementResolver, Observation, or BasicElementPopulator, got " + value.getClass()
          .getName());
    }
  }

  List<Action> getActions(ActionState state) {
    return this.actions.getOrDefault(state, new ArrayList<>());
  }

  /**
   * <p>build.</p>
   *
   * @return a TYPE object.
   */
  @SuppressWarnings("unchecked")
  public P build() {
    return (P) this;
  }

  /**
   * <p>andThen.</p>
   *
   * @return a TYPE object.
   */
  public P andThen() {
    return build();
  }

  protected enum ActionState {
    PRE_ACTION, POST_ACTION, DEFAULT
  }


  public class Action {
    private final ActionState state;
    private final Interaction action;

    Action(Interaction action) {
      this.state = ActionState.DEFAULT;
      this.action = action;
    }

    Action(ActionState state, Interaction action) {
      this.state = state;
      this.action = action;
    }

    protected ActionState getState() {
      return this.state;
    }

    protected Interaction getAction() {
      return this.action;
    }

    private P build() {
      BasicElementPopulator.this.addAction(this);
      return BasicElementPopulator.this.andThen();
    }
  }


  public class Populator {
    private final String identifier;
    private Object populator;
    private Object fallback;
    private Object transformer;

    Populator() {
      this.identifier = null;
    }

    Populator(String identifier) {
      this.identifier = identifier;
    }

    protected Object getPopulator() {
      return this.populator;
    }

    protected Object getFallback() {
      return this.fallback;
    }

    protected Object getTransformer() {
      return this.transformer;
    }

    public Populator with(BasicElementPopulator<?> value) {
      this.populator = value.withFinder(finder)
        .withPage(pageContext);
      return this;
    }

    public Populator with(ElementResolver value) {
      this.populator = value;
      return this;
    }

    public Populator with(Observation<?> value) {
      this.populator = value;
      return this;
    }

    public Populator orWith(BasicElementPopulator<?> value) {
      this.fallback = value;
      return this;
    }

    public Populator orWith(ElementResolver value) {
      this.fallback = value;
      return this;
    }

    public Populator orWith(Observation<?> value) {
      this.fallback = value;
      return this;
    }

    public Populator into(Supplier<?> value) {
      this.transformer = value;
      return this;
    }

    public Populator into(Function<? super Object, ?> value) {
      this.transformer = value;
      return this;
    }

    public P andThen() {
      return build();
    }

    public P build() {
      if (isBlank(this.identifier)) {
        BasicElementPopulator.this.setFunction(this);
      } else {
        BasicElementPopulator.this.addMappedFunction(this.identifier, this);
      }
      return BasicElementPopulator.this.andThen();
    }
  }
}
