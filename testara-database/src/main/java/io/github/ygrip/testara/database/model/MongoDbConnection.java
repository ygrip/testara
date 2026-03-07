package io.github.ygrip.testara.database.model;

import io.github.ygrip.testara.database.support.AwaitStream;
import com.mongodb.MongoClientSettings;
import com.mongodb.ServerAddress;
import com.mongodb.event.ServerHeartbeatFailedEvent;
import com.mongodb.event.ServerHeartbeatStartedEvent;
import com.mongodb.event.ServerHeartbeatSucceededEvent;
import com.mongodb.event.ServerMonitorListener;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.mongodb.reactivestreams.client.MongoDatabase;
import lombok.extern.log4j.Log4j2;
import org.bson.Document;

import java.time.Duration;
import java.util.List;

/**
 * <p>MongoDbConnection class.</p>
 *
 * @author yunaz.ramadhan on 8/12/2021
 * @version $Id: $Id
 */
@Log4j2
public class MongoDbConnection implements ServerMonitorListener {
  private final String serviceName;
  private final String dbName;
  private final List<ServerAddress> addressList;
  private final MongoClientSettings settings;
  private final long maxIdleTime;
  private MongoClient client;
  private boolean isConnected;
  private long lastConnectionAt;

  /**
   * <p>Constructor for MongoDbConnection.</p>
   *
   * @param serviceName a {@link String} object.
   * @param dbName      a {@link String} object.
   * @param addressList a {@link List< ServerAddress>} object.
   * @param settings    a {@link MongoClientSettings} object.
   * @param maxIdleTime a {@link Integer} object.
   */
  public MongoDbConnection(String serviceName,
      List<ServerAddress> addressList,
      String dbName,
      MongoClientSettings settings,
      int maxIdleTime) {
    this.serviceName = serviceName;
    this.addressList = addressList;
    this.settings = settings;
    this.maxIdleTime = maxIdleTime;
    establishConnection();
    this.dbName = dbName;
  }

  private void establishConnection() {
    //renew connection
    close();
    // Build settings with ServerMonitorListener attached to track connection state
    MongoClientSettings settingsWithListener = MongoClientSettings.builder(this.settings)
        .applyToServerSettings(builder -> builder.addServerMonitorListener(this))
        .build();
    this.client = MongoClients.create(settingsWithListener);
    this.isConnected = checkConnected(this.client);
    this.lastConnectionAt = System.currentTimeMillis();
  }

  /**
   * Check if the client can connect by sending a ping command.
   * IMPORTANT: This method does NOT close the client - it only verifies connectivity.
   */
  boolean checkConnected(MongoClient client) {
    try {
      Document ping =
          AwaitStream.one(client.getDatabase("admin").runCommand(new Document("ping", 1)), Duration.ofSeconds(5));
      return ping != null && !ping.isEmpty();
    } catch (Exception e) {
      log.warn("#Failed to verify MongoDB connection for {}: {}", serviceName, e.getMessage());
      return false;
    }
  }

  /**
   * <p>Getter for the field <code>serviceName</code>.</p>
   *
   * @return a {@link String} object.
   */
  public String getServiceName() {
    return this.serviceName;
  }

  /**
   * <p>Getter for the field <code>dbName</code>.</p>
   *
   * @return a {@link String} object.
   */
  public String getDbName() {
    return this.dbName;
  }

  MongoClient getConnection() {
    return this.client;
  }

  /**
   * <p>getDatabase.</p>
   *
   * @return a {@link MongoDatabase} object.
   */
  public MongoDatabase getDatabase() {
    if (this.client == null) {
      throw new IllegalStateException("MongoDB client is not initialized for service: " + serviceName);
    }
    return getConnection().getDatabase(this.dbName);
  }

  /**
   * <p>isConnected.</p>
   *
   * @return a boolean.
   */
  public boolean isConnected() {
    return this.isConnected && this.client != null;
  }

  /**
   * <p>wakeUp.</p>
   * Re-establishes connection if the client is null, disconnected, or stale.
   */
  public void wakeUp() {
    if (this.client == null || !isConnected() || isStaleConnection()) {
      log.debug("#Waking up MongoDB connection for {}", serviceName);
      establishConnection();
    }
  }

  /**
   * <p>getConnectionTimeMs.</p>
   *
   * @return a long.
   */
  public long getConnectionTimeMs() {
    final long currentTime = System.currentTimeMillis();
    return Math.max((currentTime - this.lastConnectionAt), 0);
  }

  /**
   * <p>isStaleConnection.</p>
   *
   * @return a boolean.
   */
  public boolean isStaleConnection() {
    final long currentTime = System.currentTimeMillis();
    boolean stale = getConnectionTimeMs() > this.maxIdleTime;
    if (stale) {
      log.trace("#Got stale connection to {} mongodb", this.serviceName);
    }
    return stale;
  }

  /**
   * <p>close.</p>
   */
  public void close() {
    if (this.client != null) {
      try {
        log.debug("#Closing mongo connection from {}", serviceName);
        this.client.close();
      } catch (Exception e) {
        log.warn("#Error while closing mongo connection for {}: {}", serviceName, e.getMessage());
      } finally {
        this.client = null;
        this.isConnected = false;
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void serverHearbeatStarted(ServerHeartbeatStartedEvent serverHeartbeatStartedEvent) {
    log.trace("#Receive heartbeat from {} at hosts {} ", serviceName, addressList);
    this.isConnected = true;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void serverHeartbeatSucceeded(ServerHeartbeatSucceededEvent serverHeartbeatSucceededEvent) {
    log.debug("#Mongo connection to {} is established", serviceName);
    this.isConnected = true;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void serverHeartbeatFailed(ServerHeartbeatFailedEvent serverHeartbeatFailedEvent) {
    log.warn("#Mongo connection to {} is closed", serviceName);
    this.isConnected = false;
  }
}
