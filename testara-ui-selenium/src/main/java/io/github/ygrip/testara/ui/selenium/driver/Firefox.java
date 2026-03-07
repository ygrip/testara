package io.github.ygrip.testara.ui.selenium.driver;

import java.util.List;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.firefox.FirefoxProfile;

import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.ui.driver.AbstractDriver;
import io.github.ygrip.testara.ui.selenium.engine.SeleniumEngine;
import io.github.ygrip.testara.ui.model.TestaraProxyModel;
import io.github.ygrip.testara.ui.model.DeviceType;
import io.github.ygrip.testara.ui.model.DriverMetadata;
import io.github.ygrip.testara.ui.selenium.proxy.SeleniumProxy;

@DriverMetadata(name = "firefox",
  engine = SeleniumEngine.class,
  platforms = {DeviceType.DEFAULT, DeviceType.DESKTOP, DeviceType.MOBILE}
)
public class Firefox extends AbstractDriver<FirefoxDriver, FirefoxOptions> {
  private final static String[] MIME_TYPES =
    {"text/plain", "audio/aac", "application/x-abiword", "video/x-msvideo", "application/octet-stream", "image/bmp",
      "application/x-bzip", "application/x-bzip2", "text/css", "text/csv", "application/msword",
      "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/gzip", "image/gif",
      "text/html", "image/vnd.microsoft.icon", "application/java-archive", "image/jpeg", "text/javascript",
      "application/json", "application/ld+json", "audio/midi;audio/x-midi", "audio/mpeg", "video/mpeg",
      "application/vnd.apple.installer+xml", "image/png", "application/pdf", "application/vnd.ms-powerpoint",
      "application/vnd.rar", "image/svg+xml", "image/tiff", "font/ttf", "application/vnd.ms-excel",
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/xml", "text/xml",
      "application/zip", "application/x-7z-compressed",
      "application/vnd.openxmlformats-officedocument.presentationml.presentation"};

  @Override
  public FirefoxDriver create(FirefoxOptions options) {
    return new FirefoxDriver(options);
  }

  @Override
  public FirefoxOptions proxyOptions() {
    FirefoxOptions options = new FirefoxOptions();
    Proxy proxy = TestFramework.context()
      .get(SeleniumProxy.class)
      .create(getProxyType());
    if (ObjectUtils.isNotEmpty(proxy)) {
      TestaraProxyModel proxyModel = new TestaraProxyModel(proxy.getHttpProxy());
      FirefoxProfile profile = options.getProfile();
      profile.setPreference("network.proxy.type", 1);
      profile.setPreference("network.proxy.http", proxyModel.getProxyHost());
      profile.setPreference("network.proxy.http_port", proxyModel.getProxyPort());
      profile.setPreference("network.proxy.ssl", proxyModel.getProxyHost());
      profile.setPreference("network.proxy.ssl_port", proxyModel.getProxyPort());
      profile.setPreference("network.proxy.socks", proxyModel.getProxyHost());
      profile.setPreference("network.proxy.socks_port", proxyModel.getProxyPort());
      options.setProfile(profile);
    }
    return options;
  }

  @Override
  public FirefoxOptions mobileOptions() {
    FirefoxOptions options = defaultOptions();
    FirefoxProfile profile = options.getProfile();
    profile.setPreference("general.useragent.override", getUserAgent());
    options.addArguments("user-agent=" + getUserAgent());
    options.setProfile(profile);
    return options;
  }

  @Override
  public FirefoxOptions defaultOptions() {
    FirefoxOptions options = new FirefoxOptions();
    if (isJavaScriptEnabled()) {
      options.addArguments("--enable-javascript");
    }
    options.addArguments("--enable-cdp");
    if (isHeadless()) {
      options.addArguments("-headless");
    }
    options.addArguments("--no-sandbox");

    List<String> additionalArgs = getArguments();
    if (ObjectUtils.isNotEmpty(additionalArgs)) {
      options.addArguments(additionalArgs);
    }
    final var binaryPath = getBinaryPath();
    if (StringUtils.isNotBlank(binaryPath)) {
      options.setBinary(binaryPath);
    }
    options.setCapability("moz:debuggerAddress", true);
    options.setProfile(getFirefoxProfile());
    return options;
  }

  private FirefoxProfile getFirefoxProfile() {
    FirefoxProfile profile = new FirefoxProfile();
    final String SUPPORTED_MIME_TYPE = String.join(";", MIME_TYPES);
    profile.setPreference("browser.download.dir", getDownloadLocation());
    profile.setPreference("browser.download.folderList", 2);
    profile.setPreference("remote.active-protocols", 3);
    profile.setPreference("browser.helperApps.alwaysAsk.force", false);
    profile.setPreference("browser.download.manager.showWhenStarting", false);
    profile.setPreference("browser.download.panel.shown", false);
    profile.setPreference("browser.download.manager.useWindow", false);
    profile.setPreference("browser.download.manager.focusWhenStarting", false);
    profile.setPreference("browser.download.manager.alertOnEXEOpen", false);
    profile.setPreference("browser.download.manager.showAlertOnComplete", false);
    profile.setPreference("browser.helperApps.neverAsk.openFile", SUPPORTED_MIME_TYPE);
    profile.setPreference("browser.helperApps.neverAsk.saveToDisk", SUPPORTED_MIME_TYPE);
    profile.setPreference("dom.webnotifications.enabled", false);
    profile.setPreference("dom.w3c_pointer_events.enabled", true);
    profile.setPreference("dom.disable_beforeunload", true);
    profile.setAssumeUntrustedCertificateIssuer(false);
    profile.setAcceptUntrustedCertificates(true);
    profile.setPreference("general.startup.browser", false);
    profile.setPreference("plugin.default_plugin_disabled", false);
    profile.setPreference("extensions.update.enabled", false);
    profile.setPreference("extensions.update.autoUpdateEnabled", false);
    profile.setPreference("extensions.checkUpdateSecurity", false);
    profile.setPreference("extensions.checkCompatibility", false);
    profile.setPreference("browser.urlbar.showSearch", false);
    profile.setPreference("browser.urlbar.showPopup", false);
    profile.setPreference("browser.urlbar.autocomplete.enabled", false);
    profile.setPreference("startup.homepage_welcome_url", "about:blank");
    profile.setPreference("browser.startup.homepage", "about:blank");
    profile.setPreference("browser.urlbar.autoFill", false);
    profile.setPreference("browser.startup.page", 0);
    profile.setPreference("browser.shell.checkDefaultBrowser", false);
    profile.setPreference("browser.formfill.enable", false);
    profile.setPreference("loop.enabled", false);
    profile.setPreference("reader.parse-on-load.enabled", false);
    return profile;
  }


  @Override
  protected boolean isJavaScriptEnabled() {
    return true;
  }
}
