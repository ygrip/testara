package io.github.ygrip.testara.database.context;

import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.database.nosql.MongoHelper;
import io.github.ygrip.testara.database.sql.SqlHelper;

public final class TestDatabase {
  public static MongoHelper mongo() {
    return TestFramework.context().get(MongoHelper.class);
  }

  public static MongoHelper mongo(String dbName) throws Exception {
    return mongo().init(dbName);
  }

  public static SqlHelper sql() {
    return TestFramework.context().get(SqlHelper.class);
  }

  public static SqlHelper sql(String dbName) throws Exception {
    return sql().init(dbName);
  }
}
