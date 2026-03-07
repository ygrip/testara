package io.github.ygrip.testara.database.model;

import com.google.common.base.Splitter;
import lombok.Data;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <p>SqlModel class.</p>
 *
 * @author yunaz.ramadhan on 10/4/2019
 * @version $Id: $Id
 */
@Data
public class SqlModel {
  private String hostName;
  private Integer port;
  private String username;
  private String password;
  private String dbName;
  private SupportedSQLDb dbType;
  private String targetServerType;
  private Boolean sslRequired;
  private Integer timeout = 3;
  private String uri;

  public SupportedSQLDb getDbType() {
    if (dbType == null && uri != null) {
      try {
        String url = constructUri();
        if (url.startsWith("jdbc:oracle:thin:@")) {
          return SupportedSQLDb.ORACLE;
        } else if (url.startsWith("jdbc:mysql://")) {
          return SupportedSQLDb.MYSQL;
        } else if (url.startsWith("jdbc:mariadb://")) {
          return SupportedSQLDb.MARIADB;
        } else if (url.startsWith("jdbc:postgresql://")) {
          return SupportedSQLDb.POSTGRESQL;
        }
      } catch (Exception ignored) {

      }
    }
    return dbType;
  }

  public String constructUri() throws Exception {
    if (getUri() != null && !getUri().isEmpty()) {
      String query = uri.split("\\?")[1];
      final Map<String, String> params = Splitter.on('&').trimResults().withKeyValueSeparator('=').split(query);
      if (params.containsKey("user") && params.containsKey("password")) {
        return getUri();
      } else {
        StringBuilder url = new StringBuilder();
        url.append(getUri());
        if (params.isEmpty()) {
          url.append("?");
        } else {
          url.append("&");
        }
        url.append("user=")
            .append(URLEncoder.encode(getUsername(), StandardCharsets.UTF_8))
            .append("&password=")
            .append(URLEncoder.encode(getPassword(), StandardCharsets.UTF_8));
        return url.toString();
      }
    } else {
      StringBuilder url = new StringBuilder();
      url.append("jdbc:");

      if (getDbType().equals(SupportedSQLDb.POSTGRESQL)) {
        url.append("postgresql://");
      } else if (getDbType().equals(SupportedSQLDb.MYSQL)) {
        url.append("mysql://");
      } else if (getDbType().equals(SupportedSQLDb.MARIADB)) {
        url.append("mariadb://");
      } else if (getDbType().equals(SupportedSQLDb.ORACLE)) {
        url.append("oracle:thin:@");
      } else {
        throw new Exception(String.format("Database type %s is not supported,\nPlease use one of the following : %s",
            getDbType(),
            Arrays.stream(SupportedSQLDb.values()).map(Enum::name).collect(Collectors.joining("\n\t"))));
      }

      url.append(StringUtils.isBlank(getHostName()) ? "localhost" : getHostName());
      if (ObjectUtils.isNotEmpty(getPort())) {
        url.append(":").append(getPort());
      }
      url.append("/")
          .append(getDbName())
          .append("?user=")
          .append(URLEncoder.encode(getUsername(), StandardCharsets.UTF_8))
          .append("&password=")
          .append(URLEncoder.encode(getPassword(), StandardCharsets.UTF_8));

      if (getSslRequired()) {
        url.append("&ssl=true");
      }
      if (getTargetServerType() != null && !getTargetServerType().isEmpty()) {
        url.append("&targetServerType=").append(getTargetServerType());
      }
      return url.toString();
    }
  }
}
