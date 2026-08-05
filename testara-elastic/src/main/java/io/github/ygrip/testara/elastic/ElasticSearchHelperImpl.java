package io.github.ygrip.testara.elastic;


import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Level;
import co.elastic.clients.elasticsearch._types.SearchType;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.cluster.HealthRequest;
import co.elastic.clients.elasticsearch.cluster.HealthResponse;
import co.elastic.clients.elasticsearch.core.CountRequest;
import co.elastic.clients.elasticsearch.core.CountResponse;
import co.elastic.clients.elasticsearch.core.DeleteRequest;
import co.elastic.clients.elasticsearch.core.DeleteResponse;
import co.elastic.clients.elasticsearch.core.GetRequest;
import co.elastic.clients.elasticsearch.core.GetResponse;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.UpdateRequest;
import co.elastic.clients.elasticsearch.core.UpdateResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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
  private RestClient restClient;
  private ElasticsearchClient client;
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
          this.restClient = builder.build();
          ElasticsearchTransport transport =
              new RestClientTransport(this.restClient, new JacksonJsonpMapper(MapperHelper.getObjectMapper()));
          this.client = new ElasticsearchClient(transport);
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
    if (this.restClient != null) {
      log.debug("#Closing elastic search connection");
      try {
        this.restClient.close();
      } catch (Exception ignored) {
      } finally {
        this.restClient = null;
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
          () -> this.client.ping().value());
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
  public ElasticsearchClient getClient() {
    return this.client;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public IndexResponse index(String indexName, String type, Object source) {
    IndexRequest<JsonData> request = new IndexRequest.Builder<JsonData>().index(indexName)
        .withJson(new StringReader(MapperHelper.toString(source)))
        .build();
    return index(request);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public IndexResponse index(IndexRequest<?> request) {
    reconnect();
    log.debug("#Elastic search indexing document in {}", request.index());
    IndexResponse result = null;
    try {
      result = ThreadExecutor.run(Thread.ofVirtual().name(PREFIX + "-", 0).factory(), () -> this.client.index(request));
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
  @SuppressWarnings("unchecked")
  @Override
  public UpdateResponse<ObjectNode> update(UpdateRequest<?, ?> request) {
    reconnect();
    log.debug("#Elastic search updating document in {}", request.index());
    UpdateResponse<ObjectNode> result = null;
    try {
      UpdateRequest<ObjectNode, Object> typed = (UpdateRequest<ObjectNode, Object>) request;
      result = ThreadExecutor.run(Thread.ofVirtual().name(PREFIX + "-", 0).factory(),
          () -> this.client.update(typed, ObjectNode.class));
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
  public GetResponse<ObjectNode> getOne(String indexName, String type, String id) {
    return getOne(new GetRequest.Builder().index(indexName).id(id).build());
  }

  @Override
  public GetResponse<ObjectNode> getOne(String indexName, String type, String routing, String id) {
    GetRequest.Builder builder = new GetRequest.Builder().index(indexName).id(id);
    if (!CommonHelper.isBlank(routing)) {
      builder.routing(routing);
    }
    return getOne(builder.build());
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public GetResponse<ObjectNode> getOne(GetRequest request) {
    reconnect();
    log.debug("#Elastic search get document with id {} in {}", request.id(), request.index());
    GetResponse<ObjectNode> result = null;
    try {
      result = ThreadExecutor.run(Thread.ofVirtual().name(PREFIX + "-", 0).factory(),
          () -> this.client.get(request, ObjectNode.class));
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
    return parseOneDocumentData(getOne(request), MapperHelper.getGenericType(clazz));
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
    return parseOneDocumentData(getOne(request), MapperHelper.getGenericType(reference));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public DeleteResponse deleteOne(String indexName, String type, String id) {
    return deleteOne(new DeleteRequest.Builder().index(indexName).id(id).build());
  }

  @Override
  public DeleteResponse deleteOne(String indexName, String type, String routing, String id) {
    DeleteRequest.Builder builder = new DeleteRequest.Builder().index(indexName).id(id);
    if (!CommonHelper.isBlank(routing)) {
      builder.routing(routing);
    }
    return deleteOne(builder.build());
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public DeleteResponse deleteOne(DeleteRequest request) {
    reconnect();
    log.debug("#Elastic search delete document with id {} in {}", request.id(), request.index());
    DeleteResponse result = null;
    try {
      result = ThreadExecutor.run(Thread.ofVirtual().name(PREFIX + "-", 0).factory(),
          () -> this.client.delete(request));
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
      HealthRequest request = new HealthRequest.Builder().level(Level.Indices).build();
      HealthResponse response = ThreadExecutor.run(Thread.ofVirtual().name(PREFIX + "-", 0).factory(),
          () -> getClient().cluster().health(request));
      if (response != null && response.indices() != null) {
        result = response.indices().keySet();
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
  public HealthResponse getClusterHealthInfo() {
    reconnect();
    log.debug("#Elastic get cluster health info");
    HealthResponse result = null;
    try {
      result = ThreadExecutor.run(Thread.ofVirtual().name(PREFIX + "-", 0).factory(),
          () -> getClient().cluster().health(h -> h));
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
  public SearchResponse<ObjectNode> search(String luceneQuery,
      String[] indexes,
      int offset,
      int limit,
      List<SortOptions> sorts,
      SearchType type) {
    return search(luceneQuery, indexes, offset, limit, null, sorts, type);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public SearchResponse<ObjectNode> search(String luceneQuery,
      String[] indexes,
      int offset,
      int limit,
      String[] type,
      List<SortOptions> sorts,
      SearchType searchType) {
    return search(luceneQuery, indexes, offset, limit, type, null, sorts, searchType);
  }

  @Override
  public SearchResponse<ObjectNode> search(String luceneQuery,
      String[] indexes,
      int offset,
      int limit,
      String[] types,
      String[] routings,
      List<SortOptions> sorts,
      SearchType searchType) {
    SearchRequest.Builder builder = new SearchRequest.Builder();
    builder.query(q -> q.queryString(qs -> qs.query(luceneQuery)));
    builder.from(Math.max(offset, 0));
    if (limit >= 1) {
      builder.size(limit);
    }
    if (sorts != null) {
      List<SortOptions> filtered = sorts.stream().filter(Objects::nonNull).collect(Collectors.toList());
      if (!filtered.isEmpty()) {
        builder.sort(filtered);
      }
    }
    if (indexes != null && indexes.length > 0) {
      builder.index(Arrays.asList(indexes));
    }
    if (routings != null && routings.length > 0) {
      builder.routing(String.join(",", routings));
    }
    if (searchType != null) {
      builder.searchType(searchType);
    }
    builder.timeout(properties.getTimeout() + "s");
    return search(builder.build());
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public SearchResponse<ObjectNode> search(String luceneQuery,
      String[] indexes,
      int offset,
      int limit,
      List<SortOptions> sorts) {
    return search(luceneQuery, indexes, offset, limit, sorts, null);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public SearchResponse<ObjectNode> search(String luceneQuery, String[] indexes, int limit, List<SortOptions> sorts) {
    return search(luceneQuery, indexes, 0, limit, sorts, null);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public SearchResponse<ObjectNode> search(String luceneQuery,
      String[] indexes,
      List<SortOptions> sorts,
      SearchType type) {
    return search(luceneQuery, indexes, 0, 0, sorts, type);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public SearchResponse<ObjectNode> search(String luceneQuery, String[] indexes, List<SortOptions> sorts) {
    return search(luceneQuery, indexes, sorts, null);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public SearchResponse<ObjectNode> search(SearchRequest request) {
    reconnect();
    log.debug("#Elastic search with query {}", request);
    SearchResponse<ObjectNode> result = null;
    try {
      result = ThreadExecutor.run(Thread.ofVirtual().name(PREFIX + "-", 0).factory(),
          () -> this.client.search(request, ObjectNode.class));
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
  public <T> List<T> parseSearchData(SearchResponse<ObjectNode> response, JavaType type) {
    List<T> result = new ArrayList<>();
    if (!CommonHelper.isBlank(response)) {
      log.debug("#Parse elastic search document to {}", type.getRawClass().getSimpleName());
      List<Hit<ObjectNode>> hits = response.hits().hits();
      for (Hit<ObjectNode> hit : hits) {
        result.add(MapperHelper.toObject(hit.source(), type));
      }
    }
    return result;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public <T> T parseOneDocumentData(GetResponse<ObjectNode> response, JavaType type) {
    if (!CommonHelper.isBlank(response) && response.found()) {
      log.debug("#Parse elastic search document to {}", type.getRawClass().getSimpleName());
      return MapperHelper.toObject(response.source(), type);
    } else {
      return null;
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<SortOptions> parseSortBuilder(Map<String, String> sorts) {
    List<SortOptions> results = new ArrayList<>();
    for (String key : sorts.keySet()) {
      SortOrder order = toSortOrder(sorts.get(key));
      results.add(SortOptions.of(s -> s.field(f -> f.field(key).order(order))));
    }
    return results;
  }

  private SortOrder toSortOrder(String raw) {
    return "desc".equalsIgnoreCase(raw == null ? "" : raw.trim()) ? SortOrder.Desc : SortOrder.Asc;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public <T> List<T> getSearchDataAs(String luceneQuery,
      String[] indexes,
      int offset,
      int limit,
      List<SortOptions> sorts,
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
      List<SortOptions> sorts,
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
      List<SortOptions> sorts,
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
      List<SortOptions> sorts,
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
      List<SortOptions> sorts,
      Class<T> clazz) {
    List<T> result = new ArrayList<>();
    try {
      SearchResponse<ObjectNode> searchResponse = search(luceneQuery, indexes, offset, limit, types, sorts, null);
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
      List<SortOptions> sorts,
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
      List<SortOptions> sorts,
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
      List<SortOptions> sorts,
      TypeReference<T> type) {
    List<T> result = new ArrayList<>();
    try {
      SearchResponse<ObjectNode> searchResponse =
          search(luceneQuery, indexes, offset, limit, types, routings, sorts, null);
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
      List<SortOptions> sorts,
      TypeReference<T> type) {
    return getSearchDataAs(luceneQuery, indexes, types, 0, limit, sorts, type);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public CountResponse count() {
    CountRequest countRequest = new CountRequest.Builder().query(q -> q.matchAll(m -> m)).build();
    return count(countRequest);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public CountResponse count(String luceneQuery, String[] indexes) {
    return count(luceneQuery, indexes, null);
  }

  @Override
  public CountResponse count(String luceneQuery, String[] indexes, String routing) {
    CountRequest.Builder builder = new CountRequest.Builder().query(q -> q.queryString(qs -> qs.query(luceneQuery)));
    if (indexes != null && indexes.length > 0) {
      builder.index(Arrays.asList(indexes));
    }
    if (!CommonHelper.isBlank(routing)) {
      builder.routing(routing);
    }
    return count(builder.build());
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public CountResponse count(CountRequest request) {
    reconnect();
    log.debug("#Elastic search count matching document with query {}", request);
    CountResponse result = null;
    try {
      result = ThreadExecutor.run(Thread.ofVirtual().name(PREFIX + "-", 0).factory(), () -> this.client.count(request));
    } catch (Exception e) {
      log.trace("#Error when count matching document to elastic search, log : ", e);
    } finally {
      close();
    }
    return result;
  }
}
