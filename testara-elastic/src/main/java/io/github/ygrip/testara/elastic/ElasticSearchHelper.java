package io.github.ygrip.testara.elastic;


import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
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
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.core.CountRequest;
import org.elasticsearch.client.core.CountResponse;
import org.elasticsearch.search.sort.SortBuilder;

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
   * Method to get current elastic search rest high level client connection
   *
   * @return RestHighLevelClient
   */
  RestHighLevelClient getClient();

  /**
   * Method to index document to elastic search
   *
   * @param indexName is String of the elastic search index to be checked
   * @param type      is String of the elastic search type to be checked
   * @param source    is object of document that want to be indexed in elastic search
   * @return IndexResponse
   */
  IndexResponse index(String indexName, String type, Object source);

  /**
   * Method to index document to elastic search
   *
   * @param request is index request data from the elastic search
   * @param options is the elastic request options
   * @return IndexResponse
   */
  IndexResponse index(IndexRequest request, RequestOptions options);

  /**
   * Method to update document to elastic search
   *
   * @param request is index request data from the elastic search
   * @param options is the elastic request options
   * @return IndexResponse
   */
  UpdateResponse update(UpdateRequest request, RequestOptions options);

  /**
   * Method to get document by id from elastic search
   *
   * @param indexName is String of the elastic search index to be checked
   * @param type      is String of the elastic search type to be checked
   * @param id        is id of the elastic document index to be checked
   * @return GetResponse
   */
  GetResponse getOne(String indexName, String type, String id);

  /**
   * Method to get document by id from elastic search
   *
   * @param indexName is String of the elastic search index to be checked
   * @param type      is String of the elastic search type to be checked
   * @param routing      is String of the elastic search routing id to be checked
   * @param id        is id of the elastic document index to be checked
   * @return GetResponse
   */
  GetResponse getOne(String indexName, String type, String routing, String id);

  /**
   * Method to get document by id from elastic search
   *
   * @param request is get request data from the elastic search
   * @param options is the elastic request options
   * @return GetResponse
   */
  GetResponse getOne(GetRequest request, RequestOptions options);

  /**
   * Method to get document by id from elastic search
   *
   * @param indexName is String of the elastic search index to be checked
   * @param type      is String of the elastic search type to be checked
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
   * @param type      is String of the elastic search type to be checked
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
   * @param type      is String of the elastic search type to be checked
   * @param id        is id of the elastic document index to be checked
   * @return GetResponse
   */
  DeleteResponse deleteOne(String indexName, String type, String id);

  /**
   * Method to delete document by id from elastic search
   *
   * @param indexName is String of the elastic search index to be checked
   * @param type      is String of the elastic search type to be checked
   * @param routing      is String of the elastic search routing id to be checked
   * @param id        is id of the elastic document index to be checked
   * @return GetResponse
   */
  DeleteResponse deleteOne(String indexName, String type, String routing, String id);

  /**
   * Method to delete document by id from elastic search
   *
   * @param request is delete request data from the elastic search
   * @param options is the elastic request options
   * @return GetResponse
   */
  DeleteResponse deleteOne(DeleteRequest request, RequestOptions options);

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
   * @return ClusterHealthResponse
   */
  ClusterHealthResponse getClusterHealthInfo();

  /**
   * Method to search documents from elastic search
   *
   * @param luceneQuery is lucene query to be passed to elastic search
   * @param indexes     is list of index to check, if empty then will search all indexes
   * @param offset      is number of document to skip
   * @param limit       is number of document to fetch
   * @param sorts       list of sort rule
   * @param type        is the elastic search type
   * @param options     is the elastic request options
   * @return SearchResponse
   */
  SearchResponse search(String luceneQuery,
      String[] indexes,
      int offset,
      int limit,
      List<SortBuilder<?>> sorts,
      SearchType type,
      RequestOptions options);

  /**
   * Method to search documents from elastic search
   *
   * @param luceneQuery is lucene query to be passed to elastic search
   * @param indexes     is list of index to check, if empty then will search all indexes
   * @param offset      is number of document to skip
   * @param limit       is number of document to fetch
   * @param types       is list of document type
   * @param sorts       list of sort rule
   * @param searchType  is the elastic search type
   * @param options     is the elastic request options
   * @return SearchResponse
   */
  SearchResponse search(String luceneQuery,
      String[] indexes,
      int offset,
      int limit,
      String[] types,
      List<SortBuilder<?>> sorts,
      SearchType searchType,
      RequestOptions options);

  /**
   * Method to search documents from elastic search
   *
   * @param luceneQuery is lucene query to be passed to elastic search
   * @param indexes     is list of index to check, if empty then will search all indexes
   * @param offset      is number of document to skip
   * @param limit       is number of document to fetch
   * @param types       is list of document type
   * @param routings     is list of routing id of elastic search
   * @param sorts       list of sort rule
   * @param searchType  is the elastic search type
   * @param options     is the elastic request options
   * @return SearchResponse
   */
  SearchResponse search(String luceneQuery,
      String[] indexes,
      int offset,
      int limit,
      String[] types,
      String[] routings,
      List<SortBuilder<?>> sorts,
      SearchType searchType,
      RequestOptions options);

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
  SearchResponse search(String luceneQuery,
      String[] indexes,
      int offset,
      int limit,
      List<SortBuilder<?>> sorts,
      SearchType type);

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
  SearchResponse search(String luceneQuery,
      String[] indexes,
      int offset,
      int limit,
      List<SortBuilder<?>> sorts);

  /**
   * Method to search documents from elastic search
   *
   * @param luceneQuery is lucene query to be passed to elastic search
   * @param indexes     is list of index to check, if empty then will search all indexes
   * @param limit       is number of document to fetch
   * @param sorts       list of sort rule
   * @return SearchResponse
   */
  SearchResponse search(String luceneQuery,
      String[] indexes,
      int limit,
      List<SortBuilder<?>> sorts);

  /**
   * Method to search documents from elastic search
   *
   * @param luceneQuery is lucene query to be passed to elastic search
   * @param indexes     is list of index to check, if empty then will search all indexes
   * @param type        is the elastic search type
   * @param options     is the elastic request options
   * @param sorts       list of sort rule
   * @return SearchResponse
   */
  SearchResponse search(String luceneQuery,
      String[] indexes,
      List<SortBuilder<?>> sorts,
      SearchType type,
      RequestOptions options);

  /**
   * Method to search documents from elastic search
   *
   * @param luceneQuery is lucene query to be passed to elastic search
   * @param options     is the elastic request options
   * @param sorts       list of sort rule
   * @return SearchResponse
   * @param indexes an array of {@link String} objects.
   */
  SearchResponse search(String luceneQuery,
      String[] indexes,
      List<SortBuilder<?>> sorts,
      RequestOptions options);

  /**
   * Method to search documents from elastic search
   *
   * @param luceneQuery is lucene query to be passed to elastic search
   * @param indexes     is list of index to check, if empty then will search all indexes
   * @param sorts       list of sort rule
   * @return SearchResponse
   */
  SearchResponse search(String luceneQuery, String[] indexes, List<SortBuilder<?>> sorts);

  /**
   * Method to search documents from elastic search
   *
   * @param request is the elastic search request
   * @param options is the elastic request options
   * @return SearchResponse
   */
  SearchResponse search(SearchRequest request, RequestOptions options);

  /**
   * Method to search documents from elastic search
   *
   * @param request is the elastic search request
   * @return SearchResponse
   */
  SearchResponse search(SearchRequest request);

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
      List<SortBuilder<?>> sorts,
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
      List<SortBuilder<?>> sorts,
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
      List<SortBuilder<?>> sorts,
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
      List<SortBuilder<?>> sorts,
      TypeReference<T> type);

  /**
   * Method to get document from elastic search and parse it into specified data type
   *
   * @param luceneQuery is lucene query to be passed to elastic search
   * @param indexes     is list of index to check, if empty then will search all indexes
   * @param types       is list of document type
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
      List<SortBuilder<?>> sorts,
      Class<T> clazz);

  /**
   * Method to get document from elastic search and parse it into specified data type
   *
   * @param luceneQuery is lucene query to be passed to elastic search
   * @param indexes     is list of index to check, if empty then will search all indexes
   * @param types       is list of document type
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
      List<SortBuilder<?>> sorts,
      Class<T> clazz);

  /**
   * Method to get document from elastic search and parse it into specified data type
   *
   * @param luceneQuery is lucene query to be passed to elastic search
   * @param indexes     is list of index to check, if empty then will search all indexes
   * @param types       is list of document type
   * @param offset      is the number of document to be skipped
   * @param limit       is the number of limit document to be fetched
   * @param sorts       list of sort rule
   * @param types       is list of document type
   * @param type        java class or type reference
   * @return List of type
   * @param <T> a T object.
   */
  <T> List<T> getSearchDataAs(String luceneQuery,
      String[] indexes,
      String[] types,
      int offset,
      int limit,
      List<SortBuilder<?>> sorts,
      TypeReference<T> type);

  /**
   * Method to get document from elastic search and parse it into specified data type
   *
   * @param luceneQuery is lucene query to be passed to elastic search
   * @param indexes     is list of index to check, if empty then will search all indexes
   * @param types       is list of document type
   * @param routings     is list of elastic search routing id
   * @param offset      is the number of document to be skipped
   * @param limit       is the number of limit document to be fetched
   * @param sorts       list of sort rule
   * @param types       is list of document type
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
      List<SortBuilder<?>> sorts,
      TypeReference<T> type);

  /**
   * Method to get document from elastic search and parse it into specified data type
   *
   * @param luceneQuery is lucene query to be passed to elastic search
   * @param indexes     is list of index to check, if empty then will search all indexes
   * @param types       is list of document type
   * @param limit       is the number of limit document to be fetched
   * @param sorts       list of sort rule
   * @param types       is list of document type
   * @param type        java class or type reference
   * @return List of type
   * @param <T> a T object.
   */
  <T> List<T> getSearchDataAs(String luceneQuery,
      String[] indexes,
      String[] types,
      int limit,
      List<SortBuilder<?>> sorts,
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
   * @param options     is the elastic request options
   * @return CountResponse
   */
  CountResponse count(String luceneQuery, String[] indexes, RequestOptions options);

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
   * @param options     is the elastic request options
   * @return CountResponse
   */
  CountResponse count(String luceneQuery, String[] indexes, String routing, RequestOptions options);

  /**
   * Method to count matching documents from elastic search
   *
   * @param request is the elastic count request
   * @param options is the elastic request options
   * @return CountResponse
   */
  CountResponse count(CountRequest request, RequestOptions options);

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
  <T> List<T> parseSearchData(SearchResponse response, JavaType type);

  /**
   * Method to convert result of elastic search document into user specified type data
   *
   * @param response is the elastic search get one documents response
   * @param type     is the user specified data type
   * @return List of type data
   * @param <T> a T object.
   */
  <T> T parseOneDocumentData(GetResponse response, JavaType type);

  /**
   * Method to convert hash map object to list of elastic search sort builder
   *
   * @param sorts is the hash map request, contains key of the field to sort and the order type
   * @return List of source builder data
   */
  List<SortBuilder<?>> parseSortBuilder(Map<String, String> sorts);
}
