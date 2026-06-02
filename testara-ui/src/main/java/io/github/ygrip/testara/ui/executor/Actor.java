package io.github.ygrip.testara.ui.executor;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import io.github.ygrip.testara.ui.driver.DriverSession;
import io.github.ygrip.testara.ui.driver.DriverSessionManager;
import io.github.ygrip.testara.ui.interaction.Interaction;
import io.github.ygrip.testara.ui.interaction.InteractionContext;
import io.github.ygrip.testara.ui.interaction.SessionInteractionContext;
import io.github.ygrip.testara.ui.observation.Observation;
import io.github.ygrip.testara.ui.page.PageContext;

import lombok.extern.log4j.Log4j2;

/**
 * Screenplay-style actor: executes {@link Interaction}s using an {@link InteractionContext}
 * (plan §5.1). Obtain context from the current session or pass one explicitly.
 *
 * <p>Usage with current driver session:
 * <pre>{@code
 * Actor.attemptsTo(
 *   Navigate.to("https://example.com/login"),
 *   Enter.text("admin").into("#username"),
 *   Enter.text("secret").into("#password"),
 *   Click.on("#submit")
 * );
 * }</pre>
 *
 * <p>Or with an explicit session/context:
 * <pre>{@code
 * try (DriverSession<?> session = AutomationSession.with(SeleniumConfig.chrome().build()).create()) {
 *   Actor.using(SessionInteractionContext.from(session))
 *     .attemptsTo(Navigate.to("/"), Click.on("#start"));
 * }
 * }</pre>
 */
@Log4j2
public final class Actor {
  private final InteractionContext context;

  private Actor(InteractionContext context) {
    this.context = context;
  }

  /**
   * Create an actor that uses the current session from {@link DriverSessionManager}.
   * Fails if no current driver is registered.
   */
  public static Actor withCurrentSession() {
    DriverSession<?> session = DriverSessionManager.inThisTestThread()
      .getCurrentDriver();
    if (session == null) {
      throw new IllegalStateException("No current driver session. Register a driver with DriverSessionManager first.");
    }
    return using(SessionInteractionContext.from(session));
  }

  /**
   * Create an actor that uses the given session (capabilities resolved from it).
   */
  public static Actor with(DriverSession<?> session) {
    return using(SessionInteractionContext.from(session));
  }

  /**
   * Create an actor that uses the given context.
   */
  public static Actor using(InteractionContext context) {
    if (context == null) {
      throw new IllegalArgumentException("context cannot be null");
    }
    return new Actor(context);
  }

  /**
   * Execute one or more interactions in order. Each {@link Interaction#perform(InteractionContext)}
   * is called with this actor's context.
   */
  public void attemptsTo(Interaction... interactions) {
    for (Interaction interaction : interactions) {
      try {
        interaction.support(context.session());
        interaction.perform(context);
      } catch (Exception err) {
        log.warn(
          "#Interaction skipped for {}, error : {}",
          interaction.getClass()
            .getSimpleName(),
          err.getMessage()
        );
      }
    }
  }

  /**
   * Observe the element and get the return value. An {@link Observation#perform(InteractionContext)}
   * is called with this actor's context.
   */
  public <T> T observe(Observation<T> observation) {
    try {
      observation.support(context.session());
      return observation.perform(context);
    } catch (Exception err) {
      log.warn(
        "#Observation skipped for {}, error : {}",
        observation.getClass()
          .getSimpleName(),
        err.getMessage()
      );
      return null;
    }
  }

  /**
   * Execute task of a matching {@link UserAction} that is resolved by {@link ActionResolver}
   * the task can be scoped on the desired page or can refer to a global task.
   */
  public void executeTask(String task, String pageName, Map<String, Object> additionalParameter) {
    try {
      DriverSession<?> session = context.session();
      PageContext<?> currentPage = (PageContext<?>) Optional.ofNullable(pageName)
        .map(page -> {
          try {
            return session.finder()
              .getPage(page);
          } catch (Exception e) {
            return null;
          }
        })
        .orElseGet(() -> session.finder()
          .getCurrentPage());
      ActionResolver.doActionOnPage(
        task,
        Optional.ofNullable(currentPage)
          .map(Object::getClass)
          .orElse(null),
        additionalParameter
      );
    } catch (Exception err) {
      log.warn("#Task skipped for {}, error : {}", task, err.getMessage());
    }
  }

  public void executeTask(String task, String pageName) {
    executeTask(task, pageName, null);
  }

  public void executeTask(String task) {
    executeTask(task, null, new HashMap<>());
  }

  public void executeTask(String task, Map<String, Object> additionalParameter) {
    executeTask(task, null, additionalParameter);
  }

  /**
   * Check whether a {@link PageContext}
   * is currently the active page within this actor's context
   */
  public boolean isOn(PageContext<?> page) {
    DriverSession<?> session = context.session();
    return session.isOn(page);
  }
}
