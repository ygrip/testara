package io.github.ygrip.testara.ui.appium.interaction;

import java.util.Optional;

import org.apache.commons.lang3.ObjectUtils;

import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.ui.appium.config.AppiumDriverProperties;
import io.github.ygrip.testara.ui.appium.driver.AppiumSession;
import io.github.ygrip.testara.ui.driver.DriverSession;
import io.github.ygrip.testara.ui.error.SessionMismatchException;
import io.github.ygrip.testara.ui.error.UnrecognizedApplicationException;
import io.github.ygrip.testara.ui.executor.Actor;
import io.github.ygrip.testara.ui.interaction.Interaction;
import io.github.ygrip.testara.ui.interaction.InteractionContext;
import io.github.ygrip.testara.ui.page.Element;
import io.github.ygrip.testara.ui.page.NamedPage;
import io.github.ygrip.testara.ui.page.PageContext;

import io.appium.java_client.AppiumDriver;
import io.appium.java_client.InteractsWithApps;
import lombok.extern.log4j.Log4j2;

/**
 * Screenplay-style interaction: open an application.
 *
 * @see Actor#attemptsTo(Interaction...)
 */
@Log4j2
public final class OpenApp implements Interaction {
  private final String appName;

  private OpenApp(String appName) {
    this.appName = appName;
  }

  public static OpenApp named(String appName) {
    return new OpenApp(appName);
  }

  public static OpenApp named(NamedPage.NamedPageContext page) {
    return new OpenApp(Optional.ofNullable(page)
      .map(NamedPage.NamedPageContext::build)
      .map(NamedPage::getPage)
      .map(PageContext::name)
      .orElse(null));
  }

  @Override
  public void perform(InteractionContext context) {
    DriverSession<?> session = context.session();
    if (session instanceof AppiumSession appiumSession) {
      AppiumDriver driver = appiumSession.instance();
      if (driver instanceof InteractsWithApps interactsWithApps) {
        final var appsData = TestFramework.configuration()
          .get(AppiumDriverProperties.class)
          .getAppsData(session, this.appName);
        if (ObjectUtils.isNotEmpty(appsData)) {
          interactsWithApps.activateApp(appsData.getAppPackage());
        } else {
          throw new UnrecognizedApplicationException("Unable to find apps with name %s".formatted(this.appName));
        }
      } else {
        log.warn("#Current active driver is unable to interact with apps");
      }
    }
  }

  @Override
  public void support(DriverSession<?> session) throws SessionMismatchException {
    if (!(session instanceof AppiumSession)) {
      throw new SessionMismatchException(String.format(
        "#OpenApp expect current session to be %s, but got %s",
        AppiumSession.class,
        session.getClass()
      ));
    }
  }

  @Override
  public Interaction root(Element element) {
    return this;
  }
}
