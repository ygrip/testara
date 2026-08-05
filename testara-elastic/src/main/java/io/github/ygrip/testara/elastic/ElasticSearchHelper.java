package io.github.ygrip.testara.elastic;


import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SearchType;
import co.elastic.clients.elasticsearch._types.SortOptions;
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
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * <p>ElasticSearchHelper interface.</p>
 *
 * @author yunaz.ramadhan on 4/19/2021
 * @version $Id: $Id
 */
public interface ElasticSearchHelper {
  /**
   * Initialize the elastic search helper to construct the user specified elastic connection
   *
   * @param service is String of the elastic search service defined in properties
   * @return instance of ElasticSearchHelper
   * @throws Exception if any.
   */
  ElasticSearchHelper init(String service) throws Exception;

  /**
   * Method to close elastic search connection
   */
  void close();

  /**
   * Method to check whether elastic search connection has been established
   *
   * @return boolean
   */
  boolean isConnected();

  /**
   * Method to get current elastic search client connection
   *
   * @return ElasticsearchClient
   */
  ElasticsearchClient getClient();

  /**
   * Method to index document to elastic search
   *
   * @param indexName is String of the elastic search index to be checked
   * @param type      is unused (Elasticsearch mapping types were removed in ES7+); kept for source compatibility
   * @param source    is object of document that want to be indexed in elastic search
   * @return IndexResponse
   */
  IndexResponse index(String indexName, String type, Object source);

  /**
   * Method to index document to elastic search
   *
   * @param request is index request data from the elastic search
   * @return IndexResponse
   */
  IndexResponse index(IndexRequest<?> request);

  /**
   * Method to update document to elastic search
   *
   * @param request is update request data from the elastic search
   * @return UpdateResponse
   */
  UpdateResponse<ObjectNode> update(UpdateRequest<?, ?> request);

  /**
   * Method to get document by id from elastic search
   *
   * @param indexName is String of the elastic search index to be checked
   * @param type      is unused (Elasticsearch mapping types were removed in ES7+); kept for source compatibility
   * @param id        is id of the elastic document index to be checked
   * @return GetResponse
   */
  GetResponse<ObjectNode> getOne(String indexName, String type, String id);

  /**
   * Method to get document by id from elastic search
   *
   * @param indexName is String of the elastic search index to be checked
   * @param type      is unused (Elasticsearch mapping types were removed in ES7+); kept for source compatibility
   * @param routing   is String of the elastic search routing id to be checked
   * @param id        is id of the elastic document index to be checked
   * @return GetResponse
   */
  GetResponse<ObjectNode> getOne(String indexName, String type, String routing, String id);

  /**
   * Method to get document by id from elastic search
   *
   * @param request is get request data from the elastic search
   * @return GetResponse
   */
  GetResponse<ObjectNode> getOne(GetRequest request);

  /**
   * Method to get document by id from elastic search
   *
   * @param indexName is String of the elastic search index to be checked
   * @param type      is unused (Elasticsearch mapping types were removed in ES7+); kept for source compatibility
   * @param id        is id of the elastic document index to be checked
   * @param clazz     is the data type user desired as output
   * @return T object typce
   * @param <T> a T object.
   */
  <T> T getOneAs(String indexName, String type, String id, Class<T> clazz);

  /**
   * Method to get document by id from elastic search
   *
   * @param request is get request data from the elastic search
   * @param clazz   is the data type user desired as output
   * @return GetResponse
   * @param <T> a T object.
   */
  <T> T getOneAs(GetRequest request, Class<T> clazz);

  /**
   * Method to get document by id from elastic search
   *
   * @param indexName is String of the elastic search index to be checked
   * @param type      is unused (Elasticsearch mapping types were removed in ES7+); kept for source compatibility
   * @param id        is id of the elastic document index to be checked
   * @param reference is the data type user desired as output
   * @return T object typce
   * @param <T> a T object.
   */
  <T> T getOneAs(String indexName, String type, String id, TypeReference<T> reference);

  /**
   * Method to get document by id from elastic search
   *
   * @param request   is get request data from the elastic search
   * @param reference is the data type user desired as output
   * @return GetResponse
   * @param <T> a T object.
   */
  <T> T getOneAs(GetRequest request, TypeReference<T> reference);

