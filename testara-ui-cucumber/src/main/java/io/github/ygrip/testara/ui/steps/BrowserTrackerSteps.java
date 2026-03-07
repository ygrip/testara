package io.github.ygrip.testara.ui.steps;

import static io.github.ygrip.testara.command.CommandExecutor.executeCommand;
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
import io.github.ygrip.testara.core.context.Inject;
import io.github.ygrip.testara.core.context.TestComponent;
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
import io.github.ygrip.testara.ui.proxy.AbstractProxy;

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
public class BrowserTrackerSteps {

  @SuppressWarnings("rawtypes")
  @Inject
  private AbstractProxy proxyUtility;

  private void checkProxy() {
    assertThat("No proxy instance is started", proxyUtility.isStarted(), equalTo(true));
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
    MitmProxyCreateInstanceResponse response = proxyUtility.createInstance();
    assertThat("Failed to create proxy instance", response, notNullValue());
    log.info("Created proxy instance: {} on port {}", response.getInstanceId(), response.getPort());
  }

  @When("^(.+) create proxy instance with TTL (\\d+)s$")
  public void createProxyInstanceWithTtl(String identifier, int ttl) throws Throwable {
    MitmProxyCreateInstanceResponse response = proxyUtility.createInstance(ttl);
    assertThat("Failed to create proxy instance", response, notNullValue());
    log.info("Created proxy instance: {} on port {} (TTL={}s)", response.getInstanceId(), response.getPort(),
      response.getTtl());
  }

  @When("^(.+) renew proxy instance$")
  public void renewProxyInstance(String identifier) throws Throwable {
    checkProxy();
    MitmProxyRenewResponse response = proxyUtility.renewInstance();
    assertThat("Failed to renew proxy instance", response, notNullValue());
    log.info("Renewed proxy instance (TTL={}s, expires={})", response.getTtl(), response.getExpiresAt());
  }

  @When("^(.+) renew proxy instance with TTL (\\d+)s$")
  public void renewProxyInstanceWithTtl(String identifier, int ttl) throws Throwable {
    checkProxy();
    MitmProxyRenewResponse response = proxyUtility.renewInstance(ttl);
    assertThat("Failed to renew proxy instance", response, notNullValue());
    log.info("Renewed proxy instance (TTL={}s, expires={})", response.getTtl(), response.getExpiresAt());
  }

  @When("^(.+) destroy proxy instance$")
  public void destroyProxyInstance(String identifier) throws Throwable {
    checkProxy();
    MitmProxyInstanceDetail detail = proxyUtility.getInstanceDetail();
    assertThat("Instance detail is null", detail, notNullValue());
    proxyUtility.destroyInstance(detail.getInstanceId());
    log.info("Destroyed proxy instance: {}", detail.getInstanceId());
  }

  @When("^(.+) destroy proxy instance with cleanup$")
  public void destroyProxyInstanceWithCleanup(String identifier) throws Throwable {
    checkProxy();
    MitmProxyInstanceDetail detail = proxyUtility.getInstanceDetail();
    assertThat("Instance detail is null", detail, notNullValue());
    proxyUtility.destroyInstance(detail.getInstanceId(), true);
    log.info("Destroyed proxy instance with cleanup: {}", detail.getInstanceId());
  }

  // ── Rule management — creation ───────────────────────────────────

  @SuppressWarnings("unchecked")
  @When("^(.+) create proxy rule to mock response for URL containing \"([^\"]*)\" with status (\\d+) and body \"([^\"]*)\"$")
  public void createMockResponseRule(String identifier, String urlContains, int statusCode, String body)
    throws Throwable {
    checkProxy();
    urlContains = executeCommand(urlContains);
    body = executeCommand(body);
    MitmProxyRule rule = MitmProxyRule.mockResponse(urlContains, statusCode, body);
    MitmProxyMessageResponse response = proxyUtility.createRule(rule);
    log.info("Created mock response rule for '{}' -> {} : {}", urlContains, statusCode,
      response != null ? response.getMessage() : "no response");
  }

  @SuppressWarnings("unchecked")
  @When("^(.+) create proxy rule to mock response for URL containing \"([^\"]*)\" with status (\\d+)$")
  public void createMockResponseRuleWithDocString(String identifier, String urlContains, int statusCode, String body)
    throws Throwable {
    checkProxy();
    urlContains = executeCommand(urlContains);
    body = executeCommand(body);
    MitmProxyRule rule = MitmProxyRule.mockResponse(urlContains, statusCode, body);
    MitmProxyMessageResponse response = proxyUtility.createRule(rule);
    log.info("Created mock response rule for '{}' -> {} : {}", urlContains, statusCode,
      response != null ? response.getMessage() : "no response");
  }

