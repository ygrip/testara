package io.github.ygrip.testara.database.steps;

import com.fasterxml.jackson.core.type.TypeReference;
import io.github.ygrip.testara.command.CommandExecutor;
import io.github.ygrip.testara.core.context.Inject;
import io.github.ygrip.testara.core.context.TestComponent;
import io.github.ygrip.testara.core.data.DataHolder;
import io.github.ygrip.testara.core.mapper.MapperHelper;
import io.github.ygrip.testara.core.model.RetryableMethod;
import io.github.ygrip.testara.core.registry.RegistryScope;
import io.github.ygrip.testara.core.transformer.TransformerService;
import io.github.ygrip.testara.database.nosql.MongoHelper;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.Map;

/**
 * @author yunaz.ramadhan on 6/26/2020
 */
@TestComponent(scope = RegistryScope.TEST)
public class MongoBaseSteps {
  @Inject
  private MongoHelper mongoHelper;
  @Inject
  private DataHolder dataHolder;
  private Object dbResponse;

  @RetryableMethod
  @Given("{mongo} connect to database with name {word}")
  public void initMongoDatabase(String identifier, String databaseName) throws Throwable {
    databaseName = CommandExecutor.executeCommand(databaseName);
    mongoHelper.init(databaseName);
  }

  @RetryableMethod
  @Given("{mongo} select collection with name {word}")
  public void selectMongoCollection(String identifier, String collectionName) throws Throwable {
    collectionName = CommandExecutor.executeCommand(collectionName);
    mongoHelper.selectCollection(collectionName);
  }

  @RetryableMethod
  @When("{mongo} select data with query :")
  public void executeMongoDbQuery(String identifier, DataTable table) throws Throwable {
    Map<String, Object> data =
        new TransformerService().sourceData(table.cells()).to(new TypeReference<Map<String, Object>>() {
        });
    String query = MapperHelper.toString(data.getOrDefault("query", "{}"));
    String sort = MapperHelper.toString(data.getOrDefault("sort", "{}"));
    String project = MapperHelper.toString(data.getOrDefault("project", "{}"));
    int limit = Integer.parseInt(data.getOrDefault("limit", 0).toString());
    int skip = Integer.parseInt(data.getOrDefault("skip", 0).toString());
    dbResponse = mongoHelper.rawQuery(query, sort, project, limit, skip);
  }

  @When("{mongo} delete data with query :")
  public void executeMongoDeleteData(String identifier, DataTable table) throws Throwable {
    Map<String, Object> data =
        new TransformerService().sourceData(table.cells()).to(new TypeReference<Map<String, Object>>() {
        });

    String query = MapperHelper.toString(data.getOrDefault("query", "{}"));
    String sort = MapperHelper.toString(data.getOrDefault("sort", "{}"));
    boolean useMany = Boolean.parseBoolean(String.valueOf(data.getOrDefault("useMany", false)));
    dbResponse = mongoHelper.delete(query, sort, useMany);
  }

  @RetryableMethod
  @When("{mongo} count data with query :")
  public void countMongoQueryResult(String identifier, DataTable table) throws Throwable {
    Map<String, Object> data =
        new TransformerService().sourceData(table.cells()).to(new TypeReference<Map<String, Object>>() {
        });

    String query = MapperHelper.toString(data.getOrDefault("query", "{}"));
    dbResponse = mongoHelper.count(query);
  }

  @RetryableMethod
  @When("{mongo} aggregate mongo data with query :")
  public void countMongoQueryResult(String identifier, String query) throws Throwable {
    query = CommandExecutor.executeCommand(query);

    dbResponse = mongoHelper.aggregate(query);
  }

  @When("{mongo} update mongo data with query :")
  public void updateMongoQueryResult(String identifier, DataTable table) throws Throwable {
    Map<String, Object> data =
        new TransformerService().sourceData(table.cells()).to(new TypeReference<Map<String, Object>>() {
        });

    String query = MapperHelper.toString(data.getOrDefault("query", "{}"));
    String update = MapperHelper.toString(data.getOrDefault("update", "{}"));
    boolean useMany = Boolean.parseBoolean(String.valueOf(data.getOrDefault("useMany", false)));
    dbResponse = mongoHelper.update(query, update, useMany);
  }

  @RetryableMethod
  @When("{mongo} get mongo indexes from current collection")
  public void getMongoIdexes(String identifier) throws Throwable {
    dbResponse = mongoHelper.getIndexes();
  }

  @RetryableMethod
  @Then("{mongo} assign previous database response to {word}")
  public void assignMongoDbResponseTo(String identifier, String key) throws Throwable {
    dataHolder.setResponse(key, dbResponse);
    dbResponse = null;
  }

  @RetryableMethod
  @When("{mongo} select distinct field with query :")
  public void executeDistinctQuery(String identifier, DataTable table) throws Throwable {
    Map<String, Object> data =
        new TransformerService().sourceData(table.cells()).to(new TypeReference<Map<String, Object>>() {
        });
    String field = MapperHelper.toString(data.getOrDefault("field", ""));
    String query = MapperHelper.toString(data.getOrDefault("query", "{}"));
    dbResponse = mongoHelper.distinct(field, query);
  }
}
