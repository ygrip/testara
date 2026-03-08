package io.github.ygrip.testara.ui.selenium.proxy;

import java.util.Collections;
import java.util.List;

import com.browserup.harreader.model.HarRequest;
import com.browserup.harreader.model.HarResponse;
import io.github.ygrip.testara.core.context.TestComponent;
import io.github.ygrip.testara.core.data.DataHolder;
import io.github.ygrip.testara.core.registry.RegistryScope;
import io.github.ygrip.testara.core.support.CommonHelper;
import io.github.ygrip.testara.ui.model.TestaraProxyModel;
import io.github.ygrip.testara.ui.model.MitmProxyCreateInstanceResponse;
import io.github.ygrip.testara.ui.model.MitmProxyHealthResponse;
import io.github.ygrip.testara.ui.model.MitmProxyInstanceDetail;
import io.github.ygrip.testara.ui.model.MitmProxyMessageResponse;
import io.github.ygrip.testara.ui.model.MitmProxyRenewResponse;
import io.github.ygrip.testara.ui.model.MitmProxyRule;
import io.github.ygrip.testara.ui.model.MitmProxyRuleResponse;
import io.github.ygrip.testara.ui.proxy.AbstractProxy;
import io.github.ygrip.testara.ui.proxy.MitmProxyClient;
import org.openqa.selenium.Proxy;

import lombok.extern.log4j.Log4j2;

/**
 * MitmProxy Grid proxy utility for Selenium WebDriver.
 * Manages instance lifecycle and creates a Selenium {@link Proxy}
 * pointing at the mitmproxy instance so that browser traffic is intercepted.
 */
@TestComponent(scope = RegistryScope.TEST)
@Log4j2
public class MitmProxySeleniumUtility extends AbstractProxy<Proxy> {

  private MitmProxyClient mitmClient;
  private String mitmApiUrl;
  private String instanceId;
  private Integer instanceTtl;

  public MitmProxySeleniumUtility(DataHolder dataHolder) {
    super(dataHolder);
  }

  public void setMitmProxyApiUrl(String apiUrl) {
    this.mitmApiUrl = apiUrl;
  }

  /**
   * Set the TTL (seconds) for new instances. If null, uses server default.
   */
  public void setInstanceTtl(Integer ttlSeconds) {
    this.instanceTtl = ttlSeconds;
  }

  public String getInstanceId() {
    return instanceId;
  }

  private MitmProxyClient getClient() {
    if (mitmClient == null) {
      mitmClient = new MitmProxyClient(mitmApiUrl);
    }
    return mitmClient;
  }

  @Override
  public void start() {
    if (!CommonHelper.isBlank(getProxy())) {
      log.debug("#MitmProxy Selenium utility already started");
      return;
    }
    try {
      log.debug("#Starting MitmProxy Selenium utility via API at {}", mitmApiUrl);

      MitmProxyClient client = getClient();
      if (!client.waitUntilReady(30_000, 1_000)) {
        log.warn("MitmProxy grid not ready after 30s, attempting to proceed anyway");
      }

      MitmProxyCreateInstanceResponse created = client.createInstance(instanceTtl);
      instanceId = created.getInstanceId();
      setPort(created.getPort());
      log.debug("#Created MitmProxy instance {} on port {} (ttl={}s, expires={})",
          instanceId, getPort(), created.getTtl(), created.getExpiresAt());

      TestaraProxyModel proxyModel = new TestaraProxyModel(getProxyAddress());
      String proxyHost = proxyModel.getProxyHost();

      Proxy seleniumProxy = new Proxy();
      String proxyAddr = String.format("%s:%d", proxyHost, getPort());
      seleniumProxy.setHttpProxy(proxyAddr);
      seleniumProxy.setSslProxy(proxyAddr);
      seleniumProxy.setFtpProxy(proxyAddr);
      setProxy(seleniumProxy);

      log.info("#MitmProxy Selenium proxy started at {}", proxyAddr);
    } catch (Exception e) {
      log.error("Failed to start MitmProxy Selenium utility", e);
    }
  }

  @Override
  public void stop() {
    if (CommonHelper.isBlank(getProxy())) {
      log.info("#No active MitmProxy Selenium proxy, ignoring stop");
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

  /**
   * Clear rules and renew the TTL so the same instance can be reused by the next
   * scenario on this thread.
   */
  @Override
  public void afterScenario() {
    if (!isStarted()) {
      return;
    }
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

  // ── Network data ──────────────────────────────────────────────────

  @Override
  public List<HarRequest> getRequestData() {
    return Collections.emptyList();
  }

  @Override
  public List<HarResponse> getResponseData() {
    return Collections.emptyList();
  }

  // ── Rule management ───────────────────────────────────────────────

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

  // ── Instance lifecycle ────────────────────────────────────────────

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

  // ── Health ────────────────────────────────────────────────────────

  @Override
  public MitmProxyHealthResponse healthCheck() {
    try {
      return getClient().health();
    } catch (Exception e) {
      log.error("MitmProxy health check failed", e);
      return MitmProxyHealthResponse.builder().status("error").build();
    }
  }

  // ── Certificate ───────────────────────────────────────────────────

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
