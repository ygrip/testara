package io.github.ygrip.testara.ui.interaction;

import io.github.ygrip.testara.ui.capability.AssertionCapability;
import io.github.ygrip.testara.ui.capability.InteractionCapability;
import io.github.ygrip.testara.ui.capability.NavigationCapability;
import io.github.ygrip.testara.ui.capability.ObservationCapability;
import io.github.ygrip.testara.ui.capability.WaitCapability;
import io.github.ygrip.testara.ui.driver.DriverSession;
import io.github.ygrip.testara.ui.executor.Actor;

/**
 * {@link InteractionContext} backed by a {@link DriverSession}. Resolves capabilities from the
 * session so the Screenplay layer (Actor, Interactions) can run against the current driver.
 */
public final class SessionInteractionContext implements InteractionContext {
  private final DriverSession<?> session;

  private SessionInteractionContext(DriverSession<?> session) {
    this.session = session;
  }

  /**
   * Build an interaction context from the given session. Use this with {@link Actor#with(DriverSession)}
   * or pass to {@link Actor#using(InteractionContext)}.
   */
  public static InteractionContext from(DriverSession<?> session) {
    if (session == null) {
      throw new IllegalArgumentException("session cannot be null");
    }
    return new SessionInteractionContext(session);
  }

  @Override
  public InteractionCapability interaction() {
    return session.capability(InteractionCapability.class);
  }

  @Override
  public ObservationCapability<?> observation() {
    return session.capability(ObservationCapability.class);
  }

  @Override
  public NavigationCapability navigation() {
    return session.capability(NavigationCapability.class);
  }

  @Override
  public AssertionCapability assertion() {
    return session.capability(AssertionCapability.class);
  }

  @Override
  public WaitCapability waits() {
    return session.capability(WaitCapability.class);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <D> D session() {
    return (D) session;
  }
}
