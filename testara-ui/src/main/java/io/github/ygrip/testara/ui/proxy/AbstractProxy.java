package io.github.ygrip.testara.ui.proxy;

import java.util.Collections;
import java.util.List;

import io.github.ygrip.testara.core.data.DataHolder;
import io.github.ygrip.testara.ui.model.AvailableProxy;
import io.github.ygrip.testara.ui.model.MitmProxyCreateInstanceResponse;
import io.github.ygrip.testara.ui.model.MitmProxyHealthResponse;
import io.github.ygrip.testara.ui.model.MitmProxyInstanceDetail;
import io.github.ygrip.testara.ui.model.MitmProxyMessageResponse;
import io.github.ygrip.testara.ui.model.MitmProxyRenewResponse;
import io.github.ygrip.testara.ui.model.MitmProxyRule;
import io.github.ygrip.testara.ui.model.MitmProxyRuleResponse;
import io.github.ygrip.testara.ui.model.ProxyRuleCreation;

import lombok.extern.log4j.Log4j2;

/**
 * Agnostic proxy abstraction supporting BrowserUp and MitmProxy Grid backends.
 * <p>
 * Subclasses implement the lifecycle ({@link #start()}, {@link #stop()}) and data retrieval
 * ({@link #getRequestData()}, {@link #getResponseData()}).
 * <p>
 * MitmProxy Grid features (rules, instances, TTL, certs) have default implementations
 * so that existing BrowserUp implementations remain source-compatible.
 *
 * @param <P> the proxy representation returned to the driver layer
 *            (e.g. {@code org.openqa.selenium.Proxy} for Selenium,
 *            {@code String} for a plain address)
 */
@Log4j2
public abstract class AbstractProxy<P> {
  private final DataHolder dataHolder;
  private P proxy;
  private String proxyAddress;
  private Integer port;
  private AvailableProxy proxyType;

  public AbstractProxy(DataHolder dataHolder) {
    this.dataHolder = dataHolder;
  }

  // ── Lifecycle ──────────────────────────────────────────────────────

  public abstract void start();

  public abstract void stop();

  public abstract boolean isStarted();

  /**
   * Called after each scenario to reset per-test state while keeping the proxy alive
   * for reuse by the next test on the same thread.
   * <p>
   * The default implementation calls {@link #stop()}, which is correct for proxies
   * that are cheap to recreate (e.g. BrowserUp).  MitmProxy implementations override
   * this to only clear rules and renew the TTL, leaving the remote instance running.
   */
  public void afterScenario() {
    stop();
  }

  // ── Core accessors ────────────────────────────────────────────────

  DataHolder getDataHolder() {
    return this.dataHolder;
  }

  public Integer getPort() {
    return this.port;
  }

  protected void setPort(Integer port) {
    this.port = port;
  }

  public P getProxy() {
    return this.proxy;
  }

  protected void setProxy(P proxy) {
    this.proxy = proxy;
  }

  public void setRemoteProxyAddress(String proxyAddress) {
    this.proxyAddress = proxyAddress;
  }

  protected String getProxyAddress() {
    return this.proxyAddress;
  }

  protected AvailableProxy getProxyType() {
    return this.proxyType;
  }

  public void setProxyType(AvailableProxy proxyType) {
    this.proxyType = proxyType;
  }

  // ── Network data ──────────────────────────────────────────────────

  public abstract List<?> getRequestData();

  public abstract List<?> getResponseData();

  // ── Rule management (MitmProxy Grid) ──────────────────────────────

  /**
   * Create an interception rule on the current instance.
   *
   * @return status message from the API, or {@code null}
   */
  public MitmProxyMessageResponse createRule(MitmProxyRule rule) {
    throw new UnsupportedOperationException(
        "Rule management is not supported by " + getClass().getSimpleName());
  }

  /**
   * Create an interception rule from a {@link ProxyRuleCreation} specification.
   * File-based body references are resolved relative to {@code baseFolder}.
   *
   * @param creation   the rule creation specification (typically deserialized from JSON)
   * @param baseFolder absolute path prefix for file resolution
   * @return status message from the API, or {@code null}
   */
  public MitmProxyMessageResponse createRule(ProxyRuleCreation creation, String baseFolder) {
    return createRule(creation.toMitmProxyRule(baseFolder));
  }

