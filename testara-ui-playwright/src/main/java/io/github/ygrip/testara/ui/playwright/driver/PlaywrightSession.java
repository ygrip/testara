package io.github.ygrip.testara.ui.playwright.driver;

import java.util.Optional;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.commons.lang3.StringUtils;

import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.core.model.DefaultProperties;
import io.github.ygrip.testara.ui.capability.AssertionCapability;
import io.github.ygrip.testara.ui.capability.InteractionCapability;
import io.github.ygrip.testara.ui.capability.NavigationCapability;
import io.github.ygrip.testara.ui.capability.ObservationCapability;
import io.github.ygrip.testara.ui.capability.WaitCapability;
import io.github.ygrip.testara.ui.driver.CurrentPageHolder;
import io.github.ygrip.testara.ui.driver.DriverInstances;
import io.github.ygrip.testara.ui.driver.DriverSession;
import io.github.ygrip.testara.ui.driver.DriverSessionManager;
import io.github.ygrip.testara.ui.page.PageContext;
import io.github.ygrip.testara.ui.executor.Actor;
import io.github.ygrip.testara.ui.executor.ActorManager;
import io.github.ygrip.testara.ui.model.DeviceType;
import io.github.ygrip.testara.ui.playwright.capability.PlaywrightAssertionCapability;
import io.github.ygrip.testara.ui.playwright.capability.PlaywrightInteractionCapability;
import io.github.ygrip.testara.ui.playwright.capability.PlaywrightNavigationCapability;
import io.github.ygrip.testara.ui.playwright.capability.PlaywrightObservationCapability;
import io.github.ygrip.testara.ui.playwright.capability.PlaywrightWaitCapability;
import io.github.ygrip.testara.ui.playwright.config.PlaywrightDriverProperties;
import io.github.ygrip.testara.ui.playwright.page.PlaywrightPageFinder;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

import lombok.extern.log4j.Log4j2;

/**
 * Serializes all Playwright API usage onto a single background thread. Playwright Java is not
 * thread-safe; calling browser/page APIs from a different thread than {@link Playwright#create()}
 * causes {@code Object doesn't exist: request@...} and broken navigation state (e.g. URL stuck at
 * {@code about:blank}).
 */
@Log4j2
@SuppressWarnings("unchecked")
public final class PlaywrightSession implements DriverSession<Browser> {
  private static final long API_TIMEOUT_MINUTES = 10;

  /**
   * While {@link #runOnApiThread} executes on the Playwright worker thread, code such as
   * {@link io.github.ygrip.testara.ui.page.Element#one()} can run there too. {@link io.github.ygrip.testara.ui.driver.DriverSessionManager}
   * is tied to the test thread, so the finder resolves this session from here instead of
   * {@code getCurrentDriver()} on the API thread.
   */
  private static final ThreadLocal<PlaywrightSession> SESSION_ON_API_THREAD = new ThreadLocal<>();

  public static PlaywrightSession sessionRunningOnThisThread() {
    return SESSION_ON_API_THREAD.get();
  }

  private final ExecutorService apiExecutor = Executors.newSingleThreadExecutor(r -> {
    Thread t = new Thread(r, "testara-playwright-api");
    t.setDaemon(true);
    return t;
  });

