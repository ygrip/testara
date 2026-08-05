package io.github.ygrip.testara.elastic;

import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.CountResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.ygrip.testara.core.BaseTests;
import io.github.ygrip.testara.core.TestWith;
import io.github.ygrip.testara.core.context.TestFramework;
import io.github.ygrip.testara.elastic.testenv.ElasticModule;
import io.github.ygrip.testara.testenv.TestEnvironmentExtension;
import io.github.ygrip.testara.testenv.WithModules;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.core.Is.is;

@ExtendWith(TestEnvironmentExtension.class)
@WithModules({ElasticModule.class})
@Tag("elastic")
@TestWith(properties = {"classpath:application.properties", "classpath:configuration.properties"})
public class ElasticSearchTests extends BaseTests {

  @Test
  public void connectToElasticSearch() throws Throwable {
    ElasticSearchHelper elasticSearch = TestFramework.context().get(ElasticSearchHelper.class);
    boolean connected = elasticSearch.init("agp").isConnected();
    assertThat(connected, equalTo(true));
  }

  @Test
  public void getElasticSearchIndexes() throws Throwable {
    ElasticSearchHelper elasticSearch = TestFramework.context().get(ElasticSearchHelper.class);
    Set<String> indexes = elasticSearch.init("agp").getIndexes();
    assertThat(indexes, is(notNullValue()));
    assertThat(indexes.isEmpty(), equalTo(false));
  }

  @Test
  public void convertHashMapToListOfSorBuilder() throws Throwable {
    Map<String, String> input = new HashMap<>();
    input.put("memberId", "desc");
    input.put("created", "asc");

    ElasticSearchHelper elasticSearch = TestFramework.context().get(ElasticSearchHelper.class);
    List<SortOptions> actual = elasticSearch.parseSortBuilder(input);
    assertThat(actual, is(notNullValue()));
    assertThat(actual.size(), equalTo(2));

    Map<String, SortOrder> actualByField = new HashMap<>();
    for (SortOptions option : actual) {
      actualByField.put(option.field().field(), option.field().order());
    }
    assertThat(actualByField.get("memberId"), equalTo(SortOrder.Desc));
    assertThat(actualByField.get("created"), equalTo(SortOrder.Asc));
  }

  @Test
  public void queryElasticSearch() throws Throwable {
    ElasticSearchHelper elasticSearch = TestFramework.context().get(ElasticSearchHelper.class);
    SearchResponse<ObjectNode> indexes =
        elasticSearch.init("agp").search("*:*", new String[] {"notification_inbox_notification_inboxes"}, null);
    assertThat(indexes, is(notNullValue()));
    assertThat(indexes.hits().hits(), is(notNullValue()));
  }

  @Test
  public void queryElasticSearchAsData() throws Throwable {
    ElasticSearchHelper elasticSearch = TestFramework.context().get(ElasticSearchHelper.class);
    List<LinkedHashMap<String, Object>> data = elasticSearch.init("agp")
        .getSearchDataAs("*:*",
            new String[] {"notification_inbox_notification_inboxes"},
            10,
            null,
            new TypeReference<LinkedHashMap<String, Object>>() {
            });
    assertThat(data, is(notNullValue()));
    assertThat(data.size(), lessThanOrEqualTo(10));
  }

  @Test
  public void queryElasticSearchAsDataWithSortOption() throws Throwable {
    Map<String, String> input = new HashMap<>();
    input.put("created", "desc");

    ElasticSearchHelper elasticSearch = TestFramework.context().get(ElasticSearchHelper.class);
    List<SortOptions> sorts = elasticSearch.parseSortBuilder(input);
    List<LinkedHashMap<String, Object>> data = elasticSearch.init("agp")
        .getSearchDataAs("*:*",
            new String[] {"notification_inbox_notification_inboxes"},
            10,
            sorts,
            new TypeReference<LinkedHashMap<String, Object>>() {
            });
    assertThat(data, is(notNullValue()));
    assertThat(data.size(), lessThanOrEqualTo(10));
  }

  @Test
  public void countInIndexElasticSearch() throws Throwable {
    ElasticSearchHelper elasticSearch = TestFramework.context().get(ElasticSearchHelper.class);
    CountResponse count =
        elasticSearch.init("agp").count("*:*", new String[] {"notification_inbox_notification_inboxes"});
    assertThat(count, is(notNullValue()));
    assertThat(count.count(), greaterThan(0L));
  }
}
