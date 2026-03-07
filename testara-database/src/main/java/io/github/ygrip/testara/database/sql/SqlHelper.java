package io.github.ygrip.testara.database.sql;

import com.fasterxml.jackson.core.type.TypeReference;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * <p>SqlHelper interface.</p>
 *
 * @author yunaz.ramadhan on 12/7/2019
 * @version $Id: $Id
 */
public interface SqlHelper {
  /**
   * Initialize sql helper to construct specified sql type database
   * The specified database service should be defined in properies
   *
   * @param serviceName is string data type of database service that user defined
   * @return instance of SqlHelper
   * @throws Exception when there are failure during method execution
   */
  default SqlHelper init(String serviceName) throws Exception{
    return this;
  }

  /**
   * Method to execute raw sql query
   * should throw exception if the query contains unsupported sql operations
   *
   * @param query is string data type of sql query
   * @return List of map of object
   * @throws Exception when there are failure during method execution
   */
  List<Map<String,Object>> query(String query) throws Exception;

  /**
   * Method to execute raw sql query
   * should throw exception if the query contains unsupported sql operations
   *
   * @param query is string data type of sql query
   * @param clazz is class type of the desired value
   * @param <T> instance
   * @return List of map of object
   * @throws Exception when there are failure during method execution
   */
  <T> List<T> queryAs(String query, Class<T> clazz) throws Exception;

  /**
   * Method to execute raw sql query
   * should throw exception if the query contains unsupported sql operations
   *
   * @param query is string data type of sql query
   * @param typeReference is type reference of the desired value
   * @param <T> instance
   * @return List of map of object
   * @throws Exception when there are failure during method execution
   */
  <T> List<T> queryAs(String query, TypeReference<T> typeReference) throws Exception;

  /**
   * Method to close sql db connection
   *
   * @throws SQLException when there are failure during method execution
   */
  void close() throws SQLException;

  /**
   * Method to close all sql db connection
   *
   * @throws SQLException when there are failure during method execution
   */
  void closeAll() throws SQLException;

  /**
   * Method to check connection to sql database
   *
   * @return boolean
   */
  boolean isConnected();
}
