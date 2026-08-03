package io.github.ygrip.testara.ui.vibium.capability;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.Consumer;

import com.vibium.Request;
import com.vibium.Response;
import com.vibium.Route;
import com.vibium.errors.VibiumException;

import io.github.ygrip.testara.ui.vibium.driver.VibiumSession;
import io.github.ygrip.testara.ui.vibium.error.UnsupportedVibiumCapabilityException;
import io.github.ygrip.testara.ui.vibium.error.VibiumOperationException;

/**
 * Vibium's {@link VibiumNetworkSupport}. Not a {@link VibiumElementResolver} subclass: like {@code
 * VibiumNavigationCapability}, none of these operations resolve an element locator, so the small
 * {@code safePageUrl()} helper is duplicated locally rather than inherited (same convention already
 * used by that class).
 */
public final class VibiumNetworkSupportImpl implements VibiumNetworkSupport {
  private final VibiumSession session;

  public VibiumNetworkSupportImpl(VibiumSession session) {
    this.session = session;
  }

  @Override
  public VibiumNetworkSupport route(String urlPattern, Consumer<Route> handler) {
    // See VibiumNetworkSupport's class javadoc: confirmed via direct manual testing against
    // vibium-26.5.31.jar that Page#route(...)'s registered handler is never actually invoked — the
    // matching request is genuinely paused (never reaches the real network) but the Java client
    // never delivers the corresponding event, so the request (and whatever awaited it) hangs until
    // a native 60s VibiumTimeoutException. Failing fast here is strictly better than silently
    // handing back a method that hangs every caller for a minute before failing anyway.
    throw unsupportedRouting("route");
  }

  @Override
  public VibiumNetworkSupport unroute(String urlPattern) {
    throw unsupportedRouting("unroute");
  }

  private UnsupportedVibiumCapabilityException unsupportedRouting(String operation) {
    return new UnsupportedVibiumCapabilityException(
      operation,
      "com.vibium.Page#route(String, Consumer) exists and compiles (confirmed via javap against "
        + "vibium-26.5.31.jar), but direct manual testing found its registered Consumer<Route> "
        + "handler is never actually invoked in this pinned client: the matching request is "
        + "genuinely paused at the protocol level (confirmed with a real HTTP fixture server "
        + "logging zero hits for the matched path) yet the Java client never delivers the "
        + "corresponding event back to Java, so the request hangs until a native "
        + "VibiumTimeoutException after 60s — reproduced with both fulfill() and doContinue(), and "
        + "both before and after navigation. Use onRequest/onResponse for passive observation "
        + "instead (confirmed working); there is no supported active interception in this client."
    );
  }

  @Override
  public VibiumNetworkSupport onRequest(Consumer<Request> listener) {
    try {
      session.pageForApi()
        .onRequest(listener);
    } catch (VibiumException e) {
      throw wrap("onRequest", e);
    }
    return this;
  }

  @Override
  public VibiumNetworkSupport onResponse(Consumer<Response> listener) {
    try {
      session.pageForApi()
        .onResponse(listener);
    } catch (VibiumException e) {
      throw wrap("onResponse", e);
    }
    return this;
  }

  @Override
  public Set<NetworkState> networkStates() {
    // NetworkState.INTERCEPTION is deliberately never included: route()/unroute() are confirmed
    // non-functional in this pinned client (see class javadoc / unsupportedRouting()).
    Set<NetworkState> states = EnumSet.noneOf(NetworkState.class);
    if (session.isRemoteConnected()) {
      states.add(NetworkState.EXTERNAL_PROXY);
    } else {
      states.add(NetworkState.LOCAL_PROXY_UNSUPPORTED);
    }
    return states;
  }

  private VibiumOperationException wrap(String operation, VibiumException cause) {
    return VibiumOperationException.of(operation, "n/a", safePageUrl(), 0L, cause);
  }

  private String safePageUrl() {
    try {
      return session.pageForApi()
        .url();
    } catch (Exception e) {
      return "<unavailable>";
    }
  }
}
