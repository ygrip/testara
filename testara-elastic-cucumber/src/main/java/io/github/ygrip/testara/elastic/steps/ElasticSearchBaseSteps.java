package io.github.ygrip.testara.elastic.steps;

import com.fasterxml.jackson.core.type.TypeReference;
import io.github.ygrip.testara.core.context.Inject;
import io.github.ygrip.testara.core.context.TestComponent;
import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.core.data.DataHolder;
import io.github.ygrip.testara.core.mapper.MapperHelper;
import io.github.ygrip.testara.core.model.RetryableMethod;
import io.github.ygrip.testara.core.registry.RegistryScope;
import io.github.ygrip.testara.core.support.CommonHelper;
import io.github.ygrip.testara.core.transformer.TransformerService;
import io.github.ygrip.testara.elastic.ElasticSearchHelper;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.xcontent.XContentType;
import org.elasticsearch.search.sort.SortBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * @author yunaz.ramadhan on 4/19/2021
 */
@TestComponent(scope = RegistryScope.TEST)
public class ElasticSearchBaseSteps {
  @Inject
  private ElasticSearchHelper elasticSearchHelper;
  @Inject
  private DataHolder dataHolder;
  private Object response;

  @RetryableMethod
  @Given("{elasticsearch} connect to elastic search with name {word}")
  public void initElasticSearchConnection(String identifier, String serviceName) throws Throwable {
    serviceName = String.valueOf(TestFramework.context().converter().convert(serviceName));
    elasticSearchHelper.init(serviceName);
  }

  @RetryableMethod
  @When("{elasticsearch} get list of indexes")
  public void getIndexes(String identifier) throws Throwable {
    assertThat("No elastic search connection is established", elasticSearchHelper.isConnected(), equalTo(true));

    response = elasticSearchHelper.getIndexes();
  }

  @RetryableMethod
  @When("{elasticsearch} count all document")
  public void countAll(String identifier) throws Throwable {
    assertThat("No elastic search connection is established", elasticSearchHelper.isConnected(), equalTo(true));

    response = elasticSearchHelper.count();
  }

  @RetryableMethod
  @When("{elasticsearch} count matching document with query :")
  public void countMatchingQuery(String identifier, String query) throws Throwable {
    countMatchingInIndexQuery(identifier, null, query);
  }

  @RetryableMethod
  @When("{elasticsearch} count matching document from index {word} with query :")
  public void countMatchingInIndexQuery(String identifier, String index, String query) throws Throwable {
    countMatchingInIndexQuery(identifier, index, null, query);
  }

  @RetryableMethod
  @When("{elasticsearch} count matching document from index {word} with routing {word} with query :")
  public void countMatchingInIndexQuery(String identifier, String index, String routing, String query)
      throws Throwable {
    index = TestFramework.context().converter().convert(index);
    routing = TestFramework.context().converter().convert(routing);
    query = TestFramework.context().converter().convert(query);
    String[] indexes = CommonHelper.isBlank(index) ?
        null :
        Arrays.stream(index.split(",")).filter(entry -> !CommonHelper.isBlank(entry)).distinct().toArray(String[]::new);
    assertThat("No elastic search connection is established", elasticSearchHelper.isConnected(), equalTo(true));

    response = elasticSearchHelper.count(query, indexes, routing, RequestOptions.DEFAULT);
  }

  @When("{elasticsearch} insert to index {string} with data :")
  public void insertDataToIndex(String identifier, String indexName, DataTable table) throws Throwable {
    insertDataToIndexWithType(identifier, indexName, null, table);
  }

  @When("{elasticsearch} insert to index {string} with type {string} and data :")
  public void insertDataToIndexWithType(String identifier, String indexName, String type, DataTable table)
      throws Throwable {
    insertDataToIndex(identifier, indexName, type, null, table);
  }

  @When("{elasticsearch} insert to index {string} with routing {string} and data :")
  public void insertDataToIndexWithRouting(String identifier, String indexName, String routing, DataTable table)
      throws Throwable {
    insertDataToIndex(identifier, indexName, null, routing, table);
  }

