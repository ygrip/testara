package io.github.ygrip.testara.elastic;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Seeds test data into the locally running Elasticsearch instance.
 * Uses ELASTIC_HOSTS system/env property, or defaults to localhost:9200.
 */
public class ElasticContainerExtension implements BeforeAllCallback {

    private static volatile boolean initialized = false;

    @Override
    public void beforeAll(ExtensionContext context) {
        if (!initialized) {
            synchronized (ElasticContainerExtension.class) {
                if (!initialized) {
                    System.setProperty("CONSUL_ENABLED", "false");
                    System.setProperty("VAULT_ENABLED", "false");
                    seedElasticData();
                    initialized = true;
                }
            }
        }
    }

    private static void seedElasticData() {
        String esHosts = resolveProperty("ELASTIC_HOSTS", "localhost:9200");
        String baseUrl = "http://" + esHosts;
        HttpClient client = HttpClient.newHttpClient();

        try {
            HttpRequest checkIndex = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/notification_inbox_notification_inboxes"))
                .GET()
                .build();
            HttpResponse<String> resp = client.send(checkIndex, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                return;
            }
        } catch (Exception ignored) {
        }

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
            System.err.println("Warning: Failed to seed Elasticsearch data: " + e.getMessage());
        }
    }

    private static String resolveProperty(String key, String defaultValue) {
        String val = System.getenv(key);
        if (val != null && !val.isBlank()) return val;
        val = System.getProperty(key);
        if (val != null && !val.isBlank()) return val;
        return defaultValue;
    }
}
