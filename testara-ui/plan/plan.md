# Unified Automation Framework -- Architecture Plan

## 1. Objective

Build a unified automation framework that supports:

-   Selenium\
-   Playwright\
-   Appium

Using:

-   Nested Factory Pattern\
-   Capability-based abstraction\
-   Screenplay-style interaction model\
-   Strict engine isolation

------------------------------------------------------------------------

## 2. High-Level Architecture

    AutomationSessionFactory
            ↓
       EngineFactory
            ↓
     Engine-Specific BrowserFactory
            ↓
         BrowserSession
            ↓
      InteractionContext
            ↓
           Actor
            ↓
        Interactions

------------------------------------------------------------------------

## 3. Core Components

### 3.1 BrowserSession

**Purpose:** Engine-agnostic runtime container.

**Responsibilities:**

-   Own engine lifecycle\
-   Expose capabilities\
-   Hide engine internals\
-   Implement `AutoCloseable`

**Interface:**

``` java
public interface BrowserSession extends AutoCloseable {
    <T> T capability(Class<T> type);
    void close();
}
```

------------------------------------------------------------------------

## 4. Capability Model

### 4.1 Core Capabilities (All Engines)

-   `NavigationCapability`\
-   `InteractionCapability`\
-   `AssertionCapability`\
-   `WaitCapability`

### 4.2 Optional Capabilities

-   `NetworkCapability` (Playwright only)\
-   `GestureCapability` (Appium only)\
-   `TracingCapability` (Playwright only)

**Rule:**\
Unsupported capabilities must fail fast with
`UnsupportedOperationException`.

------------------------------------------------------------------------

## 5. Screenplay Layer

### 5.1 Actor

Executes interactions:

``` java
actor.attemptsTo(
    Navigate.to("/login"),
    Enter.text("admin").into("#username"),
    Click.on("#submit")
);
```

------------------------------------------------------------------------

### 5.2 Interaction

Command-style object:

``` java
public interface Interaction {
    void perform(InteractionContext context);
}
```

------------------------------------------------------------------------

### 5.3 InteractionContext

Provides access to required capabilities:

``` java
public interface InteractionContext {
    NavigationCapability navigation();
    InteractionCapability interaction();
    AssertionCapability assertion();
    WaitCapability waits();
}
```

------------------------------------------------------------------------

## 6. Nested Factory Structure

### 6.1 Top-Level Factory

**AutomationSessionFactory**

**Responsibility:**

-   Select engine\
-   Delegate to correct `EngineFactory`

------------------------------------------------------------------------

### 6.2 EngineFactory

``` java
public interface EngineFactory {
    BrowserSession createSession(AutomationConfig config);
}
```

Implementations:

-   `SeleniumEngineFactory`\
-   `PlaywrightEngineFactory`\
-   `AppiumEngineFactory`

------------------------------------------------------------------------

### 6.3 Engine-Specific Browser Factory

Each engine contains its own browser factory layer.

#### Selenium

-   `SeleniumBrowserFactory`
    -   `ChromeBrowserFactory`
    -   `FirefoxBrowserFactory`
    -   `EdgeBrowserFactory`

#### Playwright

-   `PlaywrightBrowserFactory`
    -   `ChromiumBrowserFactory`
    -   `FirefoxBrowserFactory`
    -   `WebkitBrowserFactory`

#### Appium

-   `AppiumPlatformFactory`
    -   `AndroidFactory`
    -   `IOSFactory`

**Rule:**

-   Browser factories may create engine internals.\
-   Engine factories must immediately wrap them in `BrowserSession`.\
-   Engine internals must never leak outside the session boundary.

------------------------------------------------------------------------

## 7. Configuration Model

``` java
public sealed interface AutomationConfig
    permits SeleniumConfig, PlaywrightConfig, AppiumConfig {
    AutomationEngine engine();
}
```

Each config must include:

-   Engine type\
-   Browser/platform type\
-   Capabilities/options

------------------------------------------------------------------------

## 8. Selector Strategy

-   CSS-first for web engines\
-   Optional selector DSL for:
    -   accessibility id\
    -   test id\
    -   mobile locators

Selectors must not expose engine-specific syntax.

------------------------------------------------------------------------

## 9. Lifecycle Management

-   `BrowserSession` implements `AutoCloseable`\
-   Support try-with-resources\
-   Future extension: session pooling

------------------------------------------------------------------------

## 10. Design Rules

1.  Tests must never reference:

    -   `WebDriver`
    -   `Page`
    -   `AppiumDriver`

2.  Engine branching must exist in one place only (EngineFactory layer).

3.  Capabilities must remain small and focused.

4.  Interactions describe intent, not mechanics.

5.  Unsupported features must fail fast.

------------------------------------------------------------------------

## 11. Extensibility Goals

Adding a new browser should require:

-   New `BrowserFactory` implementation\
-   Registration in engine registry

Adding a new engine should require:

-   New `EngineFactory`\
-   New `BrowserSession` implementation\
-   Registration in `EngineFactoryRegistry`

No modification of:

-   Screenplay layer\
-   Existing tests\
-   Other engines

------------------------------------------------------------------------

## 12. Deliverables

-   Core module (capabilities + session + screenplay)\
-   Selenium module\
-   Playwright module\
-   Appium module\
-   Example test suite\
-   Architecture documentation\
-   Usage guide

------------------------------------------------------------------------

## 13. Recommended Usage (Entry Point)

Tests must not reference WebDriver, Page, or AppiumDriver. Use the config-based entry point and capabilities only:

``` java
// Recommended: engine-agnostic, try-with-resources
try (BrowserSession session = AutomationSession.with(SeleniumConfig.chrome().build()).create()) {
  NavigationCapability nav = session.capability(NavigationCapability.class);
  WaitCapability wait = session.capability(WaitCapability.class);
  // ... use capabilities only
}

// With options (proxy for HAR, headless, etc.)
BrowserSession session = AutomationSession
  .with(SeleniumConfig.chrome()
    .proxyType(AvailableProxy.STANDALONE)
    .headless(true)
    .build())
  .create();
// ... use session.capability(...); remember to session.close() or use try-with-resources
```

Legacy: `UiAutomationFactory.forEngine("selenium")` and `forDriver("chrome")` still return `DriverSession<?>` (which extends BrowserSession but exposes `instance()`). Prefer `AutomationSession.with(SeleniumConfig...)` for new code.

------------------------------------------------------------------------

## 14. Proxy and HAR Capture

Proxy is not in the core plan but is supported as an engine-level concern:

-   **ProxyFactory&lt;T&gt;** (e.g. SeleniumProxy) prepares the proxy object when building driver options. It is used by engine/browser factories only; tests do not touch it.
-   **AvailableProxy**: STANDALONE (remote proxy URL from config), EMBEDDED (reserved for in-process/HAR).
-   For HAR: configure a standalone proxy URL (e.g. BrowserMob) in `proxy.standaloneUrl`, or extend ProxyFactory for EMBEDDED to start an in-process proxy and return its Proxy config.
