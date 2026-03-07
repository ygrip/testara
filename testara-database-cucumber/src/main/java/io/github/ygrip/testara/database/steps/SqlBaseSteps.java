package io.github.ygrip.testara.database.steps;

import io.github.ygrip.testara.command.CommandExecutor;
import io.github.ygrip.testara.core.context.Inject;
import io.github.ygrip.testara.core.context.TestComponent;
import io.github.ygrip.testara.core.data.DataHolder;
import io.github.ygrip.testara.core.model.RetryableMethod;
import io.github.ygrip.testara.core.registry.RegistryScope;
import io.github.ygrip.testara.core.support.CommonHelper;
import io.github.ygrip.testara.database.sql.SqlHelper;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;

import java.util.List;
import java.util.Map;

/**
 * @author yunaz.ramadhan on 6/26/2020
 */
@TestComponent(scope = RegistryScope.TEST)
public class SqlBaseSteps {
  @Inject
  private SqlHelper sqlHelper;
  @Inject
  private DataHolder dataHolder;
  private String query;
  private List<Map<String, Object>> result;

  @RetryableMethod
  @Given("^(\\[sql\\]) connect to database with name (\\w+)$")
  public void initSqlDatabase(String identifier, String databaseName) throws Throwable {
    databaseName = CommandExecutor.executeCommand(databaseName);
    sqlHelper.init(databaseName);
  }

  @RetryableMethod
  @Given("^(\\[sql\\]) prepare query with value \"([^\"]*)\"$")
  public void setSqlQuery(String identifier, String value) throws Throwable {
    query = CommandExecutor.executeCommand(value);
  }

  @RetryableMethod
  @Given("^(\\[sql\\]) prepare query with value :$")
  public void setMultilineSqlQuery(String identifier, String value) throws Throwable {
    query = CommandExecutor.executeCommand(value);
  }

  @RetryableMethod
  @When("^(\\[sql\\]) execute database query$")
  public void executeSqlQuery(String identifier) throws Throwable {
    MatcherAssert.assertThat("No sql query is specified", CommonHelper.isBlank(query), CoreMatchers.equalTo(false));
    result = sqlHelper.query(query);
  }

  @RetryableMethod
  @Then("^(\\[sql\\]) assign previous database response to (\\w+)$")
  public void assignSqlDbResponseTo(String identifier, String key) throws Throwable {
    dataHolder.setResponse(key, result);
    result = null;
  }
}
