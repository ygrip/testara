package io.github.ygrip.testara.ui.vibium.config;

import java.util.HashMap;
import java.util.Map;

import lombok.Data;

/**
 * Remote-connect configuration for Vibium, distinct from the {@code boolean remote} flag already
 * declared on {@link io.github.ygrip.testara.ui.config.AbstractDriverProperties}. Maps to
 * {@code StartOptions.connectURL(...)}/{@code connectHeaders(...)} — the browser-control
 * connection, never an outbound HTTP/SOCKS proxy.
 */
@Data
public class VibiumRemoteConfig {
  private boolean enabled;
  private String url;
  private Map<String, String> headers = new HashMap<>();
}
