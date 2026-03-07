package io.github.ygrip.testara.ui.appium.remote;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import lombok.extern.log4j.Log4j2;

/**
 * @author yunaz.ramadhan on 12/10/2021
 */
@Log4j2
public final class AppiumServerManager {
  private static final ThreadLocal<Map<String, AppiumServer>> SERVERS = new InheritableThreadLocal<>();

  private AppiumServerManager() {

  }

  public static AppiumServer getServer(String name) throws Exception {
    Map<String, AppiumServer> currentServers = Optional.ofNullable(SERVERS.get())
      .orElse(new HashMap<>());

    SERVERS.set(currentServers);

    return currentServers.get(name);
  }

  public static AppiumServer buildServer(String name, String url) throws Exception {
    if (StringUtils.isNotBlank(url)) {
      AppiumServer server = new AppiumServer(url);

      return registerServer(name, server);
    }

    return null;
  }

  private static AppiumServer registerServer(String name, AppiumServer server) {
    Map<String, AppiumServer> currentServers = Optional.ofNullable(SERVERS.get())
      .orElse(new HashMap<>());

    currentServers.put(name, server);

    SERVERS.set(currentServers);

    return currentServers.get(name);
  }

  public static void closeServer(String name) {
    try {
      AppiumServer server = getServer(name);

      if (ObjectUtils.isNotEmpty(server)) {
        server.stop();
      }
    } catch (Exception ignored) {

    }
  }

  public static void closeAllServers() {
    Map<String, AppiumServer> currentServers = Optional.ofNullable(SERVERS.get())
      .orElse(new HashMap<>());

    currentServers.forEach((key, value) -> {
      log.info("Closing appium server connection for {}", key);
      value.stop();
    });
  }

}
