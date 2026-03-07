package io.github.ygrip.testara.properties.testenv;

import io.github.ygrip.testara.testenv.EnvironmentModule;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

/**
 * Manages a single Consul container (~128 MB expected memory).
 * Starts in dev mode and seeds KV test data.
 */
public class ConsulModule implements EnvironmentModule {

    private GenericContainer<?> consul;

    @Override
    public void start() {
        consul = new GenericContainer<>("hashicorp/consul:1.15")
            .withExposedPorts(8500)
            .withCommand("agent", "-dev", "-client", "0.0.0.0")
            .waitingFor(Wait.forHttp("/v1/status/leader").forPort(8500).forStatusCode(200));
        consul.start();

        String host = consul.getHost();
        int port = consul.getMappedPort(8500);

        System.setProperty("CONSUL_ENABLED", "true");
        System.setProperty("CONSUL_HOST", host);
        System.setProperty("CONSUL_PORT", String.valueOf(port));
        System.setProperty("CONSUL_TOKEN", "");

        seedConsulData(host, port);
    }

    @Override
    public void stop() {
        if (consul != null && consul.isRunning()) {
            consul.stop();
        }
    }

    private void seedConsulData(String host, int port) {
        String baseUrl = "http://" + host + ":" + port;
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
                .build();
            HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 400) {
                throw new RuntimeException("Consul KV PUT returned " + resp.statusCode());
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to seed Consul KV data", e);
        }
    }
}