  @When("{elasticsearch} insert to index {string} with type {string} with routing {string} and data :")
  public void insertDataToIndex(String identifier, String indexName, String type, String routing, DataTable table)
      throws Throwable {
    assertThat("No elastic search connection is established", elasticSearchHelper.isConnected(), equalTo(true));
    indexName = TestFramework.context().converter().convert(indexName);
    type = TestFramework.context().converter().convert(type);
    routing = TestFramework.context().converter().convert(routing);
    assertThat("index name should not be empty", CommonHelper.isBlank(indexName), equalTo(false));

    LinkedHashMap<String, Object> data = new TransformerService().sourceData(table.cells()).to(new TypeReference<>() {
    });
    assertThat("data should not be empty", CommonHelper.isBlank(data), equalTo(false));

    IndexRequest request = new IndexRequest(indexName);
    request.source(MapperHelper.toString(data), XContentType.JSON);
    if (!CommonHelper.isBlank(type)) {
      request.type(type);
    }
    if (!CommonHelper.isBlank(routing)) {
      request.routing(routing);
    }
    response = elasticSearchHelper.index(request, RequestOptions.DEFAULT);
  }

  @When("{elasticsearch} update document from index {string} with type {string} with id {string} and data :")
  public void updateDataFromIndex(String identifier, String indexName, String type, String id, DataTable table)
      throws Throwable {
    updateDataFromIndex(identifier, indexName, type, id, null, table);
  }

  @When(
      "^(\\[elastic-search\\]) update document from index \"([^\"]*)\" with type \"([^\"]*)\" with id \"([^\"]*)\" with routing \"([^\"]*)\" and data :$")
  public void updateDataFromIndex(String identifier,
      String indexName,
      String type,
      String id,
      String routing,
      DataTable table) throws Throwable {
    assertThat("No elastic search connection is established", elasticSearchHelper.isConnected(), equalTo(true));
    indexName = TestFramework.context().converter().convert(indexName);
    type = TestFramework.context().converter().convert(type);
    routing = TestFramework.context().converter().convert(routing);
    id = TestFramework.context().converter().convert(id);

    LinkedHashMap<String, Object> data = new TransformerService().sourceData(table.cells()).to(new TypeReference<>() {
    });

    if (!CommonHelper.isBlank(id)) {
      UpdateRequest updateRequest = new UpdateRequest(indexName, type, id);
      if (!CommonHelper.isBlank(routing)) {
        updateRequest.routing(routing);
      }
      updateRequest.doc(data);

      response = elasticSearchHelper.update(updateRequest, RequestOptions.DEFAULT);
    }
  }

  @RetryableMethod
  @When("{elasticsearch} get one document from index {string} with id {string}")
  public void getOneDataById(String identifier, String indexName, String id) throws Throwable {
    getOneDataByIdAndType(identifier, indexName, null, id);
  }

  @RetryableMethod
  @When("{elasticsearch} get one document from index {string} with type {string} and id {string}")
  public void getOneDataByIdAndType(String identifier, String indexName, String type, String id) throws Throwable {
    getOneDataById(identifier, indexName, type, null, id);
  }

  @RetryableMethod
  @When("{elasticsearch} get one document from index {string} with routing {string} and id {string}")
  public void getOneDataByIdAndRouting(String identifier, String indexName, String routing, String id)
      throws Throwable {
    getOneDataById(identifier, indexName, null, routing, id);
  }

