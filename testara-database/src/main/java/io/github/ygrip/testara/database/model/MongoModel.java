package io.github.ygrip.testara.database.model;

import com.mongodb.AuthenticationMechanism;
import com.mongodb.WriteConcern;
import lombok.Data;

/**
 * <p>MongoModel class.</p>
 *
 * @author yunaz.ramadhan on 10/4/2019
 * @version $Id: $Id
 */
@Data
public class MongoModel {
  private String hosts;
  private String connectionString = null;
  private String dbName;
  private String username;
  private String password;
  private boolean sslEnabled = false;
  private boolean useDnsSeed = false;
  private boolean retryReads = false;
  private boolean retryWrites = false;
  private AuthenticationMechanism authMechanism = null;
  private WriteConcern writeConcern = null;
  private int maxConnectionLifeTimeMs = 600000;
  private int maxIdleTimeMs = 30000;
  private int connectionTimeoutMs = 30000;
  private int socketTimeoutMs = 20000;
  private int heartBeatFrequency = 10000;
  private int minHeartBeatFrequency = 500;
  private int heartBeatConnectionTimeOutMs = 20000;
  private int heartBeatSocketTimeOutMs = 20000;
}
