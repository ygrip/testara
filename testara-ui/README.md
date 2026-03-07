# Testara UI

Engine-agnostic UI automation module with a Screenplay-style API supporting Selenium, Playwright, and Appium. Provides a unified abstraction for interactions, observations, page objects, and capabilities across all engines.

## Engine Setup

### Selenium

```xml
<dependency>
  <groupId>io.github.ygrip</groupId>
  <artifactId>testara-ui-selenium</artifactId>
</dependency>
```

Brings in: `testara-ui`, `selenium-java`, `selenium-api`, `webdrivermanager`

### Playwright

```xml
<dependency>
  <groupId>io.github.ygrip</groupId>
  <artifactId>testara-ui-playwright</artifactId>
</dependency>
```

Brings in: `testara-ui`, `playwright`

### Appium

```xml
<dependency>
  <groupId>io.github.ygrip</groupId>
  <artifactId>testara-ui-appium</artifactId>
</dependency>
```

Brings in: `testara-ui-selenium`, `java-client` (Appium)

### Using Multiple Engines

You can include multiple engines simultaneously. Control which are active via:

```properties
automation.engine.default-engine=selenium
automation.engine.active-engines=selenium,playwright
```

## Configuration

All properties go in `configuration.properties` in your test resources.

### Engine Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `automation.engine.default-engine` | String | — | Default engine ID (`selenium`, `playwright`, `appium`) |
| `automation.engine.active-engines` | Set | *(all)* | Active engines; leave empty to enable all discovered engines |

### Selenium Properties (`selenium.driver.*`)

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `owner` | String | — | Owner label for sessions |
| `headless` | boolean | `false` | Run in headless mode |
| `maximize-browser` | boolean | `true` | Maximize browser window |
| `enable-web-socket` | boolean | `true` | Enable WebSocket support |
| `clear-cache` | boolean | `false` | Clear driver cache on start |
| `enable-fallback` | boolean | `true` | Fallback to next driver on failure |
| `scan-locations` | Set | `io.github.ygrip.testara` | Packages to scan for drivers |
| `page-scan-locations` | Set | `io.github.ygrip.testara` | Packages to scan for page objects |
| `action-scan-locations` | Set | `io.github.ygrip.testara` | Packages to scan for user actions |

#### Browser Version

```properties
selenium.driver.version.chrome=
selenium.driver.version.firefox=
selenium.driver.version.edge=
```

#### Browser Arguments

```properties
selenium.driver.args.desktop.chrome.[0]=--remote-allow-origins=*
selenium.driver.args.desktop.chrome.[1]=--disable-gpu
selenium.driver.args.mobile.chrome.[0]=--remote-allow-origins=*
```

#### Custom Capabilities

```properties
selenium.driver.capabilities.desktop.edge.os=Windows
selenium.driver.capabilities.desktop.edge.os_version=10
selenium.driver.capabilities.android.android.deviceName=Android Emulator
selenium.driver.capabilities.android.android.platformName=Android
selenium.driver.capabilities.android.android.automationName=UiAutomator2
```

#### Remote Driver (Selenium Grid / Selenoid)

```properties
selenium.driver.remote-driver.default.enabled=false
selenium.driver.remote-driver.default.uri=http://localhost:4444/
selenium.driver.remote-driver.default.enable-vnc=true
selenium.driver.remote-driver.default.enable-video=false
selenium.driver.remote-driver.default.connection-timeout-seconds=30
selenium.driver.remote-driver.default.read-timeout-seconds=60
selenium.driver.remote-driver.default.session-creation-timeout-seconds=120
```

#### Mobile Emulation

```properties
selenium.driver.emulation.mobile.chrome.dimension.width=360
selenium.driver.emulation.mobile.chrome.dimension.height=720
selenium.driver.emulation.mobile.chrome.dimension.pixel-ratio=3.0
selenium.driver.emulation.mobile.chrome.adjust-dimension=false
selenium.driver.emulation.mobile.chrome.device-name=Nexus 5
```

#### User Agent Override

