package io.github.ygrip.testara.database.nosql;

import io.github.ygrip.testara.core.BaseTests;
import io.github.ygrip.testara.core.TestWith;
import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.database.config.MongoProperties;
import io.github.ygrip.testara.database.context.TestDatabase;
import io.github.ygrip.testara.database.model.MongoDbConnection;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.net.UnknownServiceException;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("mongo")
@Tag("database")
@TestWith(properties = {"classpath:application.properties", "classpath:configuration.properties"})
public class NoSqlDatabaseTests extends BaseTests {

  @Test
  public void bindProperties() {
    MongoProperties props = TestFramework.context().configuration().get("mongo", MongoProperties.class);
    assertThat(props, is(notNullValue()));
  }

  @Test
  public void checkConnection() throws Throwable {
    MongoDbConnection agpDb = TestDatabase.mongo("agp").getCurrentConnection();

    assertThat(agpDb.isConnected(), equalTo(true));
  }

  @Test
  public void simpleQuery() throws Throwable {
    List<Object> result = TestDatabase.mongo("agp")
        .selectCollection("notification_inbox_notification_inboxes")
        .distinct("memberId", "{}");

    assertThat(result, is(notNullValue()));
  }

  @Test
  public void countData() throws Throwable {
    long result = TestDatabase.mongo("agp")
      .selectCollection("notification_inbox_notification_inboxes")
      .count("{}");

    assertThat(result, is(notNullValue()));
  }

  @Test
  public void unableToConnectUndefinedDatabase() throws Throwable {
    Exception exception =
        assertThrows(UnknownServiceException.class, () -> TestDatabase.mongo("random_db").getCurrentConnection());

    assertThat(exception, notNullValue());
  }
}
