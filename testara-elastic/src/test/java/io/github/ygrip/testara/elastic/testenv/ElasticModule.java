package io.github.ygrip.testara.elastic.testenv;

import io.github.ygrip.testara.testenv.EnvironmentModule;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Manages a single Elasticsearch 8.15 container (~512 MB expected memory).
 * Heap is capped at 256 MB via ES_JAVA_OPTS.
 * Seeds test index and documents after startup.
 */
public class ElasticModule implements EnvironmentModule {

    private ElasticsearchContainer elasticsearch;

    @Override
    public void start() {
        elasticsearch = new ElasticsearchContainer(
            "docker.elastic.co/elasticsearch/elasticsearch:8.15.0")
            .withEnv("ES_JAVA_OPTS", "-Xms256m -Xmx256m")
            .withEnv("discovery.type", "single-node")
            .withEnv("xpack.security.enabled", "false");
        elasticsearch.start();

        String httpHost = elasticsearch.getHttpHostAddress();

        System.setProperty("ELASTIC_HOSTS", httpHost);
        System.setProperty("CONSUL_ENABLED", "false");
        System.setProperty("VAULT_ENABLED", "false");

        seedElasticData(httpHost);
    }

    @Override
    public void stop() {
        if (elasticsearch != null && elasticsearch.isRunning()) {
            elasticsearch.stop();
        }
    }

    public String getHttpHostAddress() {
        return elasticsearch.getHttpHostAddress();
    }

    private void seedElasticData(String esHosts) {
        String baseUrl = "http://" + esHosts;
        HttpClient client = HttpClient.newHttpClient();

        try {
            HttpRequest createIndex = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/notification_inbox_notification_inboxes"))
                .PUT(HttpRequest.BodyPublishers.ofString(
                    "{\"settings\":{\"number_of_shards\":1,\"number_of_replicas\":0}}"))
                .header("Content-Type", "application/json")
                .build();
            client.send(createIndex, HttpResponse.BodyHandlers.ofString());

            String[] docs = {
                "{\"memberId\":\"user1@test.com\",\"title\":\"Notification 1\",\"created\":\"2024-01-01T00:00:00\"}",
                "{\"memberId\":\"user2@test.com\",\"title\":\"Notification 2\",\"created\":\"2024-01-02T00:00:00\"}",
                "{\"memberId\":\"user3@test.com\",\"title\":\"Notification 3\",\"created\":\"2024-01-03T00:00:00\"}",
                "{\"memberId\":\"user4@test.com\",\"title\":\"Notification 4\",\"created\":\"2024-01-04T00:00:00\"}",
                "{\"memberId\":\"user5@test.com\",\"title\":\"Notification 5\",\"created\":\"2024-01-05T00:00:00\"}"
            };
            for (String doc : docs) {
                HttpRequest addDoc = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/notification_inbox_notification_inboxes/_doc"))
                    .POST(HttpRequest.BodyPublishers.ofString(doc))
                    .header("Content-Type", "application/json")
                    .build();
                client.send(addDoc, HttpResponse.BodyHandlers.ofString());
            }

            HttpRequest refresh = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/notification_inbox_notification_inboxes/_refresh"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
            client.send(refresh, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new RuntimeException("Failed to seed Elasticsearch data", e);
        }
    }
}
