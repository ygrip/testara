package io.github.ygrip.testara.ui.appium.remote;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.URI;

import org.apache.commons.lang3.StringUtils;

import io.appium.java_client.service.local.AppiumDriverLocalService;
import io.appium.java_client.service.local.AppiumServiceBuilder;
import io.appium.java_client.service.local.flags.GeneralServerFlag;
import lombok.extern.log4j.Log4j2;

/**
 * @author yunaz.ramadhan on 12/10/2021
 */
@Log4j2
public final class AppiumServer {
  private final AppiumDriverLocalService server;
  private String host;
  private int port;

  AppiumServer(String url) throws Exception {
    url = url.trim();
    if (StringUtils.isBlank(url)) {
      throw new Exception("Appium server url cannot be blank");
    }
    final String NODE_PATH = System.getenv("NODE_PATH");
    final String APPIUM_PATH = System.getenv("APPIUM_PATH");
    AppiumServiceBuilder builder = new AppiumServiceBuilder();
    parseUrl(url);
    builder.withIPAddress(this.host)
      .usingPort(this.port);
    if (StringUtils.isNotBlank(NODE_PATH)) {
      builder.usingDriverExecutable(new File(NODE_PATH));
    }
    if (StringUtils.isNotBlank(APPIUM_PATH)) {
      builder.withAppiumJS(new File(APPIUM_PATH));
    }
    builder.withArgument(GeneralServerFlag.SESSION_OVERRIDE);
    builder.withArgument(GeneralServerFlag.LOG_LEVEL, "error");
    this.server = AppiumDriverLocalService.buildService(builder);
  }

  private void parseUrl(String url) throws MalformedURLException {
    URI parsed = URI.create(url);
    this.host = parsed.getHost();
    if (parsed.getPort() == 0) {
      this.port = getAvailablePort();
    } else {
      this.port = parsed.getPort();
    }
  }

  private int getAvailablePort() {
    int port = 4723;

    try {
      ServerSocket serverSocket = new ServerSocket(0);
      port = serverSocket.getLocalPort();
      serverSocket.close();
    } catch (IOException ignored) {
    }
    return port;
  }

  public void start() {
    if (!isRunning()) {
      try {
        log.info("Start appium server connection at {}:{}", this.host, this.port);
        this.server.start();
      } catch (Exception ignored) {

      }
    }
  }

  public void stop() {
    if (isRunning()) {
      try {
        log.info("Stop appium server connection at {}:{}", this.host, this.port);
        this.server.clearOutPutStreams();
        this.server.stop();
      } catch (Exception err) {
        log.warn("Fail to stop appium server, error : {}", err.getMessage(), err);
      }
    }
  }

  /**
   * <p>isRunning.</p>
   *
   * @return a boolean.
   */
  public boolean isRunning() {
    return this.server.isRunning();
  }
}