  @RetryableMethod
  @When("{elasticsearch} get one document from index {string} with type {string} with routing {string} and id {string}")
  public void getOneDataById(String identifier, String indexName, String type, String routing, String id)
      throws Throwable {
    assertThat("No elastic search connection is established", elasticSearchHelper.isConnected(), equalTo(true));
    indexName = TestFramework.context().converter().convert(indexName);
    id = TestFramework.context().converter().convert(id);
    routing = TestFramework.context().converter().convert(routing);
    assertThat("index name should not be empty", CommonHelper.isBlank(indexName), equalTo(false));
    assertThat("id should not be empty", CommonHelper.isBlank(id), equalTo(false));
    if (!CommonHelper.isBlank(type)) {
      type = TestFramework.context().converter().convert(type);
    }
    if (!CommonHelper.isBlank(routing)) {
      routing = TestFramework.context().converter().convert(routing);
    }

    response = elasticSearchHelper.getOne(indexName, type, routing, id);
  }

  @When("{elasticsearch} delete one document from index {string} with id {string}")
  public void deleteOneDataById(String identifier, String indexName, String id) throws Throwable {
    deleteOneDataByIdAndType(identifier, indexName, null, id);
  }

  @When("{elasticsearch} delete one document from index {string} with type {string} and id {string}")
  public void deleteOneDataByIdAndType(String identifier, String indexName, String type, String id) throws Throwable {
    deleteOneDataById(identifier, indexName, type, null, id);
  }

  @When("{elasticsearch} delete one document from index {string} with routing {string} and id {string}")
  public void deleteOneDataByIdAndRouting(String identifier, String indexName, String routing, String id)
      throws Throwable {
    deleteOneDataById(identifier, indexName, null, routing, id);
  }

  @When(
      "^(\\[elastic-search\\]) delete one document from index \"([^\"]*)\" with type \"([^\"]*)\" with routing \"([^\"]*)\" and id \"([^\"]*)\"$")
  public void deleteOneDataById(String identifier, String indexName, String type, String routing, String id)
      throws Throwable {
    assertThat("No elastic search connection is established", elasticSearchHelper.isConnected(), equalTo(true));
    indexName = TestFramework.context().converter().convert(indexName);
    id = TestFramework.context().converter().convert(id);
    routing = TestFramework.context().converter().convert(routing);
    assertThat("index name should not be empty", CommonHelper.isBlank(indexName), equalTo(false));
    assertThat("id should not be empty", CommonHelper.isBlank(id), equalTo(false));
    if (!CommonHelper.isBlank(type)) {
      type = TestFramework.context().converter().convert(type);
    }
    if (!CommonHelper.isBlank(routing)) {
      routing = TestFramework.context().converter().convert(routing);
    }

    response = elasticSearchHelper.deleteOne(indexName, type, routing, id);
  }

  @RetryableMethod
  @When("{elasticsearch} assign data {string} with query :")
  public void searchDataFromElastic(String identifier, String key, DataTable table) throws Throwable {
    searchDataFromElastic(identifier, key, null, table);
  }

  @RetryableMethod
  @When("{elasticsearch} assign data {word} from index {word} with query :")
  public void searchDataFromElastic(String identifier, String key, String index, DataTable table) throws Throwable {
    index = TestFramework.context().converter().convert(index);
    String[] indexes = CommonHelper.isBlank(index) ?
        null :
        Arrays.stream(index.split(",")).filter(entry -> !CommonHelper.isBlank(entry)).distinct().toArray(String[]::new);
    assertThat("No elastic search connection is established", elasticSearchHelper.isConnected(), equalTo(true));
    Map<String, Object> data = new TransformerService().sourceData(table.cells()).to(new TypeReference<>() {
    });

    String query = MapperHelper.toString(data.getOrDefault("luceneQuery", "*"));
    String[] routings = Arrays.stream(data.getOrDefault("routing", "").toString().split(","))
        .filter(entry -> !CommonHelper.isBlank(entry))
        .distinct()
        .toArray(String[]::new);
    String[] types = Arrays.stream(data.getOrDefault("type", "").toString().split(","))
        .filter(entry -> !CommonHelper.isBlank(entry))
        .distinct()
        .toArray(String[]::new);
    Map<String, String> mappedSort = MapperHelper.toObject(data.getOrDefault("sortBy", "{}"), new TypeReference<>() {
    });
    List<SortBuilder<?>> sorts = new ArrayList<>();
    if (CommonHelper.isBlank(mappedSort)) {
      String sortBy = MapperHelper.toString(data.getOrDefault("sortBy", null));
      if (!CommonHelper.isBlank(sortBy)) {
        String[] keywords = sortBy.split(":");
        if (keywords.length == 1) {
          sorts.add(SortBuilders.fieldSort(keywords[0].trim()));
        } else if (keywords.length > 1) {
          sorts.add(SortBuilders.fieldSort(keywords[0].trim()).order(SortOrder.fromString(keywords[1].trim())));
        }
      }
    } else {
      sorts = elasticSearchHelper.parseSortBuilder(mappedSort);
    }
    int from = Integer.parseInt(data.getOrDefault("from", 0).toString());
    int size = Integer.parseInt(data.getOrDefault("size", 1).toString());

    dataHolder.setResponse(key,
        elasticSearchHelper.getSearchDataAs(query,
            indexes,
            types,
            routings,
            from,
            size,
            sorts,
            new TypeReference<LinkedHashMap<String, Object>>() {
            }));
  }

