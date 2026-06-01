package io.github.ygrip.testara.cucumber.steps;

import io.github.ygrip.testara.core.context.Inject;
import io.github.ygrip.testara.core.context.TestComponent;
import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.core.data.DataHolder;
import io.github.ygrip.testara.core.model.RetryableMethod;
import io.github.ygrip.testara.core.registry.RegistryScope;
import io.github.ygrip.testara.core.transformer.TransformerService;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;

@TestComponent(scope = RegistryScope.TEST)
public class DataManipulationSteps {
  @Inject
  private DataHolder dataHolder;

  @RetryableMethod
  @Given("{actor} {setOrDefine} {word} {setTo} {string}")
  public void defineRequestData(String identifier, String set, String key, String sign, String value) throws Throwable {
    dataHolder.setRequest(key, value);
  }

  @RetryableMethod
  @Given("{actor} {setOrDefine} {word} {setTo}")
  public void defineRequestDataFromMultiline(String identifier, String set, String key, String value) throws Throwable {
    dataHolder.setRequest(key, value);
  }

  @RetryableMethod
  @Given("{actor} {setOrDefine} {word} with")
  public void defineRequestDataFromDatatable(String identifier, String set, String key, DataTable value) throws Throwable {
    dataHolder.setRequest(key, new TransformerService().sourceData(value.cells()));
  }

  @RetryableMethod
  @And("^(.+) (set|define) (\\w+) from \"([^\"]*)\" with$")
  public void defineRequestDataFromTemplate(String identifier, String set, String key, String template, DataTable value)
      throws Throwable {
    dataHolder.setRequest(key, new TransformerService().sourceData(value.cells()).fromTemplate(template));
  }

  @RetryableMethod
  @And("^(.+) (set|define) (\\w+) from \"([^\"]*)\"$")
  public void defineRequestDataFromTemplate(String identifier, String set, String key, String template) throws Throwable {
    dataHolder.setRequest(key, new TransformerService().fromTemplate(template));
  }

  @RetryableMethod
  @Given("{actor} prepare request data {word} with value {string}")
  public void prepareRequestDataStep(String identifier, String key, String value) throws Throwable {
    dataHolder.setRequest(key, value);
  }

  @RetryableMethod
  @Given("{actor} prepare request data {word} with value :")
  public void prepareRequestDataWithMultilineText(String identifier, String key, String value) throws Throwable {
    dataHolder.setRequest(key, value);
  }

  @RetryableMethod
  @Given("{actor} prepare request data {word} with value")
  public void prepareRequestDataStep(String identifier, String key, DataTable value) throws Throwable {
    dataHolder.setRequest(key, new TransformerService().sourceData(value.cells()));
  }

  @RetryableMethod
  @And("^(.+) prepare request data (\\w+) from template \"([^\"]*)\" with value$")
  public void prepareRequestDataFromTemplate(String identifier, String key, String template, DataTable value)
      throws Throwable {
    dataHolder.setRequest(key, new TransformerService().sourceData(value.cells()).fromTemplate(template));
  }

  @RetryableMethod
  @And("^(.+) prepare request data (\\w+) from template \"([^\"]*)\"$")
  public void prepareRequestDataFromTemplate(String identifier, String key, String template) throws Throwable {
    dataHolder.setRequest(key, new TransformerService().fromTemplate(template));
  }

  @Then("{actor} reset {requestOrResponse} data {string}")
  public void resetData(String identifier, String type, String field) throws Throwable {
    switch (type) {
      case "request":
        dataHolder.resetRequestData(field);
        break;
      case "response":
        dataHolder.resetResponseData(field);
        break;
      default:
        break;
    }
  }

  @Then("{actor} reset {requestOrResponse} data")
  public void resetData(String identifier, String type) throws Throwable {
    switch (type) {
      case "request":
        dataHolder.resetRequestsData();
        break;
      case "response":
        dataHolder.resetResponsesData();
        break;
      case "all":
        dataHolder.resetRequestsData();
        dataHolder.resetResponsesData();
        break;
      default:
        break;
    }
  }

  @RetryableMethod
  @Then("{actor} assign response data {word} with value {string}")
  public void assignDataToResponse(String identifier, String key, String value) throws Throwable {
    dataHolder.setResponse(key, TestFramework.context().converter().convert(value));
  }

  @RetryableMethod
  @Then("{actor} assign response data {word} with value :")
  public void assignDataToResponseFromMultiline(String identifier, String key, String value) throws Throwable {
    dataHolder.setResponse(key, TestFramework.context().converter().convert(value));
  }
}
