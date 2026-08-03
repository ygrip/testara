package io.github.ygrip.testara.ui.interaction;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import io.github.ygrip.testara.ui.capability.AssertionCapability;
import io.github.ygrip.testara.ui.capability.InteractionCapability;
import io.github.ygrip.testara.ui.capability.NavigationCapability;
import io.github.ygrip.testara.ui.capability.ObservationCapability;
import io.github.ygrip.testara.ui.capability.WaitCapability;
import io.github.ygrip.testara.ui.page.Element;
import io.github.ygrip.testara.ui.page.NamedPage;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression coverage for {@link WaitUntil#withTimeout(Duration)}: the URL captured by
 * {@link WaitUntil#url(String)} must survive rebuilding the instance with a new timeout,
 * instead of being dropped in favor of a null url.
 */
class WaitUntilUrlTimeoutTest {

  static final class RecordingWaitCapability implements WaitCapability {
    final AtomicReference<String> capturedUrl = new AtomicReference<>();

    @Override public WaitCapability withTimeout(Duration duration) { return this; }
    @Override public WaitPage untilPageLoaded(NamedPage namedPage) { return duration -> this; }

    @Override
    public WaitPage untilUrlContains(String url) {
      capturedUrl.set(url);
      return duration -> this;
    }

    @Override public WaitCapability untilSelected(Element locator) { return this; }
    @Override public WaitCapability untilVisible(Element locator) { return this; }
    @Override public WaitCapability untilInvisible(Element locator) { return this; }
    @Override public WaitCapability untilClickable(Element locator) { return this; }
    @Override public WaitCapability untilPresent(Element locator) { return this; }
    @Override public WaitCapability untilEnabled(Element locator) { return this; }
    @Override public WaitCapability untilDisabled(Element locator) { return this; }
    @Override public WaitCapability forDuration(Duration duration) { return this; }
  }

  static final class SingleCapabilityContext implements InteractionContext {
    private final WaitCapability waitCapability;

    SingleCapabilityContext(WaitCapability waitCapability) {
      this.waitCapability = waitCapability;
    }

    @Override public InteractionCapability interaction() { throw new UnsupportedOperationException(); }
    @Override public ObservationCapability observation() { throw new UnsupportedOperationException(); }
    @Override public NavigationCapability navigation() { throw new UnsupportedOperationException(); }
    @Override public AssertionCapability assertion() { throw new UnsupportedOperationException(); }
    @Override public WaitCapability waits() { return waitCapability; }
    @Override public <D> D session() { throw new UnsupportedOperationException(); }
  }

  @Test
  void withTimeoutPreservesUrl() {
    RecordingWaitCapability waitCapability = new RecordingWaitCapability();
    SingleCapabilityContext context = new SingleCapabilityContext(waitCapability);

    WaitUntil.url("/foo")
      .withTimeout(Duration.ofSeconds(3))
      .perform(context);

    assertEquals("/foo", waitCapability.capturedUrl.get());
  }
}
