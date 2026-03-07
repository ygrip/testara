package io.github.ygrip.testara.elastic;

import com.fasterxml.jackson.core.type.TypeReference;
import io.github.ygrip.testara.core.BaseTests;
import io.github.ygrip.testara.core.TestWith;
import io.github.ygrip.testara.core.context.TestFramework;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.core.CountResponse;
import org.elasticsearch.search.sort.SortBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
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
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.core.Is.is;

@ExtendWith(ElasticContainerExtension.class)
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
    List<SortBuilder<?>> expected = new ArrayList<>();
    expected.add(SortBuilders.fieldSort("memberId").order(SortOrder.DESC));
    expected.add(SortBuilders.fieldSort("created").order(SortOrder.ASC));

    Map<String, String> input = new HashMap<>();
    input.put("memberId", "desc");
    input.put("created", "asc");

    ElasticSearchHelper elasticSearch = TestFramework.context().get(ElasticSearchHelper.class);
    List<SortBuilder<?>> actual = elasticSearch.parseSortBuilder(input);
    assertThat(actual, is(notNullValue()));
    assertThat(actual, containsInAnyOrder(expected.toArray()));
  }

  @Test
  public void queryElasticSearch() throws Throwable {
    ElasticSearchHelper elasticSearch = TestFramework.context().get(ElasticSearchHelper.class);
    SearchResponse indexes =
        elasticSearch.init("agp").search("*:*", new String[] {"notification_inbox_notification_inboxes"}, null);
    assertThat(indexes, is(notNullValue()));
    assertThat(indexes.getHits().getHits(), is(notNullValue()));
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
    List<SortBuilder<?>> sorts = elasticSearch.parseSortBuilder(input);
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
    assertThat(count.getCount(), greaterThan(0L));
  }
}
