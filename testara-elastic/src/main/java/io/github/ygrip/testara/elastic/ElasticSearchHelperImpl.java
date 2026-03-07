package io.github.ygrip.testara.elastic;


import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import io.github.ygrip.testara.core.concurrency.ThreadExecutor;
import io.github.ygrip.testara.core.context.TestComponent;
import io.github.ygrip.testara.core.mapper.MapperHelper;
import io.github.ygrip.testara.core.registry.RegistryScope;
import io.github.ygrip.testara.core.support.CommonHelper;
import io.github.ygrip.testara.elastic.config.ElasticSearchProperties;
import io.github.ygrip.testara.elastic.model.ElasticSearchModel;
import lombok.extern.log4j.Log4j2;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.core.CountRequest;
import org.elasticsearch.client.core.CountResponse;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * <p>ElasticSearchHelperImpl class.</p>
 *
 * @author yunaz.ramadhan on 4/19/2021
 * @version $Id: $Id
 */
@Log4j2
@TestComponent(scope = RegistryScope.TEST)
public class ElasticSearchHelperImpl implements ElasticSearchHelper {
  private static final String PREFIX = "elastic-search";
  private final ElasticSearchProperties properties;
  private RestHighLevelClient client;
  private String currentServiceName;

  /**
   * <p>Constructor for ElasticSearchHelperImpl.</p>
   *
   * @param properties a {@link io.github.ygrip.testara.elastic.config.ElasticSearchProperties} object.
   */
  public ElasticSearchHelperImpl(ElasticSearchProperties properties) {
    this.properties = properties;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ElasticSearchHelper init(String service) throws Exception {
    if (!CommonHelper.isBlank(service)) {
      log.info("#Establishing connection to {} elastic search", service);
      ElasticSearchModel props = properties.getService().getOrDefault(service, null);
      if (!CommonHelper.isBlank(props)) {
        if (!props.getHosts().isEmpty()) {
          HttpHost[] hosts = parseHost(props.getHosts(), props.isSecured());
          CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
          credentialsProvider.setCredentials(AuthScope.ANY,
              new UsernamePasswordCredentials(props.getUsername(), props.getPassword()));
          RestClientBuilder builder = RestClient.builder(hosts);
          if (props.isRequireAuthentication()) {
            builder.setHttpClientConfigCallback(h -> h.setDefaultCredentialsProvider(credentialsProvider));
          }
          this.client = new RestHighLevelClient(builder);
          this.currentServiceName = service;
        } else {
          throw new Exception("No elastic search host is specified");
        }
      } else {
        throw new Exception("No elastic search properties is specified");
      }
    } else {
      throw new Exception("No elastic search service name is specified");
    }
    return this;
  }

  private HttpHost[] parseHost(List<String> hostInString, Boolean isSecure) {
    HttpHost[] hosts = new HttpHost[hostInString.size()];
    for (int i = 0; i < hostInString.size(); i++) {
      String[] meta = hostInString.get(i).split(":");
      hosts[i] = new HttpHost(meta[0], Integer.parseInt(meta[1]), isSecure ? "https" : "http");
    }
    return hosts;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void close() {
    if (this.client != null) {
      log.debug("#Closing elastic search connection");
      try {
        this.client.close();
      } catch (Exception ignored) {
      } finally {
        this.client = null;
      }
    } else {
      log.debug("#No active elastic search connection established, action skipped");
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isConnected() {
    if (this.client == null) {
      return false;
    }
    try {
      return ThreadExecutor.run(Thread.ofVirtual().name(PREFIX + "-", 0).factory(),
          () -> this.client.ping(RequestOptions.DEFAULT));
    } catch (Exception e) {
      return false;
    }
  }

  private void reconnect() {
    if (!isConnected()) {
      try {
        init(this.currentServiceName);
      } catch (Exception ignored) {

      }
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public RestHighLevelClient getClient() {
    return this.client;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public IndexResponse index(String indexName, String type, Object source) {
    IndexRequest request = new IndexRequest(indexName);
    request.source(MapperHelper.toString(source), XContentType.JSON);
    if (!CommonHelper.isBlank(type)) {
      request.type(type);
    }
    return index(request, RequestOptions.DEFAULT);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public IndexResponse index(IndexRequest request, RequestOptions options) {
    reconnect();
    log.debug("#Elastic search indexing document {} in {}", request.sourceAsMap(), request.index());
    IndexResponse result = null;
    try {
      result = ThreadExecutor.run(Thread.ofVirtual().name(PREFIX + "-", 0).factory(),
          () -> this.client.index(request, options));
    } catch (Exception e) {
      log.trace("#Error when index document to elastic search, log : ", e);
    } finally {
      close();
    }
    return result;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public UpdateResponse update(UpdateRequest request, RequestOptions options) {
    reconnect();
    log.debug("#Elastic search updating document {} in {}", request.getDescription(), request.index());
    UpdateResponse result = null;
    try {
      result = ThreadExecutor.run(Thread.ofVirtual().name(PREFIX + "-", 0).factory(),
          () -> this.client.update(request, options));
    } catch (Exception e) {
      log.trace("#Error when update document to elastic search, log : ", e);
    } finally {
      close();
    }
    return result;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public GetResponse getOne(String indexName, String type, String id) {
    return CommonHelper.isBlank(type) ?
        getOne(new GetRequest(indexName).id(id), RequestOptions.DEFAULT) :
        getOne(new GetRequest(indexName, type, id), RequestOptions.DEFAULT);
  }

  @Override
  public GetResponse getOne(String indexName, String type, String routing, String id) {
    GetRequest request = null;
    if (!CommonHelper.isBlank(type)) {
      request = new GetRequest(indexName, type, id);
    } else {
      request = new GetRequest(indexName);
    }
    if (!CommonHelper.isBlank(routing)) {
      request.routing(routing);
    }
    return getOne(request, RequestOptions.DEFAULT);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public GetResponse getOne(GetRequest request, RequestOptions options) {
    reconnect();
    log.debug("#Elastic search get document with id {} in {}", request.id(), request.index());
    GetResponse result = null;
    try {
      result = ThreadExecutor.run(Thread.ofVirtual().name(PREFIX + "-", 0).factory(),
          () -> this.client.get(request, options));
    } catch (Exception e) {
      log.trace("#Error when get document to elastic search, log : ", e);
    } finally {
      close();
    }
    return result;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public <T> T getOneAs(String indexName, String type, String id, Class<T> clazz) {
    return parseOneDocumentData(getOne(indexName, type, id), MapperHelper.getGenericType(clazz));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public <T> T getOneAs(GetRequest request, Class<T> clazz) {
    return parseOneDocumentData(getOne(request, RequestOptions.DEFAULT), MapperHelper.getGenericType(clazz));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public <T> T getOneAs(String indexName, String type, String id, TypeReference<T> reference) {
    return parseOneDocumentData(getOne(indexName, type, id), MapperHelper.getGenericType(reference));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public <T> T getOneAs(GetRequest request, TypeReference<T> reference) {
    return parseOneDocumentData(getOne(request, RequestOptions.DEFAULT), MapperHelper.getGenericType(reference));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public DeleteResponse deleteOne(String indexName, String type, String id) {
    return CommonHelper.isBlank(type) ?
        deleteOne(new DeleteRequest(indexName).id(id), RequestOptions.DEFAULT) :
        deleteOne(new DeleteRequest(indexName, type, id), RequestOptions.DEFAULT);
  }

  @Override
  public DeleteResponse deleteOne(String indexName, String type, String routing, String id) {
    DeleteRequest request = null;
    if (!CommonHelper.isBlank(type)) {
      request = new DeleteRequest(indexName, type, id);
    } else {
      request = new DeleteRequest(indexName);
    }
    if (!CommonHelper.isBlank(routing)) {
      request.routing(routing);
    }
    return deleteOne(request, RequestOptions.DEFAULT);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public DeleteResponse deleteOne(DeleteRequest request, RequestOptions options) {
    reconnect();
    log.debug("#Elastic search delete document with id {} in {}", request.id(), request.index());
    DeleteResponse result = null;
    try {
      result = ThreadExecutor.run(Thread.ofVirtual().name(PREFIX + "-", 0).factory(),
          () -> this.client.delete(request, options));
    } catch (Exception e) {
      log.trace("#Error when delete document to elastic search, log : ", e);
    } finally {
      close();
    }
    return result;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean doesIndexExists(String indexName) {
    reconnect();
    log.debug("#Elastic search check index {}", indexName);
    ClusterHealthRequest request = new ClusterHealthRequest();

    boolean result = false;
    try {
      result = getIndexes().contains(indexName);
    } catch (Exception e) {
      log.trace("#Error when check index to elastic search, log : ", e);
    } finally {
      close();
    }
    return result;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Set<String> getIndexes() {
    reconnect();
    Set<String> result = new HashSet<>();
    log.debug("#Elastic get index list");
    try {
      // Use indices level to get the indices map populated
      ClusterHealthRequest request = new ClusterHealthRequest();
      request.level(ClusterHealthRequest.Level.INDICES);
      ClusterHealthResponse response = ThreadExecutor.run(Thread.ofVirtual().name(PREFIX + "-", 0).factory(),
          () -> getClient().cluster().health(request, RequestOptions.DEFAULT));
      if (response != null && response.getIndices() != null) {
        result = response.getIndices().keySet();
      }
    } catch (Exception e) {
      log.trace("#Error when get index list to elastic search, log : ", e);
    } finally {
      close();
    }
    return result;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ClusterHealthResponse getClusterHealthInfo() {
    reconnect();
    log.debug("#Elastic get cluster health info");
    ClusterHealthRequest request = new ClusterHealthRequest();
    ClusterHealthResponse result = null;
    try {
      result = ThreadExecutor.run(Thread.ofVirtual().name(PREFIX + "-", 0).factory(),
          () -> getClient().cluster().health(request, RequestOptions.DEFAULT));
    } catch (Exception e) {
      log.trace("#Error when get cluster health info to elastic search, log : ", e);
    } finally {
      close();
    }
    return result;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public SearchResponse search(String luceneQuery,
      String[] indexes,
      int offset,
      int limit,
      List<SortBuilder<?>> sorts,
      SearchType type,
      RequestOptions options) {
    return search(luceneQuery, indexes, offset, limit, null, sorts, type, options);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public SearchResponse search(String luceneQuery,
      String[] indexes,
      int offset,
      int limit,
      String[] type,
      List<SortBuilder<?>> sorts,
      SearchType searchType,
      RequestOptions options) {
    return search(luceneQuery, indexes, offset, limit, type, null, sorts, searchType, options);
  }

  @Override
  public SearchResponse search(String luceneQuery,
      String[] indexes,
      int offset,
      int limit,
      String[] types,
      String[] routings,
      List<SortBuilder<?>> sorts,
      SearchType searchType,
      RequestOptions options) {
    SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
    sourceBuilder.query(QueryBuilders.queryStringQuery(luceneQuery));
    sourceBuilder.from(Math.max(offset, 0));
    if (limit >= 1) {
      sourceBuilder.size(limit);
    }
    sourceBuilder.timeout(new TimeValue(properties.getTimeout(), TimeUnit.SECONDS));
    SearchRequest searchRequest = new SearchRequest();
    if (sorts != null) {
      for (SortBuilder<?> sort : sorts) {
        if (sort != null) {
          sourceBuilder.sort(sort);
        }
      }
    }
    if (indexes != null && indexes.length > 0) {
      searchRequest.indices(indexes);
    }
    if (types != null && types.length > 0) {
      searchRequest.types(types);
    }
    if (routings != null && routings.length > 0) {
      searchRequest.routing(routings);
    }
    searchRequest.searchType(searchType);
    searchRequest.source(sourceBuilder);
    return search(searchRequest, options);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public SearchResponse search(String luceneQuery,
      String[] indexes,
      int offset,
      int limit,
      List<SortBuilder<?>> sorts,
      SearchType type) {
    return search(luceneQuery, indexes, offset, limit, sorts, type, RequestOptions.DEFAULT);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public SearchResponse search(String luceneQuery,
      String[] indexes,
      int offset,
      int limit,
      List<SortBuilder<?>> sorts) {
    return search(luceneQuery, indexes, offset, limit, sorts, SearchType.DEFAULT);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public SearchResponse search(String luceneQuery, String[] indexes, int limit, List<SortBuilder<?>> sorts) {
    return search(luceneQuery, indexes, 0, limit, sorts, SearchType.DEFAULT);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public SearchResponse search(String luceneQuery,
      String[] indexes,
      List<SortBuilder<?>> sorts,
      SearchType type,
      RequestOptions options) {
    return search(luceneQuery, indexes, 0, 0, sorts, SearchType.DEFAULT);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public SearchResponse search(String luceneQuery,
      String[] indexes,
      List<SortBuilder<?>> sorts,
      RequestOptions options) {
    return search(luceneQuery, indexes, sorts, SearchType.DEFAULT, options);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public SearchResponse search(String luceneQuery, String[] indexes, List<SortBuilder<?>> sorts) {
    return search(luceneQuery, indexes, sorts, SearchType.DEFAULT, RequestOptions.DEFAULT);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public SearchResponse search(SearchRequest request, RequestOptions options) {
    reconnect();
    log.debug("#Elastic search with query {}", request);
    SearchResponse result = null;
    try {
      result = ThreadExecutor.run(Thread.ofVirtual().name(PREFIX + "-", 0).factory(),
          () -> this.client.search(request, options));
    } catch (Exception e) {
      log.trace("#Error when search document to elastic search, log : ", e);
    } finally {
      close();
    }
    return result;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public SearchResponse search(SearchRequest request) {
    return search(request, RequestOptions.DEFAULT);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public <T> List<T> parseSearchData(SearchResponse response, JavaType type) {
    List<T> result = new ArrayList<>();
    if (!CommonHelper.isBlank(response)) {
      log.debug("#Parse elastic search document to {}", type.getRawClass().getSimpleName());
      SearchHit[] searchHits = response.getHits().getHits();
      for (SearchHit hit : searchHits) {
        result.add(MapperHelper.toObject(hit.getSourceAsString(), type));
      }
    }
    return result;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public <T> T parseOneDocumentData(GetResponse response, JavaType type) {
    if (!CommonHelper.isBlank(response)) {
      log.debug("#Parse elastic search document to {}", type.getRawClass().getSimpleName());
      return MapperHelper.toObject(response.getSourceAsString(), type);
    } else {
      return null;
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<SortBuilder<?>> parseSortBuilder(Map<String, String> sorts) {
    List<SortBuilder<?>> results = new ArrayList<>();
    for (String key : sorts.keySet()) {
      SortBuilder<?> sort = null;
      try {
        sort = SortBuilders.fieldSort(key).order(SortOrder.fromString(sorts.getOrDefault(key, "ASC").trim()));
      } catch (Exception ignored) {
        sort = SortBuilders.fieldSort(key);
      } finally {
        results.add(sort);
      }
    }
    return results;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public <T> List<T> getSearchDataAs(String luceneQuery,
      String[] indexes,
      int offset,
      int limit,
      List<SortBuilder<?>> sorts,
      Class<T> clazz) {
    return getSearchDataAs(luceneQuery, indexes, null, offset, limit, sorts, clazz);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public <T> List<T> getSearchDataAs(String luceneQuery,
      String[] indexes,
      int limit,
      List<SortBuilder<?>> sorts,
      Class<T> clazz) {
    return getSearchDataAs(luceneQuery, indexes, null, 0, limit, sorts, clazz);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public <T> List<T> getSearchDataAs(String luceneQuery,
      String[] indexes,
      int offset,
      int limit,
      List<SortBuilder<?>> sorts,
      TypeReference<T> type) {
    return getSearchDataAs(luceneQuery, indexes, null, offset, limit, sorts, type);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public <T> List<T> getSearchDataAs(String luceneQuery,
      String[] indexes,
      int limit,
      List<SortBuilder<?>> sorts,
      TypeReference<T> type) {
    return getSearchDataAs(luceneQuery, indexes, null, 0, limit, sorts, type);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public <T> List<T> getSearchDataAs(String luceneQuery,
      String[] indexes,
      String[] types,
      int offset,
      int limit,
      List<SortBuilder<?>> sorts,
      Class<T> clazz) {
    List<T> result = new ArrayList<>();
    try {
      SearchResponse searchResponse =
          search(luceneQuery, indexes, offset, limit, types, sorts, SearchType.DEFAULT, RequestOptions.DEFAULT);
      result = parseSearchData(searchResponse, MapperHelper.getGenericType(clazz));
    } catch (Exception ignored) {

    }

    return result;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public <T> List<T> getSearchDataAs(String luceneQuery,
      String[] indexes,
      String[] types,
      int limit,
      List<SortBuilder<?>> sorts,
      Class<T> clazz) {
    return getSearchDataAs(luceneQuery, indexes, types, 0, limit, sorts, clazz);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public <T> List<T> getSearchDataAs(String luceneQuery,
      String[] indexes,
      String[] types,
      int offset,
      int limit,
      List<SortBuilder<?>> sorts,
      TypeReference<T> type) {
    return getSearchDataAs(luceneQuery, indexes, types, null, offset, limit, sorts, type);
  }

  @Override
  public <T> List<T> getSearchDataAs(String luceneQuery,
      String[] indexes,
      String[] types,
      String[] routings,
      int offset,
      int limit,
      List<SortBuilder<?>> sorts,
      TypeReference<T> type) {
    List<T> result = new ArrayList<>();
    try {
      SearchResponse searchResponse = search(luceneQuery,
          indexes,
          offset,
          limit,
          types,
          routings,
          sorts,
          SearchType.DEFAULT,
          RequestOptions.DEFAULT);
      result = parseSearchData(searchResponse, MapperHelper.getGenericType(type));
    } catch (Exception ignored) {

    }

    return result;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public <T> List<T> getSearchDataAs(String luceneQuery,
      String[] indexes,
      String[] types,
      int limit,
      List<SortBuilder<?>> sorts,
      TypeReference<T> type) {
    return getSearchDataAs(luceneQuery, indexes, types, 0, limit, sorts, type);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public CountResponse count() {
    CountRequest countRequest = new CountRequest();
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
    searchSourceBuilder.query(QueryBuilders.matchAllQuery());
    countRequest.source(searchSourceBuilder);
    return count(countRequest, RequestOptions.DEFAULT);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public CountResponse count(String luceneQuery, String[] indexes, RequestOptions options) {
    return count(luceneQuery, indexes, null, options);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public CountResponse count(String luceneQuery, String[] indexes) {
    return count(luceneQuery, indexes, RequestOptions.DEFAULT);
  }

  @Override
  public CountResponse count(String luceneQuery, String[] indexes, String routing, RequestOptions options) {
    CountRequest countRequest = new CountRequest();
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
    searchSourceBuilder.query(QueryBuilders.queryStringQuery(luceneQuery));
    countRequest.source(searchSourceBuilder);
    if (indexes != null) {
      countRequest.indices(indexes);
    }
    if (routing != null && !routing.isEmpty()) {
      if (!routing.trim().isEmpty()) {
        countRequest.routing(routing);
      }
    }
    return count(countRequest, options);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public CountResponse count(CountRequest request, RequestOptions options) {
    reconnect();
    log.debug("#Elastic search count matching document with query {}", request);
    CountResponse result = null;
    try {
      result = ThreadExecutor.run(Thread.ofVirtual().name(PREFIX + "-", 0).factory(),
          () -> this.client.count(request, options));
    } catch (Exception e) {
      log.trace("#Error when count matching document to elastic search, log : ", e);
    } finally {
      close();
    }
    return result;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public CountResponse count(CountRequest request) {
    return count(request, RequestOptions.DEFAULT);
  }
}
