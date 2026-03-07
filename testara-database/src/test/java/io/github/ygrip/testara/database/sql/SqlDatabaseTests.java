package io.github.ygrip.testara.database.sql;

import io.github.ygrip.testara.core.BaseTests;
import io.github.ygrip.testara.core.TestWith;
import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.database.config.DatabaseProperties;
import io.github.ygrip.testara.database.context.TestDatabase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.net.UnknownServiceException;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("sql")
@Tag("database")
@TestWith(properties = {"classpath:application.properties", "classpath:configuration.properties"})
public class SqlDatabaseTests extends BaseTests {
  @Test
  public void bindProperties() {
    DatabaseProperties props = TestFramework.context().configuration().get("sql", DatabaseProperties.class);
    assertThat(props, is(notNullValue()));
  }

  @Test
  public void checkConnection() throws Throwable {
    boolean connected = TestDatabase.sql("xmessage").isConnected();

    assertThat(connected, equalTo(true));
  }

  @Test
  public void unableToConnectUndefinedDatabase() throws Throwable {
    Exception exception = assertThrows(UnknownServiceException.class, () -> TestDatabase.sql("random_db"));

    assertThat(exception, notNullValue());
  }

  @Test
  public void simpleQuery() throws Throwable {
    List<Map<String, Object>> result = TestDatabase.sql("xmessage").query("SELECT * FROM msg_message_sent LIMIT 1");

    assertThat(result, is(notNullValue()));
    assertThat(result.size(), equalTo(1));
  }
}