  private volatile Thread apiThread;
  private Playwright playwright;
  private Browser driver;
  private DeviceType deviceType;
  private BrowserContext browserContext;
  private Page page;
  private String userAgent;
  private String stealthInitScript;
  private Integer viewportWidth;
  private Integer viewportHeight;
  private Double deviceScaleFactor;
  private Boolean isMobile;
  private Boolean hasTouch;
  private boolean maximized = false;
  private final CurrentPageHolder pageState = new CurrentPageHolder(this);

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
   * Runs browser bootstrap on the Playwright API thread. The runnable must call
   * {@link Playwright#create()}, launch/connect {@link Browser}, {@link #using(Browser)},
   * {@link #withStealthConfig(String, String)}, and {@link #on(DeviceType)}.
   */
  public void runInitializeBlocking(Runnable initializer) {
    runOnApiThread(() -> {
      initializer.run();
      return null;
    });
  }

  /**
   * Run Playwright API work on the session's dedicated thread. Re-entrant from that same thread.
   */
  public <T> T runOnApiThread(Callable<T> action) {
    // Collect test thread context BEFORE switching threads
    String currentName = null;
    try {
      currentName = DriverSessionManager.inThisTestThread().getDriverName(this);
    } catch (Exception ignored) {
    }
    final String sessionName = currentName != null && !currentName.isEmpty() ? currentName : "playwright-api-" + hashCode();
    final DriverInstances callingThreadInstances = DriverSessionManager.getInstances();
    final Map<String, Actor> callingThreadActors = ActorManager.getActors();

    Callable<T> wrapped = () -> {
      if (apiThread == null) {
        apiThread = Thread.currentThread();
      }
      DriverInstances previousInstances = DriverSessionManager.getInstances();
      Map<String, Actor> previousActors = ActorManager.getActors();
      PlaywrightSession previous = SESSION_ON_API_THREAD.get();
      SESSION_ON_API_THREAD.set(this);
      DriverSessionManager.bindToCurrentThread(callingThreadInstances);
      ActorManager.bindToCurrentThread(callingThreadActors);

      // Hydrate DriverSessionManager locally on the API thread
      try {
        var apiInstances = DriverSessionManager.inThisTestThread();
        if (apiInstances.getDriver(sessionName) == null) {
          apiInstances.registerDriver(sessionName).forDriver(this);
          apiInstances.setCurrentActiveDriver(this);
        }
      } catch (Exception ignored) {
      }

      try {
        return action.call();
      } finally {
        DriverSessionManager.bindToCurrentThread(previousInstances);
        ActorManager.bindToCurrentThread(previousActors);
        if (previous != null) {
          SESSION_ON_API_THREAD.set(previous);
        } else {
          SESSION_ON_API_THREAD.remove();
        }
      }
    };
    try {
      if (apiThread != null && Thread.currentThread() == apiThread) {
        return wrapped.call();
      }
      return apiExecutor.submit(wrapped).get(API_TIMEOUT_MINUTES, TimeUnit.MINUTES);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(e);
    } catch (TimeoutException e) {
      throw new RuntimeException("Playwright API call timed out", e);
    } catch (ExecutionException e) {
      Throwable c = e.getCause();
      if (c instanceof RuntimeException re) {
        throw re;
      }
      if (c instanceof Error er) {
        throw er;
      }
      throw new RuntimeException(c);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public void runOnApiThread(Runnable action) {
    runOnApiThread(() -> {
      action.run();
      return null;
    });
  }

  public void retainPlaywright(Playwright playwrightInstance) {
    this.playwright = playwrightInstance;
  }

  /**
   * Active page; only valid on the Playwright API thread or inside {@link #runOnApiThread}.
   */
  public Page pageForApi() {
    if (page == null || page.isClosed()) {
      throw new IllegalStateException(
        "Page is not available or already closed. Session lifecycle is broken."
      );
    }
    return page;
  }

  @Override
  public Browser instance() {
    return driver;
  }

  public void withStealthConfig(String userAgent, String stealthInitScript) {
    this.userAgent = userAgent;
    this.stealthInitScript = stealthInitScript;
  }

  public void setMaximized(boolean isMaximized) {
    this.maximized = isMaximized;
  }

  public void withViewportConfig(int width, int height, Double deviceScaleFactor,
    Boolean isMobile, Boolean hasTouch) {
    this.viewportWidth = width;
    this.viewportHeight = height;
    this.deviceScaleFactor = deviceScaleFactor;
    this.isMobile = isMobile;
    this.hasTouch = hasTouch;
  }

  public void setActivePage(Page newPage) {
    runOnApiThread(() -> {
      if (newPage == null || newPage.isClosed()) {
        throw new IllegalArgumentException("Cannot set a closed/null page");
      }
      if (newPage.context() != browserContext) {
        throw new IllegalStateException("Page belongs to different BrowserContext");
      }
      page = newPage;
      log.debug("Switched active page: {}", newPage.hashCode());
      return null;
    });
  }

  public BrowserContext contextForApi() {
    if (browserContext == null) {
      throw new IllegalStateException("Context not initialized");
    }
    return browserContext;
  }

  private BrowserContext createContext() {
    Browser.NewContextOptions contextOptions = new Browser.NewContextOptions()
      .setLocale("en-US")
      .setTimezoneId(TestFramework.context().get(DefaultProperties.class).getTimeZone());

    boolean explicitViewport = viewportWidth != null && viewportHeight != null;
    if (maximized && !explicitViewport) {
      contextOptions.setViewportSize(null);
    } else {
      int width = Optional.ofNullable(viewportWidth).orElse(1280);
      int height = Optional.ofNullable(viewportHeight).orElse(720);
      contextOptions.setViewportSize(width, height);
    }

    if (StringUtils.isNotBlank(userAgent)) {
      contextOptions.setUserAgent(userAgent);
    }
    if (deviceScaleFactor != null && deviceScaleFactor > 0) {
      contextOptions.setDeviceScaleFactor(deviceScaleFactor);
    }
    if (Boolean.TRUE.equals(isMobile)) {
      contextOptions.setIsMobile(true);
    }
    if (Boolean.TRUE.equals(hasTouch)) {
      contextOptions.setHasTouch(true);
    }

    String viewportDesc = maximized && !explicitViewport
      ? "full-window"
      : Optional.ofNullable(viewportWidth).orElse(1280) + "x" + Optional.ofNullable(viewportHeight).orElse(720);
    log.debug(
      "Creating BrowserContext with viewport={}, mobile={}, touch={}, scaleFactor={}",
      viewportDesc,
      isMobile,
      hasTouch,
      deviceScaleFactor
    );

    return driver.newContext(contextOptions);
  }

  @Override
  public DeviceType platform() {
    return Optional.ofNullable(deviceType).orElse(DeviceType.DEFAULT);
  }

  @Override
  public DriverSession<Browser> using(Browser driver) {
    this.driver = driver;
    return this;
  }

  @Override
  public DriverSession<Browser> on(DeviceType platform) {
    this.deviceType = platform;
    init();
    return this;
  }

  private void init() {
    if (browserContext != null && page != null && !page.isClosed()) {
      return;
    }

    browserContext = createContext();

    // Inject stealth at the context level so it runs for EVERY page/navigation
    // (including new tabs and pop-ups).  context.addInitScript executes before any
    // page script on every new document, whereas page.addInitScript only covers that
    // one Page object and may fire too late on the very first navigation.
    if (StringUtils.isNotBlank(stealthInitScript)) {
      browserContext.addInitScript(stealthInitScript);
    }

    page = browserContext.newPage();

    // Wait for the blank page to be fully ready before handing control to the test.
    // This avoids race conditions where an immediate navigate() arrives while the
    // browser process is still finishing its startup work.
    page.waitForLoadState();

    log.debug("Initialized context={} page={}",
      browserContext.hashCode(),
      page.hashCode());
  }

  @Override
  public <T> T capability(Class<T> capabilityType) {
    if (driver == null) {
      throw new IllegalStateException("Session not initialized: no driver bound");
    }
    runOnApiThread(() -> {
      if (page == null || page.isClosed()) {
        throw new IllegalStateException(
          "Page is not available or already closed. Session lifecycle is broken."
        );
      }
      return null;
    });
    if (capabilityType == NavigationCapability.class) {
      return capabilityType.cast(new PlaywrightNavigationCapability(this));
    }
    if (capabilityType == InteractionCapability.class) {
      return capabilityType.cast(new PlaywrightInteractionCapability(this));
    }
    if (capabilityType == AssertionCapability.class) {
      return capabilityType.cast(new PlaywrightAssertionCapability(this));
    }
    if (capabilityType == WaitCapability.class) {
      return capabilityType.cast(new PlaywrightWaitCapability(this));
    }
    if (capabilityType == ObservationCapability.class) {
      return capabilityType.cast(new PlaywrightObservationCapability(this));
    }
    throw new UnsupportedOperationException("Capability not supported: " + capabilityType.getName());
  }

  @Override
  public Class<PlaywrightDriverProperties> configType() {
    return PlaywrightDriverProperties.class;
  }

  @Override
  @SuppressWarnings("unchecked")
  public PlaywrightPageFinder finder() {
    PlaywrightPageFinder finder;
    try {
      finder = TestFramework.context()
        .get(PlaywrightPageFinder.class);
    } catch (Exception ignored) {
      finder = TestFramework.factory()
        .getInstance(PlaywrightPageFinder.class);
    }
    finder.setDeviceType(platform());
    finder.bindSession(this);
    return finder;
  }

  @Override
  public void close() {
    try {
      if (driver != null || playwright != null) {
        try {
          runOnApiThread(() -> {
            try {
              if (page != null && !page.isClosed()) {
                page.close();
              }
              if (browserContext != null) {
                browserContext.close();
              }
              if (driver != null) {
                driver.close();
              }
            } finally {
              page = null;
              browserContext = null;
              driver = null;
              pageState.clear();
              if (playwright != null) {
                playwright.close();
                playwright = null;
              }
            }
            return null;
          });
        } catch (Exception e) {
          log.warn("Error closing Playwright session: {}", e.getMessage(), e);
        }
      }
    } finally {
      apiExecutor.shutdown();
    }
  }

  @Override
  public boolean isActive() {
    if (driver == null) {
      return false;
    }
    try {
      return Boolean.TRUE.equals(runOnApiThread(() -> driver != null && driver.isConnected()));
    } catch (Exception e) {
      return false;
    }
  }
}
