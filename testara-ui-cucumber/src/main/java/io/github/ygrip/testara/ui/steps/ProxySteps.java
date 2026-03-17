package io.github.ygrip.testara.ui.steps;

import static io.github.ygrip.testara.command.CommandExecutor.executeCommand;
import static io.github.ygrip.testara.core.support.CommonHelper.isBlank;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.equalToIgnoringCase;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.text.IsEmptyString.emptyOrNullString;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import io.github.ygrip.testara.core.context.TestComponent;
import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.core.mapper.MapperHelper;
import io.github.ygrip.testara.core.model.DefaultProperties;
import io.github.ygrip.testara.core.registry.RegistryScope;
import io.github.ygrip.testara.core.transformer.TransformerService;
import io.github.ygrip.testara.ui.context.TestUI;
import io.github.ygrip.testara.ui.model.MitmProxyCreateInstanceResponse;
import io.github.ygrip.testara.ui.model.MitmProxyHealthResponse;
import io.github.ygrip.testara.ui.model.MitmProxyInstanceDetail;
import io.github.ygrip.testara.ui.model.MitmProxyMessageResponse;
import io.github.ygrip.testara.ui.model.MitmProxyRenewResponse;
import io.github.ygrip.testara.ui.model.MitmProxyRule;
import io.github.ygrip.testara.ui.model.MitmProxyRuleResponse;
import io.github.ygrip.testara.ui.model.ProxyRuleCreation;
import io.github.ygrip.testara.ui.proxy.AbstractProxy;
import io.github.ygrip.testara.ui.proxy.ProxyInstanceManager;

import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.extern.log4j.Log4j2;

/**
 * Cucumber step definitions for proxy lifecycle and MitmProxy rule management.
 *
 * @author yunaz.ramadhan on 6/22/2020
 */
@Log4j2
@TestComponent(scope = RegistryScope.TEST)
public class ProxySteps {

  /**
   * Resolve the active proxy for the current test thread via {@link ProxyInstanceManager}.
   * The proxy is registered during driver setup by each {@code ProxyFactory} implementation.
   */
  @SuppressWarnings("rawtypes")
  private AbstractProxy proxy() {
    AbstractProxy proxy = ProxyInstanceManager.currentProxy();
    assertThat("No proxy has been registered for the current thread. "
      + "Ensure the driver was started with a proxy type (standalone, embedded, or mitmproxy).", proxy, notNullValue());
    return proxy;
  }

  private void checkProxy() {
    assertThat("No proxy instance is started", proxy().isStarted(), equalTo(true));
  }

  // ── Driver setup with proxy ──────────────────────────────────────

  @Given("^(\\w+) using (\\w+) in (desktop|mobile|android|ios) with (standalone|embedded|mitmproxy) proxy$")
  public void actorNamedUsingDeviceWithProxy(String actorName, String application, String platform, String proxyType)
    throws Throwable {
    TestUI.withDefaultEngine()
      .forDriver(application, platform, proxyType);
  }

  // ── Instance lifecycle ───────────────────────────────────────────

  @When("^(.+) create proxy instance$")
  public void createProxyInstance(String identifier) throws Throwable {
    MitmProxyCreateInstanceResponse response = proxy().createInstance();
    assertThat("Failed to create proxy instance", response, notNullValue());
    log.info("Created proxy instance: {} on port {}", response.getInstanceId(), response.getPort());
  }

  @When("^(.+) create proxy instance with TTL (\\d+)s$")
  public void createProxyInstanceWithTtl(String identifier, int ttl) throws Throwable {
    MitmProxyCreateInstanceResponse response = proxy().createInstance(ttl);
    assertThat("Failed to create proxy instance", response, notNullValue());
    log.info("Created proxy instance: {} on port {} (TTL={}s)", response.getInstanceId(), response.getPort(),
      response.getTtl());
  }