  @SuppressWarnings("unchecked")
  @When("^(.+) create proxy rule to block URL containing \"([^\"]*)\"$")
  public void createBlockRule(String identifier, String urlContains) throws Throwable {
    checkProxy();
    urlContains = executeCommand(urlContains);
    MitmProxyRule rule = MitmProxyRule.block(urlContains);
    MitmProxyMessageResponse response = proxyUtility.createRule(rule);
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
    MitmProxyMessageResponse response = proxyUtility.createRule(rule);
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
    MitmProxyMessageResponse response = proxyUtility.createRule(rule);
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
    MitmProxyMessageResponse response = proxyUtility.createRule(rule);
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
    MitmProxyMessageResponse response = proxyUtility.createRule(rule);
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
    MitmProxyMessageResponse response = proxyUtility.createRule(rule);
    log.info("Created set query params rule for '{}' ({} params) : {}", urlContains, params.size(),
      response != null ? response.getMessage() : "no response");
  }

  // ── Rule management — CRUD operations ────────────────────────────

  @When("^(.+) delete proxy rule at index (\\d+)$")
  public void deleteProxyRule(String identifier, int index) throws Throwable {
    checkProxy();
    MitmProxyMessageResponse response = proxyUtility.deleteRule(index);
    log.info("Deleted proxy rule at index {} : {}", index,
      response != null ? response.getMessage() : "no response");
  }

  @When("^(.+) toggle proxy rule at index (\\d+)$")
  public void toggleProxyRule(String identifier, int index) throws Throwable {
    checkProxy();
    MitmProxyMessageResponse response = proxyUtility.toggleRule(index);
    log.info("Toggled proxy rule at index {} : {}", index,
      response != null ? response.getMessage() : "no response");
  }

  @When("^(.+) clear all proxy rules$")
  public void clearAllProxyRules(String identifier) throws Throwable {
    checkProxy();
    proxyUtility.clearRules();
    log.info("Cleared all proxy rules");
  }

  // ── Assertions ───────────────────────────────────────────────────

  @Then("^(.+) proxy should be started$")
  public void proxyShouldBeStarted(String identifier) throws Throwable {
    assertThat("Proxy should be started", proxyUtility.isStarted(), equalTo(true));
  }

  @Then("^(.+) proxy should not be started$")
  public void proxyShouldNotBeStarted(String identifier) throws Throwable {
    assertThat("Proxy should not be started", proxyUtility.isStarted(), equalTo(false));
  }

  @Then("^(.+) proxy should have (\\d+) rules$")
  public void proxyShouldHaveRules(String identifier, int expectedCount) throws Throwable {
    checkProxy();
    List<MitmProxyRuleResponse> rules = proxyUtility.listRules();
    assertThat("Proxy rule count mismatch", rules.size(), equalTo(expectedCount));
  }

  @Then("^(.+) proxy health status should be \"([^\"]*)\"$")
  public void proxyHealthStatusShouldBe(String identifier, String expectedStatus) throws Throwable {
    MitmProxyHealthResponse health = proxyUtility.healthCheck();
    assertThat("Proxy health response is null", health, notNullValue());
    assertThat("Proxy health status mismatch", health.getStatus(), equalToIgnoringCase(expectedStatus));
  }

  @Then("^(.+) proxy instance remaining TTL should be greater than (\\d+)s$")
  public void proxyInstanceRemainingTtlShouldBeGreaterThan(String identifier, int seconds) throws Throwable {
    checkProxy();
    MitmProxyInstanceDetail detail = proxyUtility.getInstanceDetail();
    assertThat("Instance detail is null", detail, notNullValue());
    assertThat(
      String.format("Remaining TTL should be greater than %ds", seconds),
      detail.getRemainingSeconds(), greaterThan((double) seconds)
    );
  }

  @Then("^(.+) proxy instance should have status \"([^\"]*)\"$")
  public void proxyInstanceShouldHaveStatus(String identifier, String expectedStatus) throws Throwable {
    checkProxy();
    MitmProxyInstanceDetail detail = proxyUtility.getInstanceDetail();
    assertThat("Instance detail is null", detail, notNullValue());
    assertThat("Instance status mismatch", detail.getStatus(), equalToIgnoringCase(expectedStatus));
  }

  @Then("^(.+) proxy CA certificate should be available$")
  public void proxyCaCertificateShouldBeAvailable(String identifier) throws Throwable {
    checkProxy();
    String pem = proxyUtility.getCaCertificatePem();
    assertThat("CA certificate PEM should not be null or empty", pem, not(emptyOrNullString()));
  }

  // ── Helpers ──────────────────────────────────────────────────────

  private Map<String, String> resolveDataTableMap(DataTable table) {
    return new TransformerService()
      .sourceData(table.cells())
      .to(new TypeReference<>() {});
  }
}
