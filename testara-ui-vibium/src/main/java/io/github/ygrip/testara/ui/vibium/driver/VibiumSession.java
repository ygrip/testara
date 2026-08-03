package io.github.ygrip.testara.ui.vibium.driver;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import com.vibium.Browser;
import com.vibium.Page;
import com.vibium.types.ViewportSize;

import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.ui.capability.AssertionCapability;
import io.github.ygrip.testara.ui.capability.InteractionCapability;
import io.github.ygrip.testara.ui.capability.NavigationCapability;
import io.github.ygrip.testara.ui.capability.ObservationCapability;
import io.github.ygrip.testara.ui.capability.WaitCapability;
import io.github.ygrip.testara.ui.driver.CurrentPageHolder;
import io.github.ygrip.testara.ui.driver.DriverSession;
import io.github.ygrip.testara.ui.model.DeviceType;
import io.github.ygrip.testara.ui.page.PageContext;
import io.github.ygrip.testara.ui.vibium.capability.VibiumAssertionCapability;
import io.github.ygrip.testara.ui.vibium.capability.VibiumInteractionCapability;
import io.github.ygrip.testara.ui.vibium.capability.VibiumMobileEmulation;
import io.github.ygrip.testara.ui.vibium.capability.VibiumMobileEmulationImpl;
import io.github.ygrip.testara.ui.vibium.capability.VibiumNavigationCapability;
import io.github.ygrip.testara.ui.vibium.capability.VibiumNetworkSupport;
import io.github.ygrip.testara.ui.vibium.capability.VibiumNetworkSupportImpl;
import io.github.ygrip.testara.ui.vibium.capability.VibiumObservationCapability;
import io.github.ygrip.testara.ui.vibium.capability.VibiumWaitCapability;
import io.github.ygrip.testara.ui.vibium.config.VibiumDriverProperties;
import io.github.ygrip.testara.ui.vibium.config.VibiumViewportSize;
import io.github.ygrip.testara.ui.vibium.error.UnsupportedVibiumCapabilityException;
import io.github.ygrip.testara.ui.vibium.page.VibiumPageFinder;

import lombok.extern.log4j.Log4j2;

/**
 * Owns the raw {@link Browser}, the active Vibium {@link Page}, and this session's Testara
 * current-page state. Two sessions never share a {@link Page} or {@link CurrentPageHolder}.
 */
@Log4j2
public final class VibiumSession implements DriverSession<Browser> {

  private final AtomicBoolean closed = new AtomicBoolean(false);
  private final CurrentPageHolder pageState = new CurrentPageHolder(this);

  private Browser browser;
  private Page activePage;
  private DeviceType deviceType;
  private Map<DeviceType, VibiumViewportSize> viewportProfiles = Collections.emptyMap();
  private VibiumPageFinder finder;
  private boolean remoteConnected;

  @Override
  public PageContext<?> currentPage() {
    return pageState.current();
  }

  @Override
  public void activatePage(PageContext<?> page) {
    pageState.activate(page);
  }

  @Override
  public void clearCurrentPage() {
    pageState.clear();
  }

  /**
   * The raw Vibium page backing this session. For engine/capability internals only.
   */
  public Page pageForApi() {
    if (activePage == null) {
      throw new IllegalStateException(
        "Page is not available. Session lifecycle is broken."
      );
    }
    return activePage;
  }

  @Override
  public Browser instance() {
    return browser;
  }

  @Override
  public DeviceType platform() {
    return Optional.ofNullable(deviceType).orElse(DeviceType.DEFAULT);
  }

  @Override
  public DriverSession<Browser> using(Browser driver) {
    this.browser = driver;
    return this;
  }

  @Override
  public DriverSession<Browser> on(DeviceType platform) {
    this.deviceType = platform;
    init();
    return this;
  }

  /**
   * Bind per-device viewport profiles, resolved from {@link VibiumDriverProperties#getViewport()}.
   * Must be called before {@link #on(DeviceType)} so the initial page picks up the configured
   * viewport for its device type. Fluent, mirrors the existing {@link #using(Browser)}/{@link
   * #on(DeviceType)} builder style.
   */
  public VibiumSession withViewport(Map<DeviceType, VibiumViewportSize> viewportProfiles) {
    this.viewportProfiles = Optional.ofNullable(viewportProfiles)
      .orElse(Collections.emptyMap());
    return this;
  }

  /**
   * Records whether this session's browser was launched via Vibium's remote-connect ({@code
   * StartOptions.connectURL}) rather than a plain local launch. Set once by {@code
   * VibiumEngine#createSession} right after resolving remote-connect configuration; consumed by
   * {@link VibiumNetworkSupportImpl#networkStates()} to report {@code EXTERNAL_PROXY} vs {@code
   * LOCAL_PROXY_UNSUPPORTED} (plan §14) — Testara neither creates nor can verify any proxy already
   * applied to an externally-configured remote endpoint.
   */
  public VibiumSession markRemoteConnected(boolean remoteConnected) {
    this.remoteConnected = remoteConnected;
    return this;
  }

  /** See {@link #markRemoteConnected(boolean)}. */
  public boolean isRemoteConnected() {
    return remoteConnected;
  }

  private void init() {
    if (activePage != null) {
      return;
    }
    activePage = browser.newPage();
    applyViewport(activePage);
    log.debug("Initialized vibium page={}", activePage.hashCode());
  }