  @When("^(.+) renew proxy instance$")
  public void renewProxyInstance(String identifier) throws Throwable {
    checkProxy();
    MitmProxyRenewResponse response = proxy().renewInstance();
    assertThat("Failed to renew proxy instance", response, notNullValue());
    log.info("Renewed proxy instance (TTL={}s, expires={})", response.getTtl(), response.getExpiresAt());
  }

  @When("^(.+) renew proxy instance with TTL (\\d+)s$")
  public void renewProxyInstanceWithTtl(String identifier, int ttl) throws Throwable {
    checkProxy();
    MitmProxyRenewResponse response = proxy().renewInstance(ttl);
    assertThat("Failed to renew proxy instance", response, notNullValue());
    log.info("Renewed proxy instance (TTL={}s, expires={})", response.getTtl(), response.getExpiresAt());
  }

  @When("^(.+) destroy proxy instance$")
  public void destroyProxyInstance(String identifier) throws Throwable {
    checkProxy();
    MitmProxyInstanceDetail detail = proxy().getInstanceDetail();
    assertThat("Instance detail is null", detail, notNullValue());
    proxy().destroyInstance(detail.getInstanceId());
    log.info("Destroyed proxy instance: {}", detail.getInstanceId());
  }

  @When("^(.+) destroy proxy instance with cleanup$")
  public void destroyProxyInstanceWithCleanup(String identifier) throws Throwable {
    checkProxy();
    MitmProxyInstanceDetail detail = proxy().getInstanceDetail();
    assertThat("Instance detail is null", detail, notNullValue());
    proxy().destroyInstance(detail.getInstanceId(), true);
    log.info("Destroyed proxy instance with cleanup: {}", detail.getInstanceId());
  }

  // ── Caching ──────────────────────────────────────────────────────

  @When("^(.+) disable proxy cache$")
  public void disableProxyCache(String identifier) throws Throwable {
    checkProxy();
    proxy().disableCaching();
    log.info("Disabled caching via proxy (cache-negotiation headers will be stripped)");
  }

  @When("^(.+) disable proxy cache for URL containing \"([^\"]*)\"$")
  public void disableProxyCacheForUrl(String identifier, String urlContains) throws Throwable {
    checkProxy();
    proxy().disableCaching(urlContains);
    log.info("Disabled caching via proxy for URLs containing '{}'", urlContains);
  }

  // ── Rule management — creation ───────────────────────────────────

  @SuppressWarnings("unchecked")
  @When("^(.+) create proxy rule to mock response for URL containing \"([^\"]*)\" with status (\\d+) and body \"([^\"]*)\"$")
  public void createMockResponseRule(String identifier, String urlContains, int statusCode, String body)
    throws Throwable {
    checkProxy();
    urlContains = executeCommand(urlContains);
    body = MapperHelper.toString(executeCommand(body));
    MitmProxyRule rule = MitmProxyRule.mockResponse(urlContains, statusCode, body);
    MitmProxyMessageResponse response = proxy().createRule(rule);
    log.info("Created mock response rule for '{}' -> {} : {}", urlContains, statusCode,
      response != null ? response.getMessage() : "no response");
  }

  @SuppressWarnings("unchecked")
  @When("^(.+) create proxy rule to mock response for URL containing \"([^\"]*)\" with status (\\d+)$")
  public void createMockResponseRuleWithDocString(String identifier, String urlContains, int statusCode, String body)
    throws Throwable {
    checkProxy();
    urlContains = executeCommand(urlContains);
    body = MapperHelper.toString(executeCommand(body));
    MitmProxyRule rule = MitmProxyRule.mockResponse(urlContains, statusCode, body);
    MitmProxyMessageResponse response = proxy().createRule(rule);
    log.info("Created mock response rule for '{}' -> {} : {}", urlContains, statusCode,
      response != null ? response.getMessage() : "no response");
  }