```properties
selenium.driver.user-agent.desktop.chrome=Mozilla/5.0 (Windows NT 10.0; Win64; x64) ...
selenium.driver.user-agent.mobile.chrome=Mozilla/5.0 (Linux; Android 8.0; Pixel 2) ...
```

### Playwright Properties (`playwright.browser.*`)

Same structure as Selenium, with prefix `playwright.browser` instead of `selenium.driver`.

### Appium Properties (`appium.driver.*`)

Same as Selenium, plus:

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `timeout` | Duration | `60s` | General timeout |
| `install-timeout` | Duration | `120s` | App install timeout |

#### App Configuration

```properties
appium.driver.apps.android.my-app.app-name=My Application
appium.driver.apps.android.my-app.file-name=app-debug.apk
appium.driver.apps.android.my-app.file-location=/path/to/apks/
appium.driver.apps.android.my-app.app-package=com.myapp.android
appium.driver.apps.android.my-app.reset-install=false
```

### Proxy Properties

```properties
proxy.standalone-url=http://localhost:9091/
proxy.mitmproxy-api-url=http://localhost:8080/
```

### Web Page URL Overrides

```properties
web.page.desktop.login.url=https://example.com/login
web.page.mobile.login.url=https://m.example.com/login
```

## Screenplay API

### Actor

The `Actor` is the entry point for performing interactions and observations:

```java
// Use the current session
Actor.withCurrentSession()
    .attemptsTo(
        Navigate.to("https://example.com"),
        Enter.text("admin").into("#username"),
        Enter.text("password").into("#password"),
        Click.on("#login-btn")
    );

// Observe a value
String title = Actor.withCurrentSession().observe(TheText.of("h1"));

// With an explicit session
Actor.with(session).attemptsTo(Click.on("#logout"));
```

## Built-in Interactions

| Interaction | Usage |
|-------------|-------|
| `Click` | `Click.on("#btn")`, `Click.on(Locator.css(".submit"))` |
| `DoubleClick` | `DoubleClick.on("#item")` |
| `ForceClick` | `ForceClick.on("#hidden-btn")` |
| `Enter` | `Enter.text("value").into("#input")`, `.thenHit(Keys.ENTER)` |
| `Clear` | `Clear.field("#input")` |
| `Navigate` | `Navigate.to("https://...")`, `Navigate.refresh()`, `Navigate.reload()` |
| `Submit` | `Submit.form("#form")` |
| `SelectOption` | `SelectOption.from("#select").byValue("x")`, `.byIndex(0)`, `.byVisibleText("...")` |
| `Hover` | `Hover.over("#element")` |
| `Focus` | `Focus.on("#element")` |
| `Blur` | `Blur.from("#element")` |
| `Scroll` | `Scroll.to("#element")` |
| `Tab` | `Tab.press()` |
| `Hold` | `Hold.on("#element", Duration.ofSeconds(2))` |
| `Drag` | `Drag.from(source).to(target)`, `Drag.from(source).by(10, 20)` |
| `WaitUntil` | `WaitUntil.element("#id").isVisible()` |
| `SeeThat` | `SeeThat.element("#id").hasText("expected")` |

## Built-in Observations

| Observation | Returns | Usage |
|-------------|---------|-------|
| `TheText` | String | `TheText.of("#title")` |
| `TheValue` | String | `TheValue.of("#input")` |
| `TheAttribute` | String | `TheAttribute.of("#link", "href")` |
| `TheCss` | String | `TheCss.of("#el", "color")` |
| `AllText` | List\<String\> | `AllText.of(".items li")` |
| `AllValue` | List\<String\> | `AllValue.of("input.field")` |
| `TheElement` | Object | `TheElement.of("#el")` |
| `TheElements` | List | `TheElements.of(".items")` |
| `ChildElement` | Object | `ChildElement.of(parent, "#child")` |
| `ChildElements` | List | `ChildElements.of(parent, ".children")` |
| `CountElements` | Integer | `CountElements.of(".item")` |
| `StateElement` | Boolean | `StateElement.visible("#el")` |
| `HasAttribute` | Boolean | `HasAttribute.on("#el", "disabled")` |
| `ThisPage` | String | `ThisPage.title()`, `ThisPage.url()` |
| `ExecuteScript` | Object | `ExecuteScript.script("return document.title")` |
| `Capture` | byte[] | `Capture.page().fullPage()`, `Capture.element("#el")` |

