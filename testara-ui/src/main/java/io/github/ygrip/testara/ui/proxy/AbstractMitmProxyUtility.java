package io.github.ygrip.testara.ui.proxy;

import java.util.Collections;
import java.util.List;

import io.github.ygrip.mitmproxy.grid.client.MitmProxyGridClient;
import io.github.ygrip.mitmproxy.grid.client.model.MitmProxyCreateInstanceResponse;
import io.github.ygrip.mitmproxy.grid.client.model.MitmProxyHealthResponse;
import io.github.ygrip.mitmproxy.grid.client.model.MitmProxyInstanceDetail;
import io.github.ygrip.mitmproxy.grid.client.model.MitmProxyMessageResponse;
import io.github.ygrip.mitmproxy.grid.client.model.MitmProxyRenewResponse;
import io.github.ygrip.mitmproxy.grid.client.model.MitmProxyRule;
import io.github.ygrip.mitmproxy.grid.client.model.MitmProxyRuleResponse;
import io.github.ygrip.testara.core.data.DataHolder;
import io.github.ygrip.testara.core.support.CommonHelper;
import io.github.ygrip.testara.ui.model.TestaraProxyModel;

import lombok.extern.log4j.Log4j2;

/**
 * Shared lifecycle base for all MitmProxy Grid driver adapters.
 * Subclasses only need to implement {@link #buildProxy(String, int)} to convert the
 * created instance port into the driver-specific proxy representation.
 *
 * @param <P> driver-specific proxy type (e.g. {@code org.openqa.selenium.Proxy} or {@code String})
 */
@Log4j2
public abstract class AbstractMitmProxyUtility<P> extends AbstractProxy<P> {

  private MitmProxyGridClient mitmClient;
  protected String mitmApiUrl;
  protected String instanceId;
  protected Integer instanceTtl;

  public AbstractMitmProxyUtility(DataHolder dataHolder) {
    super(dataHolder);
  }

  public void setMitmProxyApiUrl(String apiUrl) {
    this.mitmApiUrl = apiUrl;
  }

  public void setInstanceTtl(Integer ttlSeconds) {
    this.instanceTtl = ttlSeconds;
  }

  public String getInstanceId() {
    return instanceId;
  }

  protected MitmProxyGridClient getClient() {
    if (mitmClient == null) {
      mitmClient = new MitmProxyGridClient(mitmApiUrl);
    }
    return mitmClient;
  }

  /**
   * Build the driver-specific proxy object from the grid instance host and port.
   */
  protected abstract P buildProxy(String proxyHost, int port);

  // ── Lifecycle ──────────────────────────────────────────────────────

  @Override
  public void start() {
    if (!CommonHelper.isBlank(getProxy())) {
      log.debug("#{} already started", getClass().getSimpleName());
      return;
    }
    try {
      log.debug("#Starting {} via API at {}", getClass().getSimpleName(), mitmApiUrl);
      MitmProxyGridClient client = getClient();
      if (!client.waitUntilReady(30_000, 1_000)) {
        log.warn("MitmProxy grid not ready after 30s, attempting to proceed anyway");
      }
      MitmProxyCreateInstanceResponse created = client.createInstance(instanceTtl);
      instanceId = created.getInstanceId();
      setPort(created.getPort());
      log.debug("#Created MitmProxy instance {} on port {} (ttl={}s, expires={})",
          instanceId, getPort(), created.getTtl(), created.getExpiresAt());

      TestaraProxyModel proxyModel = new TestaraProxyModel(getProxyAddress());
      setProxy(buildProxy(proxyModel.getProxyHost(), getPort()));
      log.info("#{} started on port {}", getClass().getSimpleName(), getPort());
    } catch (Exception e) {
      log.error("Failed to start {}", getClass().getSimpleName(), e);
    }
  }

  @Override
  public void stop() {
    if (CommonHelper.isBlank(getProxy())) {
      log.info("#No active {} proxy, ignoring stop", getClass().getSimpleName());
      return;
    }
    try {
      if (instanceId != null) {
        log.info("#Destroying MitmProxy instance {} (cleanup=true)", instanceId);
        getClient().destroyInstance(instanceId, true);
        instanceId = null;
      }
    } catch (Exception e) {
      log.warn("Error stopping MitmProxy instance: {}", e.getMessage());
    }
    setProxy(null);
    setPort(null);
  }