  /**
   * Method to delete document by id from elastic search
   *
   * @param indexName is String of the elastic search index to be checked
   * @param type      is unused (Elasticsearch mapping types were removed in ES7+); kept for source compatibility
   * @param id        is id of the elastic document index to be checked
   * @return GetResponse
   */
  DeleteResponse deleteOne(String indexName, String type, String id);

  /**
   * Method to delete document by id from elastic search
   *
   * @param indexName is String of the elastic search index to be checked
   * @param type      is unused (Elasticsearch mapping types were removed in ES7+); kept for source compatibility
   * @param routing   is String of the elastic search routing id to be checked
   * @param id        is id of the elastic document index to be checked
   * @return GetResponse
   */
  DeleteResponse deleteOne(String indexName, String type, String routing, String id);

  /**
   * Method to delete document by id from elastic search
   *
   * @param request is delete request data from the elastic search
   * @return GetResponse
   */
  DeleteResponse deleteOne(DeleteRequest request);

  /**
   * Method to check whether elastic search index is exists
   *
   * @param indexName is String of the elastic search index to be checked
   * @return boolean
   */
  boolean doesIndexExists(String indexName);

  /**
   * Method to get list of elastic search indexes
   *
   * @return Set of string
   */
  Set<String> getIndexes();

  /**
   * Method to check cluster health info a an elastic search system
   *
   * @return HealthResponse
   */
  HealthResponse getClusterHealthInfo();

  /**
   * Method to search documents from elastic search
   *
   * @param luceneQuery is lucene query to be passed to elastic search
   * @param indexes     is list of index to check, if empty then will search all indexes
   * @param offset      is number of document to skip
   * @param limit       is number of document to fetch
   * @param sorts       list of sort rule
   * @param type        is the elastic search type
   * @return SearchResponse
   */
  SearchResponse<ObjectNode> search(String luceneQuery,
      String[] indexes,
      int offset,
      int limit,
      List<SortOptions> sorts,
      SearchType type);

  /**
   * Method to search documents from elastic search
   *
   * @param luceneQuery is lucene query to be passed to elastic search
   * @param indexes     is list of index to check, if empty then will search all indexes
   * @param offset      is number of document to skip
   * @param limit       is number of document to fetch
   * @param types       is unused (Elasticsearch mapping types were removed in ES7+); kept for source compatibility
   * @param sorts       list of sort rule
   * @param searchType  is the elastic search type
   * @return SearchResponse
   */
  SearchResponse<ObjectNode> search(String luceneQuery,
      String[] indexes,
      int offset,
      int limit,
      String[] types,
      List<SortOptions> sorts,
      SearchType searchType);

  /**
   * Method to search documents from elastic search
   *
   * @param luceneQuery is lucene query to be passed to elastic search
   * @param indexes     is list of index to check, if empty then will search all indexes
   * @param offset      is number of document to skip
   * @param limit       is number of document to fetch
   * @param types       is unused (Elasticsearch mapping types were removed in ES7+); kept for source compatibility
   * @param routings    is list of routing id of elastic search
   * @param sorts       list of sort rule
   * @param searchType  is the elastic search type
   * @return SearchResponse
   */
  SearchResponse<ObjectNode> search(String luceneQuery,
      String[] indexes,
      int offset,
      int limit,
      String[] types,
      String[] routings,
      List<SortOptions> sorts,
      SearchType searchType);

  /**
   * Method to search documents from elastic search
   *
   * @param luceneQuery is lucene query to be passed to elastic search
   * @param indexes     is list of index to check, if empty then will search all indexes
   * @param offset      is number of document to skip
   * @param limit       is number of document to fetch
   * @param sorts       list of sort rule
   * @return SearchResponse
   */
  SearchResponse<ObjectNode> search(String luceneQuery,
      String[] indexes,
      int offset,
      int limit,
      List<SortOptions> sorts);

  /**
   * Method to search documents from elastic search
   *
   * @param luceneQuery is lucene query to be passed to elastic search
   * @param indexes     is list of index to check, if empty then will search all indexes
   * @param limit       is number of document to fetch
   * @param sorts       list of sort rule
   * @return SearchResponse
   */
  SearchResponse<ObjectNode> search(String luceneQuery, String[] indexes, int limit, List<SortOptions> sorts);