## Page Object Model

### Defining a Page

```java
@Page(name = "login", url = "/login", platforms = {DeviceType.DEFAULT, DeviceType.DESKTOP})
public class LoginPage extends PageContext<WebDriver> {
    // Element fields discovered by PageFinder
}
```

### Page Annotations

| Field | Description |
|-------|-------------|
| `name` | Page identifier |
| `url` | Page URL path |
| `platforms` | `DEFAULT`, `DESKTOP`, `MOBILE`, `ANDROID`, `IOS` |

### Navigating to a Page

```java
Actor.withCurrentSession().attemptsTo(Navigate.to(loginPage));
```

Pages can also define URL overrides per device type in properties:

```properties
web.page.desktop.login.url=https://example.com/login
web.page.mobile.login.url=https://m.example.com/login
```

## Element Locators

### Selector Syntax

Elements can be located using a `selector:value` format:

| Prefix | Description | Example |
|--------|-------------|---------|
| `css:` | CSS selector | `css:#submit-btn` |
| `xpath:` | XPath | `xpath://button[@id='submit']` |
| `id:` | Element ID | `id:username` |
| `class:` | CSS class name | `class:btn-primary` |
| `tag:` | Tag name | `tag:input` |
| `name:` | Name attribute | `name:email` |
| `link:` | Link text | `link:Click Here` |
| `partial-link:` | Partial link text | `partial-link:Click` |
| `accessibility:` | Accessibility ID (mobile) | `accessibility:login-btn` |
| `android-ui-automator:` | Android UI Automator | `android-ui-automator:text("Login")` |
| `ios-class-chain:` | iOS class chain | `ios-class-chain:**/XCUIElementTypeButton` |

### Element Builder

```java
// Simple
Element.of("css:#submit").build()

// With locator
Element.of(Locator.xpath("//button")).build()

// Chained (parent → child)
Element.of("#parent").child().followingSibling().build()

// On a page context
Element.of("id:foo").on(pageContext).by(finder).build()

// Single element
element.one()

// All matching elements
element.all()
```

## User Actions

User actions encapsulate reusable page-specific tasks:

```java
@OnPage(LoginPage.class)
public class LoginActions extends UserAction {

    @Action("login with credentials")
    public void login(String username, String password) {
        attemptsTo(
            Enter.text(username).into("#username"),
            Enter.text(password).into("#password"),
            Click.on("#submit")
        );
    }

    @Action(value = "open login page", allowAnonymousCall = true)
    public void openLogin() {
        capability(NavigationCapability.class).to("/login");
    }
}
```

Execute actions:

```java
Actor.withCurrentSession().executeTask("login with credentials", "login", Map.of());
```

## Capabilities

Each engine provides implementations of these capability interfaces:

| Capability | Purpose |
|------------|---------|
| `InteractionCapability` | click, enter, clear, submit, select, drag, scroll, executeScript |
| `ObservationCapability` | getText, getValue, getAttribute, getCssValue, capture, findOne/findAll |
| `NavigationCapability` | navigate to, back, forward, refresh, reload, manage tabs |
| `AssertionCapability` | seeThatVisible, seeThatText, seeThatValue, isVisible, isPresent |
| `WaitCapability` | untilVisible, untilClickable, untilPresent, withTimeout |

Access capabilities directly:

```java
DriverSession<?> session = DriverSessionManager.inThisTestThread().getCurrentDriver();
InteractionCapability interaction = session.capability(InteractionCapability.class);
interaction.click(Element.of("#btn").build());

ObservationCapability observation = session.capability(ObservationCapability.class);
String text = observation.getText(Element.of("#title").build());
```

## Creating Custom Components

### Custom Engine

1. Implement `EngineFactory<T extends AbstractDriverProperties>`:

```java
public class MyEngine implements EngineFactory<MyDriverProperties> {
    @Override
    public String id() { return "myengine"; }

    @Override
    public MyDriverProperties config() {
        return TestFramework.configuration().get(MyDriverProperties.class);
    }

    @Override
    public <D extends DriverSession<?>> D forDriver(String name) { /* ... */ }

    @Override
    public <D extends DriverSession<?>> D forDriver(String name, String deviceType) { /* ... */ }

    @Override
    public <D extends DriverSession<?>> D forDriver(String name, String deviceType, String proxyType) { /* ... */ }
}
```

2. Register via SPI in `META-INF/services/io.github.ygrip.testara.ui.factory.EngineFactory`:

```
com.myproject.engine.MyEngine
```

### Custom Driver

1. Extend `AbstractDriver<D, O>` and annotate with `@DriverMetadata`:

```java
@DriverMetadata(
    name = "my-chrome",
    browserName = "chrome",
    engine = SeleniumEngine.class,
    platforms = {DeviceType.DEFAULT, DeviceType.DESKTOP}
)
public class MyCustomChromeDriver extends AbstractDriver<WebDriver, ChromeOptions> {

    @Override
    public WebDriver create(ChromeOptions options) {
        // Custom driver creation logic
        return new ChromeDriver(options);
    }

    @Override
    public ChromeOptions defaultOptions() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--custom-flag");
        return options;
    }

    @Override
    public ChromeOptions mobileOptions() { return defaultOptions(); }

    @Override
    public ChromeOptions proxyOptions() { return defaultOptions(); }

    @Override
    protected boolean isJavaScriptEnabled() { return true; }
}
```

2. Place in a package covered by `scan-locations`.

### Custom Interaction

```java
public final class RightClick implements Interaction {
    private final Element element;

    private RightClick(Element element) {
        this.element = element;
    }

    public static RightClick on(String locator) {
        return new RightClick(Element.of(locator).build());
    }

    @Override
    public void perform(InteractionContext context) {
        // Use capabilities from the context
        context.interaction().executeScript(
            "arguments[0].dispatchEvent(new MouseEvent('contextmenu', {bubbles: true}));",
            element
        );
    }

    @Override
    public Interaction root(Element root) {
        return new RightClick(root.withChild(element).child());
    }
}
```

Usage:

```java
Actor.withCurrentSession().attemptsTo(RightClick.on("#context-menu-target"));
```

### Custom Observation

```java
public final class PlaceholderText implements Observation<String> {
    private final Element element;

    public static PlaceholderText of(String locator) {
        return new PlaceholderText(Element.of(locator).build());
    }

    @Override
    public Observation<String> root(Element root) {
        return new PlaceholderText(root.withChild(element).child());
    }

    @Override
    public String perform(InteractionContext context) {
        return context.observation().getAttribute(element, "placeholder");
    }
}
```

Usage:

```java
String placeholder = Actor.withCurrentSession().observe(PlaceholderText.of("#email-input"));
```

### Custom UserAction

```java
@OnPage(CheckoutPage.class)
public class CheckoutActions extends UserAction {

    @Action("complete checkout")
    public void completeCheckout(String cardNumber, String cvv) {
        attemptsTo(
            Enter.text(cardNumber).into("#card-number"),
            Enter.text(cvv).into("#cvv"),
            Click.on("#pay-now")
        );
    }
}
```

## Session Management

```java
// Get an engine
EngineFactory<?> engine = UiAutomationFactory.forEngine("selenium");

// Create a session
DriverSession<?> session = engine.forDriver("chrome");
DriverSession<?> session = engine.forDriver("chrome", "desktop");
DriverSession<?> session = engine.forDriver("chrome", "desktop", "mitm");

// Register and activate
DriverSessionManager.inThisTestThread().registerDriver("chrome-desktop").forDriver(session);
DriverSessionManager.inThisTestThread().setCurrentActiveDriver(session);

// Get current driver
DriverSession<?> current = DriverSessionManager.inThisTestThread().getCurrentDriver();
```

## Device Types

`DEFAULT`, `DESKTOP`, `MOBILE`, `ANDROID`, `IOS`

These are used for capability mapping, page resolution, and emulation configuration.
