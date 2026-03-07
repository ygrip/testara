package io.github.ygrip.testara.database.sql;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import io.github.ygrip.testara.core.context.TestComponent;
import io.github.ygrip.testara.core.mapper.MapperHelper;
import io.github.ygrip.testara.core.registry.RegistryScope;
import io.github.ygrip.testara.core.support.CommonHelper;
import io.github.ygrip.testara.database.config.DatabaseProperties;
import io.github.ygrip.testara.database.model.SqlModel;
import com.mysql.cj.jdbc.AbandonedConnectionCleanupThread;
import lombok.extern.log4j.Log4j2;

import java.net.UnknownServiceException;
import java.security.InvalidParameterException;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>SqlHelperImpl class.</p>
 *
 * @author yunaz.ramadhan on 10/4/2019
 * @version $Id: $Id
 */
@TestComponent(scope = RegistryScope.TEST)
@Log4j2
public class SqlHelperImpl implements SqlHelper {
  private final DatabaseProperties properties;
  private final Map<String, Connection> connections;
  private String currentConnection;

  /**
   * <p>Constructor for SqlHelperImpl.</p>
   *
   * @param properties a {@link io.github.ygrip.testara.database.config.DatabaseProperties} object.
   */
  public SqlHelperImpl(DatabaseProperties properties) {
    this.properties = properties;
    this.connections = new HashMap<>();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public SqlHelper init(String serviceName) throws Exception {
    SqlModel model = this.properties.getService().getOrDefault(serviceName, null);
    if (CommonHelper.isBlank(model)) {
      this.currentConnection = null;
      throw new UnknownServiceException(String.format("Cannot find target service %s", serviceName));
    }
    if (CommonHelper.isBlank(model)) {
      throw new InvalidParameterException("No specified database service to construct");
    } else {
      log.info("#Establishing {} database connection to {}", model.getDbType(), model.constructUri());
      Connection connection = connect(model);
      if (connection.isReadOnly()) {
        log.warn("#WARNING {} database at {} is in read-only mode", model.getDbType(), model.constructUri());
      }
      this.connections.put(serviceName, connection);
      this.currentConnection = serviceName;
    }
    return this;
  }

  private Connection connect(SqlModel model) throws Exception {
    if (model == null || model.constructUri() == null) {
      throw new InvalidParameterException("Invalid database configuration");
    } else {
      return DriverManager.getConnection(model.constructUri());
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<Map<String, Object>> query(String query) throws Exception {
    return executeQuery(query, MapperHelper.getGenericType(new TypeReference<Map<String, Object>>() {
    }));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public <T> List<T> queryAs(String query, Class<T> clazz) throws Exception {
    return executeQuery(query, MapperHelper.getGenericType(clazz));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public <T> List<T> queryAs(String query, TypeReference<T> typeReference) throws Exception {
    return executeQuery(query, MapperHelper.getGenericType(typeReference));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void close() throws SQLException {
    if (isConnected()) {
      log.debug("#Closing {} database connection", this.currentConnection);
      getCurrentConnection().close();
      this.connections.remove(this.currentConnection);
    } else {
      log.debug("#No active database connection has been made, action skipped");
    }
  }

  @Override
  public void closeAll() throws SQLException {
    if (!CommonHelper.isBlank(this.connections)) {
      //removing from listed connection
      try {
        for (String key : this.connections.keySet()) {
          SqlModel model = this.properties.getService().getOrDefault(key, new SqlModel());
          log.debug("Closing {} sql database connection", key);
          this.connections.get(key).close();
          this.connections.remove(key);
        }
      } catch (Exception ignored) {
      }
    }
    //removing from driver manager
    Enumeration<Driver> drivers = DriverManager.getDrivers();

    Driver driver = null;

    // clear drivers
    while (drivers.hasMoreElements()) {
      try {
        driver = drivers.nextElement();
        DriverManager.deregisterDriver(driver);
      } catch (SQLException ignored) {

      }
    }
    // MySQL driver leaves around a thread. This static method cleans it up.
    AbandonedConnectionCleanupThread.uncheckedShutdown();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isConnected() {
    try {
      SqlModel model = this.properties.getService().getOrDefault(this.currentConnection, new SqlModel());
      return !getCurrentConnection().isClosed();
    } catch (Exception e) {
      return false;
    }
  }

  private void refreshConnection() {
    try {
      if (!isConnected()) {
        Connection connection = connect(this.properties.getService().get(this.currentConnection));
        this.connections.put(this.currentConnection, connection);
      }
    } catch (Exception e) {
      log.warn("Fail to refresh database connection for {}", this.currentConnection);
    }
  }

  private Connection getCurrentConnection() {
    return this.connections.getOrDefault(this.currentConnection, null);
  }

  private <T> List<T> executeQuery(String query, JavaType type) throws Exception {
    log.debug("#Executing SQL query {}", query);
    List<T> result = new ArrayList<>();
    if (CommonHelper.isBlank(getCurrentConnection())) {
      throw new Exception("no database connection established");
    } else {
      String trimmedQuery = query == null ? null : query.trim();

      if (CommonHelper.isBlank(trimmedQuery)) {
        throw new IllegalArgumentException("#ERROR cannot execute empty sql query");
      }

      refreshConnection();
      Connection connection = getCurrentConnection();

      try {
        connection.setAutoCommit(false);

        try (PreparedStatement statement = connection.prepareStatement(trimmedQuery)) {
          boolean hasResultSet = statement.execute();

          if (hasResultSet) {
            try (ResultSet resultSet = statement.getResultSet()) {
              if (resultSet != null) {
                ResultSetMetaData metaData = resultSet.getMetaData();
                int columnCount = metaData.getColumnCount();

                List<String> columnNames = new ArrayList<>(columnCount);
                for (int i = 1; i <= columnCount; i++) {
                  columnNames.add(metaData.getColumnLabel(i));
                }

                while (resultSet.next()) {
                  Map<String, Object> row = new HashMap<>();
                  for (String columnName : columnNames) {
                    row.put(columnName, resultSet.getObject(columnName));
                  }
                  result.add(MapperHelper.toObject(row, type));
                }
              }
            }
          }
        }

        connection.commit();
      } catch (Exception ex) {
        try {
          connection.rollback();
        } catch (SQLException rollbackEx) {
          log.error("Rollback failed", rollbackEx);
        }
        log.error("SQL execution failed", ex);
        throw ex;
      } finally {
        try {
          connection.setAutoCommit(true);
        } catch (SQLException ex) {
          log.warn("Failed to reset autoCommit", ex);
        }
      }
    }
    return result;
  }
}