  /**
   * Method to search documents from elastic search
   *
   * @param luceneQuery is lucene query to be passed to elastic search
   * @param indexes     is list of index to check, if empty then will search all indexes
   * @param sorts       list of sort rule
   * @param type        is the elastic search type
   * @return SearchResponse
   */
  SearchResponse<ObjectNode> search(String luceneQuery, String[] indexes, List<SortOptions> sorts, SearchType type);

  /**
   * Method to search documents from elastic search
   *
   * @param luceneQuery is lucene query to be passed to elastic search
   * @param indexes     is list of index to check, if empty then will search all indexes
   * @param sorts       list of sort rule
   * @return SearchResponse
   */
  SearchResponse<ObjectNode> search(String luceneQuery, String[] indexes, List<SortOptions> sorts);

  /**
   * Method to search documents from elastic search
   *
   * @param request is the elastic search request
   * @return SearchResponse
   */
  SearchResponse<ObjectNode> search(SearchRequest request);

  /**
   * Method to get document from elastic search and parse it into specified data type
   *
   * @param luceneQuery is lucene query to be passed to elastic search
   * @param indexes     is list of index to check, if empty then will search all indexes
   * @param offset      is the number of document to be skipped
   * @param limit       is the number of limit document to be fetched
   * @param sorts       list of sort rule
   * @param clazz       java class or type reference
   * @return List of type
   * @param <T> a T object.
   */
  <T> List<T> getSearchDataAs(String luceneQuery,
      String[] indexes,
      int offset,
      int limit,
      List<SortOptions> sorts,
      Class<T> clazz);

  /**
   * Method to get document from elastic search and parse it into specified data type
   *
   * @param luceneQuery is lucene query to be passed to elastic search
   * @param indexes     is list of index to check, if empty then will search all indexes
   * @param limit       is the number of limit document to be fetched
   * @param sorts       list of sort rule
   * @param clazz       java class or type reference
   * @return List of type
   * @param <T> a T object.
   */
  <T> List<T> getSearchDataAs(String luceneQuery,
      String[] indexes,
      int limit,
      List<SortOptions> sorts,
      Class<T> clazz);

  /**
   * Method to get document from elastic search and parse it into specified data type
   *
   * @param luceneQuery is lucene query to be passed to elastic search
   * @param indexes     is list of index to check, if empty then will search all indexes
   * @param offset      is the number of document to be skipped
   * @param limit       is the number of limit document to be fetched
   * @param sorts       list of sort rule
   * @param type        java class or type reference
   * @return List of type
   * @param <T> a T object.
   */
  <T> List<T> getSearchDataAs(String luceneQuery,
      String[] indexes,
      int offset,
      int limit,
      List<SortOptions> sorts,
      TypeReference<T> type);

  /**
   * Method to get document from elastic search and parse it into specified data type
   *
   * @param luceneQuery is lucene query to be passed to elastic search
   * @param indexes     is list of index to check, if empty then will search all indexes
   * @param limit       is the number of limit document to be fetched
   * @param sorts       list of sort rule
   * @param type        java class or type reference
   * @return List of type
   * @param <T> a T object.
   */
  <T> List<T> getSearchDataAs(String luceneQuery,
      String[] indexes,
      int limit,
      List<SortOptions> sorts,
      TypeReference<T> type);

  /**
   * Method to get document from elastic search and parse it into specified data type
   *
   * @param luceneQuery is lucene query to be passed to elastic search
   * @param indexes     is list of index to check, if empty then will search all indexes
   * @param types       is unused (Elasticsearch mapping types were removed in ES7+); kept for source compatibility
   * @param offset      is the number of document to be skipped
   * @param limit       is the number of limit document to be fetched
   * @param sorts       list of sort rule
   * @param clazz       java class or type reference
   * @return List of type
   * @param <T> a T object.
   */
  <T> List<T> getSearchDataAs(String luceneQuery,
      String[] indexes,
      String[] types,
      int offset,
      int limit,
      List<SortOptions> sorts,
      Class<T> clazz);

