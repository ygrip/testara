package io.github.ygrip.testara.ui.vibium.capability;

import java.util.Set;
import java.util.function.Consumer;

import com.vibium.Request;
import com.vibium.Response;
import com.vibium.Route;

/**
 * Vibium-specific network surface, deliberately split into the three distinct features this
 * module's implementation plan §14 insists must never be conflated:
 * <ul>
 *   <li><b>Remote connect</b> — attaching to an externally-started browser via {@code
 *       StartOptions.connectURL}/{@code connectHeaders}, already applied at session-creation time
 *       (see {@code VibiumEngine#createSession}), not a capability method here. {@link
 *       #networkStates()} reports it as {@link NetworkState#EXTERNAL_PROXY} because Testara neither
 *       created nor can verify any proxy the remote endpoint's own owner may have configured.
 *   <li><b>Request interception</b> — {@code Page#route(String, Consumer)}/{@code
 *       Route#fulfill/doContinue/abort} genuinely exist as real, compilable methods in this pinned
 *       client (confirmed via {@code javap} against {@code vibium-26.5.31.jar}), but direct,
 *       repeatable manual testing against the real jar (three independent variants: registering the
 *       route before vs. after navigation, and using {@code fulfill()} vs. {@code doContinue()})
 *       found that {@code Page#route(...)}'s registered {@code Consumer<Route>} is <b>never actually
 *       invoked</b>: the matching request is genuinely paused at the protocol level (it never
 *       reaches the real server, confirmed by a real HTTP fixture server logging zero hits for the
 *       matched path) but the Java client never delivers the corresponding event back to the
 *       registered handler, so the request — and whatever awaited it (a top-level {@code
 *       Page#go(...)} or an in-page {@code fetch()}) — hangs until a native 60s {@code
 *       VibiumTimeoutException}. This is a real functional gap in the pinned client, not a
 *       plan-§13-style "no API exists" gap, so {@link #route}/{@link #unroute} fail fast with
 *       {@code UnsupportedVibiumCapabilityException} instead of exposing a method that silently
 *       hangs every caller for a minute before failing anyway. Passive observation is a separate,
 *       genuinely-working feature: {@code Page#onRequest}/{@code onResponse} were independently
 *       confirmed (same manual testing) to fire correctly for both navigation and {@code fetch()}
 *       requests, so {@link #onRequest}/{@link #onResponse} are real, supported pass-throughs.
 *   <li><b>Outbound browser proxy</b> — already rejected at session-creation time by {@code
 *       VibiumChromium#proxyOptions()} before any browser is launched; there is no capability
 *       method for it here because there is nothing left to call once a session exists. {@link
 *       #networkStates()} reports {@link NetworkState#LOCAL_PROXY_UNSUPPORTED} for any session that
 *       was not remote-connected.
 * </ul>
 */
public interface VibiumNetworkSupport {

  /** The three distinct network-support states this module's plan §14 requires never be conflated. */
  enum NetworkState {
    INTERCEPTION,
    EXTERNAL_PROXY,
    LOCAL_PROXY_UNSUPPORTED
  }

  /**
   * Always throws {@link io.github.ygrip.testara.ui.vibium.error.UnsupportedVibiumCapabilityException}
   * in this pinned client — see this interface's class javadoc for the confirmed real bug
   * (registered handler never invoked; matching requests hang instead of being fulfilled).
   */
  VibiumNetworkSupport route(String urlPattern, Consumer<Route> handler);

  /** Always throws, for the same reason as {@link #route}: there is no working route to remove. */
  VibiumNetworkSupport unroute(String urlPattern);

  /** Subscribes to real outgoing requests via {@code Page#onRequest(Consumer)} (confirmed working). */
  VibiumNetworkSupport onRequest(Consumer<Request> listener);

  /** Subscribes to real responses via {@code Page#onResponse(Consumer)} (confirmed working). */
  VibiumNetworkSupport onResponse(Consumer<Response> listener);

  /**
   * Reports which of plan §14's three network-support states genuinely apply to this session right
   * now. {@link NetworkState#INTERCEPTION} is never reported by this pinned client (see this
   * interface's class javadoc — {@link #route} is confirmed non-functional here); exactly one of
   * {@link NetworkState#EXTERNAL_PROXY}/{@link NetworkState#LOCAL_PROXY_UNSUPPORTED} is present
   * depending on whether this session was created via remote connect.
   */
  Set<NetworkState> networkStates();
}