  @Override
  public boolean isStarted() {
    return !CommonHelper.isBlank(getProxy()) && getPort() != null && instanceId != null;
  }

  @Override
  public void afterScenario() {
    if (!isStarted()) return;
    try {
      clearRules();
    } catch (Exception e) {
      log.warn("Failed to clear rules on instance {} after scenario: {}", instanceId, e.getMessage());
    }
    try {
      getClient().renewInstance(instanceId, instanceTtl);
      log.debug("Renewed MitmProxy instance {} for next scenario", instanceId);
    } catch (Exception e) {
      log.warn("Failed to renew instance {} after scenario: {}", instanceId, e.getMessage());
    }
  }

  @Override
  public List<?> getRequestData() { return Collections.emptyList(); }

  @Override
  public List<?> getResponseData() { return Collections.emptyList(); }

  // ── Rule management ────────────────────────────────────────────────

  @Override
  public MitmProxyMessageResponse createRule(MitmProxyRule rule) {
    try {
      return getClient().createRule(instanceId, rule);
    } catch (Exception e) {
      log.error("Failed to create rule on instance {}", instanceId, e);
      return null;
    }
  }

  @Override
  public List<MitmProxyRuleResponse> listRules() {
    try {
      return getClient().listRules(instanceId);
    } catch (Exception e) {
      log.error("Failed to list rules on instance {}", instanceId, e);
      return Collections.emptyList();
    }
  }

  @Override
  public MitmProxyMessageResponse deleteRule(int ruleIndex) {
    try {
      return getClient().deleteRule(instanceId, ruleIndex);
    } catch (Exception e) {
      log.error("Failed to delete rule {} on instance {}", ruleIndex, instanceId, e);
      return null;
    }
  }

  @Override
  public MitmProxyMessageResponse toggleRule(int ruleIndex) {
    try {
      return getClient().toggleRule(instanceId, ruleIndex);
    } catch (Exception e) {
      log.error("Failed to toggle rule {} on instance {}", ruleIndex, instanceId, e);
      return null;
    }
  }

  @Override
  public void clearRules() {
    try {
      getClient().clearAllRules(instanceId);
    } catch (Exception e) {
      log.error("Failed to clear rules on instance {}", instanceId, e);
    }
  }

  // ── Instance lifecycle ─────────────────────────────────────────────

  @Override
  public MitmProxyCreateInstanceResponse createInstance(Integer ttlSeconds) {
    try {
      MitmProxyCreateInstanceResponse resp = getClient().createInstance(ttlSeconds);
      instanceId = resp.getInstanceId();
      setPort(resp.getPort());
      return resp;
    } catch (Exception e) {
      log.error("Failed to create MitmProxy instance", e);
      return null;
    }
  }

  @Override
  public MitmProxyInstanceDetail getInstanceDetail() {
    try {
      return getClient().getInstance(instanceId);
    } catch (Exception e) {
      log.error("Failed to get instance detail for {}", instanceId, e);
      return null;
    }
  }

  @Override
  public MitmProxyRenewResponse renewInstance(Integer ttlSeconds) {
    try {
      return getClient().renewInstance(instanceId, ttlSeconds);
    } catch (Exception e) {
      log.error("Failed to renew instance {}", instanceId, e);
      return null;
    }
  }

  @Override
  public void destroyInstance(String instanceId, boolean cleanup) {
    try {
      getClient().destroyInstance(instanceId, cleanup);
      if (instanceId.equals(this.instanceId)) {
        this.instanceId = null;
      }
    } catch (Exception e) {
      log.error("Failed to destroy instance {}", instanceId, e);
    }
  }

  // ── Health / certificate ───────────────────────────────────────────

  @Override
  public MitmProxyHealthResponse healthCheck() {
    try {
      return getClient().health();
    } catch (Exception e) {
      log.error("MitmProxy health check failed", e);
      return MitmProxyHealthResponse.builder().status("error").build();
    }
  }

  @Override
  public String getCaCertificatePem() {
    try {
      return getClient().getCaCertificate(instanceId);
    } catch (Exception e) {
      log.error("Failed to retrieve CA certificate for instance {}", instanceId, e);
      return null;
    }
  }
}
