package io.github.ygrip.testara.api;

import io.github.ygrip.testara.api.config.ApiProperties;
import io.github.ygrip.testara.api.config.ApiSpecProperties;
import io.github.ygrip.testara.api.config.SharedServiceConfigCache;
import io.github.ygrip.testara.api.context.TestApi;
import io.github.ygrip.testara.core.BaseTests;
import io.github.ygrip.testara.core.TestWith;
import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.core.data.DataHolder;
import io.github.ygrip.testara.core.support.StringHelper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.restassured.http.ContentType;
import io.restassured.http.Method;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@Log4j2
@Tag("api")
@TestWith(properties = {"classpath:application.properties", "classpath:configuration.properties"})
public class ApiTest extends BaseTests {

  @RegisterExtension
  static WireMockExtension wiremock = WireMockExtension.newInstance()
      .options(wireMockConfig().dynamicPort().dynamicHttpsPort()) // Configure the server instance
      .build();

  @BeforeEach
  void setup() {
    System.setProperty("MOCK_HTTP_PORT", String.valueOf(wiremock.getPort()));
    TestFramework.context().configuration().reload();
    TestFramework.context()
        .get(SharedServiceConfigCache.class)
        .loadServiceConfigurations(TestFramework.context().configuration().get(ApiProperties.class),
            TestFramework.context().configuration().get(ApiSpecProperties.class));
  }

  @RepeatedTest(5)
  public void makeApiCallFromRequestPath() throws Throwable {
    Map<String, Object> mappedResponse =
        Map.of("value", "this is a testing message", "timestamp", System.currentTimeMillis());
    String response = StringHelper.prettyPrint(mappedResponse);
    // Setup the WireMock mapping stub for the test
    wiremock.stubFor(get("/fact").withHeader("Content-Type", containing("json"))
        .willReturn(ok().withHeader("Content-Type", "application/json").withBody(response)));

    DataHolder dataHolder = TestFramework.context().get(DataHolder.class);
    TestApi.rest().process("get fact");
    dataHolder.setResponse("factResponse", TestApi.response().getData().getBody());
    Object fact = dataHolder.getResponse("$['factResponse']").getValue();
    assertThat(fact, notNullValue());
    assertThat(dataHolder.getResponse("$['factResponse']['value']").getValue(), equalTo(mappedResponse.get("value")));
    assertThat(dataHolder.getResponse("$['factResponse']['timestamp']").getValue(),
        equalTo(mappedResponse.get("timestamp")));
  }

  @RepeatedTest(5)
  public void makeApiCallFromSpecification() throws Throwable {
    Map<String, Object> mappedResponse =
        Map.of("value", "this is a testing message", "timestamp", System.currentTimeMillis());
    String response = StringHelper.prettyPrint(mappedResponse);
    // Setup the WireMock mapping stub for the test
    wiremock.stubFor(get("/fact").withHeader("Content-Type", containing("json"))
        .willReturn(ok().withHeader("Content-Type", "application/json").withBody(response)));

    DataHolder dataHolder = TestFramework.context().get(DataHolder.class);
    TestApi.rest("mock-api").process(Method.GET, "/fact");
    dataHolder.setResponse("factResponse", TestApi.response().getData().getBody());
    Object fact = dataHolder.getResponse("$['factResponse']").getValue();
    assertThat(fact, notNullValue());
    assertThat(dataHolder.getResponse("$['factResponse']['value']").getValue(), equalTo(mappedResponse.get("value")));
    assertThat(dataHolder.getResponse("$['factResponse']['timestamp']").getValue(),
        equalTo(mappedResponse.get("timestamp")));
  }

  @RepeatedTest(5)
  public void makeApiCallFromUndefinedSpecification() throws Throwable {
    Map<String, Object> mappedResponse =
        Map.of("value", "this is a testing message", "timestamp", System.currentTimeMillis());
    String response = StringHelper.prettyPrint(mappedResponse);
    // Setup the WireMock mapping stub for the test
    wiremock.stubFor(get("/fact").withHeader("Content-Type", containing("json"))
        .willReturn(ok().withHeader("Content-Type", "application/json").withBody(response)));

    DataHolder dataHolder = TestFramework.context().get(DataHolder.class);
    TestApi.rest("undefined-api")
        .setRequestContentType(ContentType.JSON)
        .process(Method.GET, String.format("http://localhost:%s/fact", wiremock.getPort()));
    dataHolder.setResponse("factResponse", TestApi.response().getData().getBody());
    Object fact = dataHolder.getResponse("$['factResponse']").getValue();
    assertThat(fact, notNullValue());
    assertThat(dataHolder.getResponse("$['factResponse']['value']").getValue(), equalTo(mappedResponse.get("value")));
    assertThat(dataHolder.getResponse("$['factResponse']['timestamp']").getValue(),
        equalTo(mappedResponse.get("timestamp")));
  }
}