  private void applyViewport(Page target) {
    VibiumViewportSize configured = resolveViewport();
    if (configured != null) {
      target.setViewport(new ViewportSize(configured.getWidth(), configured.getHeight()));
      log.debug(
        "Applied viewport {}x{} to vibium page={}",
        configured.getWidth(),
        configured.getHeight(),
        target.hashCode()
      );
    }
  }

  private VibiumViewportSize resolveViewport() {
    DeviceType type = Optional.ofNullable(deviceType)
      .orElse(DeviceType.DEFAULT);
    VibiumViewportSize configured = viewportProfiles.get(type);
    if (configured != null) {
      return configured;
    }
    return viewportProfiles.get(DeviceType.DEFAULT);
  }

  /**
   * Opens a new tab/page on this session's browser, reapplies this session's configured viewport
   * (new tabs do not inherit a previously-set viewport), and makes it the active page. Does NOT
   * touch {@link CurrentPageHolder} — matches the existing (if imperfect) convention set by
   * {@code PlaywrightSession.setActivePage(Page)}, which has the same gap.
   */
  public Page openNewPage() {
    if (browser == null) {
      throw new IllegalStateException("Session not initialized: no browser bound");
    }
    Page newPage = browser.newPage();
    applyViewport(newPage);
    activePage = newPage;
    log.debug("Opened new vibium page={}", newPage.hashCode());
    return newPage;
  }

  /**
   * Switches the active page to a tab already open on this session's browser (e.g. one opened via
   * {@link #openNewPage()} or a popup). Validates the target belongs to this session's browser,
   * then brings it to the front. Does NOT touch {@link CurrentPageHolder} — same convention as
   * {@link #openNewPage()}.
   *
   * <p>{@code Browser.pages()} returns a fresh {@code Page} wrapper object per call (and
   * {@code Page} has no {@code equals()}/{@code hashCode()} override), so membership is checked by
   * {@link Page#id()} rather than {@code List.contains(...)}/object identity — a direct identity
   * check would spuriously fail for every page except the exact Java object still referenced from
   * this session.
   */
  public void switchToPage(Page target) {
    if (target == null) {
      throw new IllegalArgumentException("Cannot switch to a null page");
    }
    if (browser == null) {
      throw new IllegalStateException("Session not initialized: no browser bound");
    }
    boolean belongsToThisBrowser = false;
    for (Page candidate : browser.pages()) {
      if (candidate.id().equals(target.id())) {
        belongsToThisBrowser = true;
        break;
      }
    }
    if (!belongsToThisBrowser) {
      throw new IllegalStateException("Page does not belong to this session's browser");
    }
    activePage = target;
    target.bringToFront();
    log.debug("Switched active vibium page={}", target.hashCode());
  }

  /**
   * Session-owned: constructs exactly one {@link VibiumPageFinder} per session, caching it on
   * this instance rather than fetching it from {@code TestFramework.context()}'s shared
   * TEST-scoped registry. Deliberately does not mirror {@code PlaywrightSession.finder()}'s
   * pattern of resolving a shared, {@code RegistryScope.TEST}-cached finder on every call: that
   * scope is keyed per test/scenario, not per session, so two sessions in one test would silently
   * share (and rebind) the same finder instance. Constructing via {@code TestFramework.factory()}
   * (a plain reflective factory, not a cache) and caching the result here keeps each session's
   * finder — and its {@link io.github.ygrip.testara.ui.page.PageFinder} page/catalog caches —
   * independent.
   */
  @Override
  public VibiumPageFinder finder() {
    if (finder == null) {
      finder = TestFramework.factory()
        .getInstance(VibiumPageFinder.class);
      finder.setDeviceType(platform());
      finder.bindSession(this);
    }
    return finder;
  }

  @Override
  public <T> T capability(Class<T> capabilityType) {
    if (browser == null) {
      throw new IllegalStateException("Session not initialized: no browser bound");
    }
    if (capabilityType == NavigationCapability.class) {
      return capabilityType.cast(new VibiumNavigationCapability(this));
    }
    if (capabilityType == WaitCapability.class) {
      return capabilityType.cast(new VibiumWaitCapability(this));
    }
    if (capabilityType == AssertionCapability.class) {
      return capabilityType.cast(new VibiumAssertionCapability(this));
    }
    if (capabilityType == InteractionCapability.class) {
      return capabilityType.cast(new VibiumInteractionCapability(this));
    }
    if (capabilityType == ObservationCapability.class) {
      return capabilityType.cast(new VibiumObservationCapability(this));
    }
    if (capabilityType == VibiumMobileEmulation.class) {
      return capabilityType.cast(new VibiumMobileEmulationImpl(this));
    }
    if (capabilityType == VibiumNetworkSupport.class) {
      return capabilityType.cast(new VibiumNetworkSupportImpl(this));
    }
    throw new UnsupportedVibiumCapabilityException(
      "capability(" + capabilityType.getSimpleName() + ")",
      "capability adapter not yet implemented for this type"
    );
  }

  @Override
  public Class<VibiumDriverProperties> configType() {
    return VibiumDriverProperties.class;
  }

  @Override
  public void close() {
    if (closed.compareAndSet(false, true)) {
      try {
        if (browser != null) {
          browser.stop();
        }
      } finally {
        activePage = null;
        browser = null;
        finder = null;
        pageState.clear();
      }
    }
  }

  @Override
  public boolean isActive() {
    if (closed.get() || browser == null) {
      return false;
    }
    try {
      // Lightweight real SDK call to confirm the browser process is still responsive.
      browser.pages();
      return true;
    } catch (Exception e) {
      return false;
    }
  }
}