  @RetryableMethod
  @When("{elasticsearch} assign data {string} from index {word} with id {string}")
  public void assignDataFromGetOneDocument(String identifier, String key, String index, String id) throws Throwable {
    assignDataFromGetOneDocumentWithType(identifier, key, index, null, id);
  }

  @RetryableMethod
  @When("{elasticsearch} assign data {word} from index {word} with type {string} and id {string}")
  public void assignDataFromGetOneDocumentWithType(String identifier, String key, String index, String type, String id)
      throws Throwable {
    assignDataFromGetOneDocument(identifier, key, index, type, null, id);
  }

  @RetryableMethod
  @When("{elasticsearch} assign data {word} from index {word} with routing {string} and id {string}")
  public void assignDataFromGetOneDocumentWithRouting(String identifier,
      String key,
      String index,
      String routing,
      String id) throws Throwable {
    assignDataFromGetOneDocument(identifier, key, index, null, routing, id);
  }

  @RetryableMethod
  @When("{elasticsearch} assign data {word} from index {word} with type {string} with routing {string} and id {string}")
  public void assignDataFromGetOneDocument(String identifier,
      String key,
      String index,
      String type,
      String routing,
      String id) throws Throwable {
    index = TestFramework.context().converter().convert(index);
    type = TestFramework.context().converter().convert(type);
    id = TestFramework.context().converter().convert(id);
    routing = TestFramework.context().converter().convert(routing);
    assertThat("No elastic search connection is established", elasticSearchHelper.isConnected(), equalTo(true));

    GetRequest request = null;
    if (!CommonHelper.isBlank(type)) {
      request = new GetRequest(index, type, id);
    } else {
      request = new GetRequest(index);
    }
    if (!CommonHelper.isBlank(routing)) {
      request.routing(routing);
    }

    dataHolder.setResponse(key,
        elasticSearchHelper.getOneAs(request, new TypeReference<LinkedHashMap<String, Object>>() {
        }));
  }

  @RetryableMethod
  @Then("{elasticsearch} assign previous elastic search response to {word}")
  public void assignElasticSearchResponseTo(String identifier, String key) throws Throwable {
    dataHolder.setResponse(key, response);
    response = null;
  }

  @RetryableMethod
  @Then("{elasticsearch} index with name {string} {shouldOrShouldNot} be exists")
  public void insertDataToIndex(String identifier, String indexName, String shouldOrShouldNot) throws Throwable {
    assertThat("No elastic search connection is established", elasticSearchHelper.isConnected(), equalTo(true));
    indexName = TestFramework.context().converter().convert(indexName);

    boolean actual = elasticSearchHelper.doesIndexExists(indexName);
    boolean expected = shouldOrShouldNot.equalsIgnoreCase("should");

    assertThat(String.format("Index with name %s is %s exists", indexName, actual ? "" : "not"),
        actual,
        equalTo(expected));
  }
}
