package io.github.ygrip.testara.ui.interaction;

import io.github.ygrip.testara.ui.capability.AssertionCapability;
import io.github.ygrip.testara.ui.capability.InteractionCapability;
import io.github.ygrip.testara.ui.capability.NavigationCapability;
import io.github.ygrip.testara.ui.capability.ObservationCapability;
import io.github.ygrip.testara.ui.capability.WaitCapability;

/**
 * Provides access to capabilities required by the Screenplay layer (plan §5.3).
 * Method names align with plan: navigation(), interaction(), assertion(), waits().
 */
public interface InteractionContext {
  InteractionCapability interaction();

  ObservationCapability observation();

  NavigationCapability navigation();

  AssertionCapability assertion();

  WaitCapability waits();

  <D> D session();
}
