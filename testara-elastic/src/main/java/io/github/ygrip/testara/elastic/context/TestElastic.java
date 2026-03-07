package io.github.ygrip.testara.elastic.context;

import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.elastic.ElasticSearchHelper;

public final class TestElastic {
  public static ElasticSearchHelper search() {
    return TestFramework.context().get(ElasticSearchHelper.class);
  }

  public static ElasticSearchHelper search(String serviceName) throws Exception {
    return search().init(serviceName);
  }
}
