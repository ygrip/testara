package io.github.ygrip.testara.core.data;

import io.github.ygrip.testara.core.model.DefaultData;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * <p>DataHolder interface.</p>
 *
 * @author yunaz.ramadhan on 12/7/2019
 * @version $Id: $Id
 */
public interface DataHolder {
  /**
   * Method to return all registered request data types
   * will hold all request data type annotated with @DefaultRequestData alongside its instance
   *
   * @return Map of class of a default data type
   */
  LinkedHashMap<Class<? extends DefaultData>, DefaultData> getRequests();

  /**
   * Method to return all registered response data types
   * will hold all response data type annotated with @DefaultResponseData alongside its instance
   *
   * @return Map of class of a default data type
   */
  LinkedHashMap<Class<? extends DefaultData>, DefaultData> getResponses();

  /**
   * Method to return the first DefaultData instance that has the specified field
   * if no class is found then will return the DefaultRequestData
   *
   * @param fieldName is the specified field that user wants to seek
   * @return pair of DefaultData instance and the request object
   */
  Map.Entry<DefaultData, Object> getRequest(String fieldName);

  /**
   * Method to return the first DefaultData instance that has the specified field
   * if no class is found then will return the DefaultRequestData
   *
   * @param fieldName is the specified field that user wants to seek
   * @return pair of DefaultData instance and the response object
   */
  Map.Entry<DefaultData, Object> getResponse(String fieldName);

  /**
   * <p>getResponse.</p>
   *
   * @param clazz is target response data class that extends default data
   * @param <T>   generic type to return
   * @return instance of target response data class
   */
  <T> T getResponse(Class<T> clazz);

  /**
   * <p>getRequest.</p>
   *
   * @param clazz is target request data class that extends default data
   * @param <T>   generic type to return
   * @return instance of target request data class
   */
  <T> T getRequest(Class<T> clazz);

  /**
   * This method is to reset all data populated in DataHolder catalogs.
   * you can use this method to reduce the memory used to save request or response data
   */
  void reset();

  /**
   * This method will reset all response data populated in DatHolder catalogs
   */
  void resetResponsesData();

  /**
   * This method will reset all request data populated in DatHolder catalogs
   */
  void resetRequestsData();

  /**
   * This method will reset specific request data class stored in DatHolder catalogs
   *
   * @param clazz clazz
   */
  void resetRequestDataOnClass(Class<? extends DefaultData> clazz);

  /**
   * This method will reset specific response data class stored in DatHolder catalogs
   *
   * @param clazz clazz
   */
  void resetResponseDataOnClass(Class<? extends DefaultData> clazz);

  /**
   * This method will reset specific request data field stored in DatHolder catalogs
   *
   * @param path path
   */
  void resetRequestData(String path);

  /**
   * This method will reset specific response data field stored in DatHolder catalogs
   *
   * @param path path
   */
  void resetResponseData(String path);

  /**
   * This method will set specific request data field value stored in DatHolder catalogs
   *
   * @param path path
   */
  void setRequest(String path, Object data);

  /**
   * This method will set specific request data field value stored in DatHolder catalogs
   *
   * @param path path
   */
  void setResponse(String path, Object data);
}