  @SuppressWarnings("unchecked")
  @When("^(.+) create proxy rule to block URL containing \"([^\"]*)\"$")
  public void createBlockRule(String identifier, String urlContains) throws Throwable {
    checkProxy();
    urlContains = executeCommand(urlContains);
    MitmProxyRule rule = MitmProxyRule.block(urlContains);
    MitmProxyMessageResponse response = proxy().createRule(rule);
    log.info("Created block rule for '{}' : {}", urlContains,
      response != null ? response.getMessage() : "no response");
  }

  @SuppressWarnings("unchecked")
  @When("^(.+) create proxy rule to replace response body for URL containing \"([^\"]*)\" from \"([^\"]*)\" to \"([^\"]*)\"$")
  public void createReplaceResponseBodyRule(String identifier, String urlContains, String from, String to)
    throws Throwable {
    checkProxy();
    urlContains = executeCommand(urlContains);
    from = executeCommand(from);
    to = executeCommand(to);
    MitmProxyRule rule = MitmProxyRule.replaceResponseBody(urlContains, from, to);
    MitmProxyMessageResponse response = proxy().createRule(rule);
    log.info("Created replace response body rule for '{}' : {}", urlContains,
      response != null ? response.getMessage() : "no response");
  }

  @SuppressWarnings("unchecked")
  @When("^(.+) create proxy rule to replace request body for URL containing \"([^\"]*)\" from \"([^\"]*)\" to \"([^\"]*)\"$")
  public void createReplaceRequestBodyRule(String identifier, String urlContains, String from, String to)
    throws Throwable {
    checkProxy();
    urlContains = executeCommand(urlContains);
    from = executeCommand(from);
    to = executeCommand(to);
    MitmProxyRule rule = MitmProxyRule.replaceRequestBody(urlContains, from, to);
    MitmProxyMessageResponse response = proxy().createRule(rule);
    log.info("Created replace request body rule for '{}' : {}", urlContains,
      response != null ? response.getMessage() : "no response");
  }

  @SuppressWarnings("unchecked")
  @When("^(.+) create proxy rule to set request headers for URL containing \"([^\"]*)\"$")
  public void createSetRequestHeadersRule(String identifier, String urlContains, DataTable table) throws Throwable {
    checkProxy();
    urlContains = executeCommand(urlContains);
    Map<String, String> headers = resolveDataTableMap(table);
    MitmProxyRule rule = MitmProxyRule.setRequestHeaders(urlContains, headers);
    MitmProxyMessageResponse response = proxy().createRule(rule);
    log.info("Created set request headers rule for '{}' ({} headers) : {}", urlContains, headers.size(),
      response != null ? response.getMessage() : "no response");
  }

  @SuppressWarnings("unchecked")
  @When("^(.+) create proxy rule to set response headers for URL containing \"([^\"]*)\"$")
  public void createSetResponseHeadersRule(String identifier, String urlContains, DataTable table) throws Throwable {
    checkProxy();
    urlContains = executeCommand(urlContains);
    Map<String, String> headers = resolveDataTableMap(table);
    MitmProxyRule rule = MitmProxyRule.setResponseHeaders(urlContains, headers);
    MitmProxyMessageResponse response = proxy().createRule(rule);
    log.info("Created set response headers rule for '{}' ({} headers) : {}", urlContains, headers.size(),
      response != null ? response.getMessage() : "no response");
  }

  @SuppressWarnings("unchecked")
  @When("^(.+) create proxy rule to set query params for URL containing \"([^\"]*)\"$")
  public void createSetQueryParamsRule(String identifier, String urlContains, DataTable table) throws Throwable {
    checkProxy();
    urlContains = executeCommand(urlContains);
    Map<String, String> params = resolveDataTableMap(table);
    MitmProxyRule rule = MitmProxyRule.setQueryParams(urlContains, params);
    MitmProxyMessageResponse response = proxy().createRule(rule);
    log.info("Created set query params rule for '{}' ({} params) : {}", urlContains, params.size(),
      response != null ? response.getMessage() : "no response");
  }

  // ── Rule management — file-based creation ─────────────────────────