  /**
   * List all rules on the current instance.
   */
  public List<MitmProxyRuleResponse> listRules() {
    return Collections.emptyList();
  }

  /**
   * Delete a rule by its positional index.
   */
  public MitmProxyMessageResponse deleteRule(int ruleIndex) {
    throw new UnsupportedOperationException(
        "Rule management is not supported by " + getClass().getSimpleName());
  }

  /**
   * Toggle a rule between enabled and disabled.
   */
  public MitmProxyMessageResponse toggleRule(int ruleIndex) {
    throw new UnsupportedOperationException(
        "Rule management is not supported by " + getClass().getSimpleName());
  }

  /**
   * Remove all rules from the current instance.
   */
  public void clearRules() {
    log.debug("clearRules() is a no-op for {}", getClass().getSimpleName());
  }

  /**
   * Add a high-priority rule that strips browser cache-negotiation headers from
   * requests and prevents caching on responses.  Call this <b>before</b> navigating
   * so that subsequent interception rules always see full {@code 200 OK} responses.
   *
   * @param urlContains substring the URL must contain ({@code ""} for all traffic)
   * @return API response, or {@code null} if the proxy does not support rules
   */
  public MitmProxyMessageResponse disableCaching(String urlContains) {
    return createRule(MitmProxyRule.disableCaching(urlContains));
  }

  /**
   * Disable caching for all traffic.
   *
   * @see #disableCaching(String)
   */
  public MitmProxyMessageResponse disableCaching() {
    return disableCaching("");
  }

  // ── Instance lifecycle (MitmProxy Grid) ───────────────────────────

  /**
   * Create a new proxy instance with optional TTL.
   *
   * @param ttlSeconds lifespan in seconds, or {@code null} for server default
   */
  public MitmProxyCreateInstanceResponse createInstance(Integer ttlSeconds) {
    throw new UnsupportedOperationException(
        "Instance management is not supported by " + getClass().getSimpleName());
  }

  /**
   * Create a new proxy instance with server-default TTL.
   */
  public MitmProxyCreateInstanceResponse createInstance() {
    return createInstance(null);
  }

  /**
   * Get detailed info about the current instance (rules, uptime, remaining TTL).
   */
  public MitmProxyInstanceDetail getInstanceDetail() {
    throw new UnsupportedOperationException(
        "Instance management is not supported by " + getClass().getSimpleName());
  }

  /**
   * Renew (extend) the current instance's lifespan.
   *
   * @param ttlSeconds new TTL in seconds, or {@code null} to reuse current
   */
  public MitmProxyRenewResponse renewInstance(Integer ttlSeconds) {
    throw new UnsupportedOperationException(
        "Instance management is not supported by " + getClass().getSimpleName());
  }

  /**
   * Renew the current instance with its existing TTL.
   */
  public MitmProxyRenewResponse renewInstance() {
    return renewInstance(null);
  }

  /**
   * Destroy a proxy instance.
   *
   * @param instanceId instance to destroy
   * @param cleanup    also remove rule files and CA directory on the server
   */
  public void destroyInstance(String instanceId, boolean cleanup) {
    throw new UnsupportedOperationException(
        "Instance management is not supported by " + getClass().getSimpleName());
  }

  public void destroyInstance(String instanceId) {
    destroyInstance(instanceId, false);
  }

  // ── Health ────────────────────────────────────────────────────────

  /**
   * Grid-level health and capacity info.
   */
  public MitmProxyHealthResponse healthCheck() {
    return MitmProxyHealthResponse.builder()
        .status(isStarted() ? "ok" : "unavailable")
        .build();
  }

  /**
   * Block until the proxy grid is ready, polling up to {@code timeoutMs}.
   */
  public boolean waitUntilReady(long timeoutMs, long pollMs) {
    long deadline = System.currentTimeMillis() + timeoutMs;
    while (System.currentTimeMillis() < deadline) {
      try {
        if (isStarted()) {
          return true;
        }
        Thread.sleep(pollMs);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return false;
      }
    }
    return isStarted();
  }

  // ── CA certificate ────────────────────────────────────────────────

  /**
   * Download the CA certificate PEM for the current instance.
   */
  public String getCaCertificatePem() {
    return null;
  }
}