  /**
   * Method to get document from elastic search and parse it into specified data type
   *
   * @param luceneQuery is lucene query to be passed to elastic search
   * @param indexes     is list of index to check, if empty then will search all indexes
   * @param types       is unused (Elasticsearch mapping types were removed in ES7+); kept for source compatibility
   * @param limit       is the number of limit document to be fetched
   * @param sorts       list of sort rule
   * @param clazz       java class or type reference
   * @return List of type
   * @param <T> a T object.
   */
  <T> List<T> getSearchDataAs(String luceneQuery,
      String[] indexes,
      String[] types,
      int limit,
      List<SortOptions> sorts,
      Class<T> clazz);

  /**
   * Method to get document from elastic search and parse it into specified data type
   *
   * @param luceneQuery is lucene query to be passed to elastic search
   * @param indexes     is list of index to check, if empty then will search all indexes
   * @param types       is unused (Elasticsearch mapping types were removed in ES7+); kept for source compatibility
   * @param offset      is the number of document to be skipped
   * @param limit       is the number of limit document to be fetched
   * @param sorts       list of sort rule
   * @param type        java class or type reference
   * @return List of type
   * @param <T> a T object.
   */
  <T> List<T> getSearchDataAs(String luceneQuery,
      String[] indexes,
      String[] types,
      int offset,
      int limit,
      List<SortOptions> sorts,
      TypeReference<T> type);

  /**
   * Method to get document from elastic search and parse it into specified data type
   *
   * @param luceneQuery is lucene query to be passed to elastic search
   * @param indexes     is list of index to check, if empty then will search all indexes
   * @param types       is unused (Elasticsearch mapping types were removed in ES7+); kept for source compatibility
   * @param routings    is list of elastic search routing id
   * @param offset      is the number of document to be skipped
   * @param limit       is the number of limit document to be fetched
   * @param sorts       list of sort rule
   * @param type        java class or type reference
   * @return List of type
   * @param <T> a T object.
   */
  <T> List<T> getSearchDataAs(String luceneQuery,
      String[] indexes,
      String[] types,
      String[] routings,
      int offset,
      int limit,
      List<SortOptions> sorts,
      TypeReference<T> type);

  /**
   * Method to get document from elastic search and parse it into specified data type
   *
   * @param luceneQuery is lucene query to be passed to elastic search
   * @param indexes     is list of index to check, if empty then will search all indexes
   * @param types       is unused (Elasticsearch mapping types were removed in ES7+); kept for source compatibility
   * @param limit       is the number of limit document to be fetched
   * @param sorts       list of sort rule
   * @param type        java class or type reference
   * @return List of type
   * @param <T> a T object.
   */
  <T> List<T> getSearchDataAs(String luceneQuery,
      String[] indexes,
      String[] types,
      int limit,
      List<SortOptions> sorts,
      TypeReference<T> type);

  /**
   * Method to count matching documents from elastic search
   *
   * @return CountResponse
   */
  CountResponse count();

  /**
   * Method to count matching documents from elastic search
   *
   * @param luceneQuery is lucene query to be passed to elastic search
   * @param indexes     is list of index to check, if empty then will search all indexes
   * @return CountResponse
   */
  CountResponse count(String luceneQuery, String[] indexes);

  /**
   * Method to count matching documents from elastic search
   *
   * @param luceneQuery is lucene query to be passed to elastic search
   * @param indexes     is list of index to check, if empty then will search all indexes
   * @param routing     is routing id of elastic search
   * @return CountResponse
   */
  CountResponse count(String luceneQuery, String[] indexes, String routing);

  /**
   * Method to count matching documents from elastic search
   *
   * @param request is the elastic count request
   * @return CountResponse
   */
  CountResponse count(CountRequest request);

  /**
   * Method to convert result of elastic search documents into user specified type data
   *
   * @param response is the elastic search response
   * @param type     is the user specified data type
   * @return List of type data
   * @param <T> a T object.
   */
  <T> List<T> parseSearchData(SearchResponse<ObjectNode> response, JavaType type);

  /**
   * Method to convert result of elastic search document into user specified type data
   *
   * @param response is the elastic search get one documents response
   * @param type     is the user specified data type
   * @return List of type data
   * @param <T> a T object.
   */
  <T> T parseOneDocumentData(GetResponse<ObjectNode> response, JavaType type);

  /**
   * Method to convert hash map object to list of elastic search sort options
   *
   * @param sorts is the hash map request, contains key of the field to sort and the order type
   * @return List of sort options
   */
  List<SortOptions> parseSortBuilder(Map<String, String> sorts);
}