  /**
   * Create a proxy rule from a JSON specification file.
   * <p>Example Gherkin:</p>
   * <pre>When user create proxy rule from "github/intercept network response from user avatar"</pre>
   * <p>Resolves the file at {@code src/test/resources/github/intercept network response from user avatar.json},
   * deserializes it to {@link ProxyRuleCreation}, and sends the resulting rule to the proxy.</p>
   */
  @When("^(.+) create proxy rule from \"([^\"]*)\"$")
  public void createProxyRuleFromSpecification(String identifier, String ruleSpecificationPath) throws Throwable {
    checkProxy();
    ruleSpecificationPath = executeCommand(ruleSpecificationPath);
    String baseFolder = resolveBaseFolder();
    TransformerService transformerService = new TransformerService();
    ProxyRuleCreation creation = transformerService
      .fromTemplate(ruleSpecificationPath)
      .to(ProxyRuleCreation.class);
    assertThat("Failed to load proxy rule specification from: " + ruleSpecificationPath, creation, notNullValue());
    MitmProxyRule rule = creation.toMitmProxyRule(baseFolder);
    MitmProxyMessageResponse response = proxy().createRule(rule);
    log.info("Created proxy rule from '{}' : {}", ruleSpecificationPath,
      response != null ? response.getMessage() : "no response");
  }

  /**
   * Create a proxy rule from a JSON specification file, with DataTable overrides.
   * <p>Example Gherkin:</p>
   * <pre>
   * When user create proxy rule from "github/intercept network response from user avatar"
   *   | $.match.urlContains               | avatars.githubusercontent.com |
   *   | $.action.modifyResponse.statusCode | 404                          |
   * </pre>
   */
  @When("^(.+) create proxy rule from \"([^\"]*)\" with$")
  public void createProxyRuleFromSpecificationWithOverrides(String identifier, String ruleSpecificationPath,
    DataTable table) throws Throwable {
    checkProxy();
    ruleSpecificationPath = executeCommand(ruleSpecificationPath);
    String baseFolder = resolveBaseFolder();
    TransformerService transformerService = new TransformerService();
    ProxyRuleCreation creation = transformerService
      .fromTemplate(ruleSpecificationPath)
      .sourceData(table.cells())
      .to(ProxyRuleCreation.class);
    assertThat("Failed to load proxy rule specification from: " + ruleSpecificationPath, creation, notNullValue());
    MitmProxyRule rule = creation.toMitmProxyRule(baseFolder);
    MitmProxyMessageResponse response = proxy().createRule(rule);
    log.info("Created proxy rule from '{}' (with overrides) : {}", ruleSpecificationPath,
      response != null ? response.getMessage() : "no response");
  }

  /**
   * Replace an image asset at matching URLs with a local file from test resources.
   * <p>Example Gherkin:</p>
   * <pre>When user create proxy rule to replace image at URL containing "avatar.png" with file "github/images/replacement.png" as "image/png"</pre>
   */
  @When("^(.+) create proxy rule to replace image at URL containing \"([^\"]*)\" with file \"([^\"]*)\" as \"([^\"]*)\"$")
  public void createReplaceImageRule(String identifier, String urlContains, String filePath, String contentType)
    throws Throwable {
    checkProxy();
    urlContains = executeCommand(urlContains);
    filePath = executeCommand(filePath);
    String baseFolder = resolveBaseFolder();
    java.io.File imageFile = new java.io.File(baseFolder + filePath);
    assertThat("Image file not found: " + imageFile.getAbsolutePath(), imageFile.exists(), equalTo(true));
    MitmProxyRule rule = MitmProxyRule.replaceImage(urlContains, imageFile, contentType);
    MitmProxyMessageResponse response = proxy().createRule(rule);
    log.info("Created image replacement rule for '{}' -> {} : {}", urlContains, filePath,
      response != null ? response.getMessage() : "no response");
  }

  // ── Rule management — CRUD operations ────────────────────────────

