package io.github.ygrip.testara.database.nosql;

import io.github.ygrip.testara.database.model.MongoDbConnection;
import com.mongodb.reactivestreams.client.MongoCollection;
import org.bson.Document;

import java.util.LinkedHashMap;
import java.util.List;

/**
 * <p>MongoHelper interface.</p>
 *
 * @author yunaz.ramadhan on 12/6/2019
 * @version $Id: $Id
 */
public interface MongoHelper {
  /**
   * Initialize the mongo helper to construct the user specified database specification
   *
   * @param service is String of the db service defined in properties
   * @return instance of MongoHelper
   * @throws Exception exceptions
   */
  MongoHelper init(String service) throws Exception;

  /**
   * Method to get current mongo db connection
   *
   * @return MongoDbConnection
   */
  MongoDbConnection getCurrentConnection();

  /**
   * Method to check whether current mongo db connection is up
   *
   * @return boolean
   */
  Boolean isConnected();

  /**
   * Set the mongo db collection to connect to
   *
   * @param collectionName is string of collection name
   * @return instance of MongoHelper
   */
  MongoHelper selectCollection(String collectionName);

  /**
   * Get the connected mongo db collection
   *
   * @return MongoCollection object
   */
  MongoCollection<Document> getCollection();

  /**
   * Raw query to mongo db collection by query filter only,
   * then construct to List of LinkedHashMap
   *
   * @param query is string data type to define the query filter
   * @return list of linked hashmap
   */
  List<LinkedHashMap<String, Object>> rawQuery(String query);

  /**
   * Raw query to mongo db collection by query filter and limit,
   * then construct to List of LinkedHashMap
   *
   * @param query is string data type to define the query filter
   * @param limit is int data type to define limit of the query
   * @return list of linked hashmap
   */
  List<LinkedHashMap<String, Object>> rawQuery(String query, int limit);

  /**
   * Raw query to mongo db collection by query filter and projection of field,
   * then construct to List of LinkedHashMap
   *
   * @param query       is string data type to define the query filter
   * @param projections is string data type to define field to project
   * @return list of linked hashmap
   */
  List<LinkedHashMap<String, Object>> rawQuery(String query, String projections);

  /**
   * Raw query to mongo db collection by query filter and limit and sorting rule,
   * then construct to List of LinkedHashMap
   *
   * @param query is string data type to define the query filter
   * @param sort  is string data type to define sorting rule of the query
   * @param limit is int data type to define limit of the query
   * @return list of linked hashmap
   */
  List<LinkedHashMap<String, Object>> rawQuery(String query, String sort, int limit);

  /**
   * Raw query to mongo db collection by query filter and limit and sorting rule,
   * then construct to List of LinkedHashMap
   *
   * @param query       is string data type to define the query filter
   * @param sort        is string data type to define sorting rule of the query
   * @param projections is string data type to define what field to project
   * @param limit       is int data type to define limit of the query
   * @param skip        is int data type to define how forOne to skip
   * @return list of linked hashmap
   */
  List<LinkedHashMap<String, Object>> rawQuery(String query, String sort, String projections, int limit, int skip);

  /**
   * Delete one forOne from mongo db collection, only the first forOne will be deleted,
   * you may as well want to define correct sorting rule so you can delete the specified data
   * then construct result to List of LinkedHashMap
   *
   * @param query is string data type to define the query filter
   * @param sort  is string data type to define sorting rule of the query
   * @return list of linked hashmap
   */
  List<LinkedHashMap<String, Object>> delete(String query, String sort);

  /**
   * Delete one forOne from mongo db collection, only the first forOne will be deleted,
   * you may as well want to define correct sorting rule so you can delete the specified data
   * then construct result to List of LinkedHashMap
   *
   * @param query   is string data type to define the query filter
   * @param sort    is string data type to define sorting rule of the query
   * @param useMany is boolean data type indicating delete multiple data or not
   * @return list of linked hashmap
   */
  List<LinkedHashMap<String, Object>> delete(String query, String sort, boolean useMany);

  /**
   * Update query to mongo db collection by query filter and sorting rule,
   * then construct result to List of LinkedHashMap
   *
   * @param query   is string data type to define the query filter
   * @param update  is string data type to define sorting rule of the query
   * @param useMany is boolean data type to define whether to update multiple forOne or not
   * @return list of linked hashmap
   */
  List<LinkedHashMap<String, Object>> update(String query, String update, boolean useMany);

  /**
   * Aggregation query to mongo db collection
   * then construct result to List of LinkedHashMap
   *
   * @param query is string data type to define the aggregation query pipelines
   * @return list of linked hashmap
   */
  List<LinkedHashMap<String, Object>> aggregate(String query);

  /**
   * Insert query to mongo db collection
   * then construct result to List of LinkedHashMap
   *
   * @param document is string data type to define the insert query
   * @return list of linked hashmap
   */
  List<LinkedHashMap<String, Object>> insert(Object document);

  /**
   * Method to close current mongo connection
   */
  void close();

  /**
   * Method to close specific mongo connection
   *
   * @param serviceName service name
   */
  void close(String serviceName);

  /**
   * Method to close all mongo connection
   */
  void closeAll();

  /**
   * Count query to mongo db collection by query filter only,
   * then construct to List of LinkedHashMap
   *
   * @param query is string data type to define the query filter
   * @return size of countable data
   */
  long count(String query);

  /**
   * <p>getIndexes.</p>
   *
   * @return index information of selected collection
   */
  List<LinkedHashMap<String, Object>> getIndexes();

  /**
   * <p>distinct.</p>
   *
   * @param field a {@link String} object.
   * @return a {@link List} object.
   */
  List<Object> distinct(String field);

  /**
   * <p>distinct.</p>
   *
   * @param field a {@link String} object.
   * @param query a {@link String} object.
   * @return a {@link List} object.
   */
  List<Object> distinct(String field, String query);
}
