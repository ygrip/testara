package io.github.ygrip.testara.properties;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

/**
 * Seeds test data into the locally running Consul and Vault instances
 * configured in application.properties.
 */
public class PropertiesContainerExtension implements BeforeAllCallback {

    private static volatile boolean initialized = false;

    @Override
    public void beforeAll(ExtensionContext context) {
        if (!initialized) {
            synchronized (PropertiesContainerExtension.class) {
                if (!initialized) {
                    seedConsulData();
                    seedVaultData();
                    initialized = true;
                }
            }
        }
    }

    private static void seedConsulData() {
        String consulHost = resolveProperty("CONSUL_HOST", "localhost");
        String consulPort = resolveProperty("CONSUL_PORT", "8500");
        String token = resolveProperty("CONSUL_TOKEN", "local-root-token");
        String baseUrl = "http://" + consulHost + ":" + consulPort;
        HttpClient client = HttpClient.newHttpClient();

        String propertiesBlob = String.join("\n",
            "api.service.quest.host=localhost",
            "api.service.quest.basePath=/api/v1",
            "api.service.quest.port=8080",
            "environment=qa"
        );

        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/kv/config/testara-automation/qa/services"))
                .PUT(HttpRequest.BodyPublishers.ofString(propertiesBlob, StandardCharsets.UTF_8))
                .header("X-Consul-Token", token)
                .build();
            client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            System.err.println("Warning: Failed to seed Consul KV: " + e.getMessage());
        }
    }

    private static void seedVaultData() {
        String vaultAddress = resolveProperty("VAULT_HOST", "http://127.0.0.1:8200");
        String vaultToken = resolveProperty("VAULT_TOKEN", "myroot");
        HttpClient client = HttpClient.newHttpClient();

        try {
            HttpRequest mount = HttpRequest.newBuilder()
                .uri(URI.create(vaultAddress + "/v1/sys/mounts/config"))
                .POST(HttpRequest.BodyPublishers.ofString(
                    "{\"type\":\"kv\",\"options\":{\"version\":\"2\"}}", StandardCharsets.UTF_8))
                .header("X-Vault-Token", vaultToken)
                .header("Content-Type", "application/json")
                .build();
            client.send(mount, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            // mount may already exist
        }

        String secretPayload = "{\"data\":{\"type\":\"qa\",\"environment\":\"qa\",\"region\":\"ap-southeast-1\"}}";
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(vaultAddress + "/v1/config/data/testara-automation/qa"))
                .POST(HttpRequest.BodyPublishers.ofString(secretPayload, StandardCharsets.UTF_8))
                .header("X-Vault-Token", vaultToken)
                .header("Content-Type", "application/json")
                .build();
            client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            System.err.println("Warning: Failed to seed Vault secret: " + e.getMessage());
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