  @When("^(.+) delete proxy rule at index (\\d+)$")
  public void deleteProxyRule(String identifier, int index) throws Throwable {
    checkProxy();
    MitmProxyMessageResponse response = proxy().deleteRule(index);
    log.info("Deleted proxy rule at index {} : {}", index,
      response != null ? response.getMessage() : "no response");
  }

  @When("^(.+) toggle proxy rule at index (\\d+)$")
  public void toggleProxyRule(String identifier, int index) throws Throwable {
    checkProxy();
    MitmProxyMessageResponse response = proxy().toggleRule(index);
    log.info("Toggled proxy rule at index {} : {}", index,
      response != null ? response.getMessage() : "no response");
  }

  @When("^(.+) clear all proxy rules$")
  public void clearAllProxyRules(String identifier) throws Throwable {
    checkProxy();
    proxy().clearRules();
    log.info("Cleared all proxy rules");
  }

  // ── Assertions ───────────────────────────────────────────────────

  @Then("^(.+) proxy should be started$")
  public void proxyShouldBeStarted(String identifier) throws Throwable {
    assertThat("Proxy should be started", proxy().isStarted(), equalTo(true));
  }

  @Then("^(.+) proxy should not be started$")
  public void proxyShouldNotBeStarted(String identifier) throws Throwable {
    assertThat("Proxy should not be started", proxy().isStarted(), equalTo(false));
  }

  @Then("^(.+) proxy should have (\\d+) rules$")
  public void proxyShouldHaveRules(String identifier, int expectedCount) throws Throwable {
    checkProxy();
    List<MitmProxyRuleResponse> rules = proxy().listRules();
    assertThat("Proxy rule count mismatch", rules.size(), equalTo(expectedCount));
  }

  @Then("^(.+) proxy health status should be \"([^\"]*)\"$")
  public void proxyHealthStatusShouldBe(String identifier, String expectedStatus) throws Throwable {
    MitmProxyHealthResponse health = proxy().healthCheck();
    assertThat("Proxy health response is null", health, notNullValue());
    assertThat("Proxy health status mismatch", health.getStatus(), equalToIgnoringCase(expectedStatus));
  }

  @Then("^(.+) proxy instance remaining TTL should be greater than (\\d+)s$")
  public void proxyInstanceRemainingTtlShouldBeGreaterThan(String identifier, int seconds) throws Throwable {
    checkProxy();
    MitmProxyInstanceDetail detail = proxy().getInstanceDetail();
    assertThat("Instance detail is null", detail, notNullValue());
    assertThat(
      String.format("Remaining TTL should be greater than %ds", seconds),
      detail.getRemainingSeconds(), greaterThan((double) seconds)
    );
  }

  @Then("^(.+) proxy instance should have status \"([^\"]*)\"$")
  public void proxyInstanceShouldHaveStatus(String identifier, String expectedStatus) throws Throwable {
    checkProxy();
    MitmProxyInstanceDetail detail = proxy().getInstanceDetail();
    assertThat("Instance detail is null", detail, notNullValue());
    assertThat("Instance status mismatch", detail.getStatus(), equalToIgnoringCase(expectedStatus));
  }

  @Then("^(.+) proxy CA certificate should be available$")
  public void proxyCaCertificateShouldBeAvailable(String identifier) throws Throwable {
    checkProxy();
    String pem = proxy().getCaCertificatePem();
    assertThat("CA certificate PEM should not be null or empty", pem, not(emptyOrNullString()));
  }

  // ── Helpers ──────────────────────────────────────────────────────

  private Map<String, String> resolveDataTableMap(DataTable table) {
    return new TransformerService()
      .sourceData(table.cells())
      .to(new TypeReference<>() {});
  }

  private String resolveBaseFolder() {
    String templateFolder = null;
    try {
      templateFolder = TestFramework.context().configuration().get(DefaultProperties.class).getTemplateFolder();
    } catch (Exception ignored) {
    }
    return String.format("%s%s",
      System.getProperty("user.dir"),
      isBlank(templateFolder) ? "/src/test/resources/" : templateFolder);
  }
}
