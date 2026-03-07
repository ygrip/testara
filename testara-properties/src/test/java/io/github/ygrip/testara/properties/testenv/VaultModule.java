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
 * Manages a single HashiCorp Vault container (~128 MB expected memory).
 * Starts in dev mode with a known root token,
 * creates a KV v2 engine at {@code config/}, and seeds test secrets.
 */
public class VaultModule implements EnvironmentModule {

    private static final String ROOT_TOKEN = "myroot";

    private GenericContainer<?> vault;

    @Override
    public void start() {
        vault = new GenericContainer<>("hashicorp/vault:1.15")
            .withExposedPorts(8200)
            .withEnv("VAULT_DEV_ROOT_TOKEN_ID", ROOT_TOKEN)
            .withEnv("VAULT_DEV_LISTEN_ADDRESS", "0.0.0.0:8200")
            .withCommand("server", "-dev")
            .waitingFor(Wait.forHttp("/v1/sys/health").forPort(8200).forStatusCode(200));
        vault.start();

        String address = "http://" + vault.getHost() + ":" + vault.getMappedPort(8200);

        System.setProperty("VAULT_ENABLED", "true");
        System.setProperty("VAULT_HOST", address);
        System.setProperty("VAULT_TOKEN", ROOT_TOKEN);

        seedVaultData(address);
    }

    @Override
    public void stop() {
        if (vault != null && vault.isRunning()) {
            vault.stop();
        }
    }

    private void seedVaultData(String vaultAddress) {
        HttpClient client = HttpClient.newHttpClient();

        // Create KV v2 engine at mount point "config/"
        try {
            HttpRequest mount = HttpRequest.newBuilder()
                .uri(URI.create(vaultAddress + "/v1/sys/mounts/config"))
                .POST(HttpRequest.BodyPublishers.ofString(
                    "{\"type\":\"kv\",\"options\":{\"version\":\"2\"}}", StandardCharsets.UTF_8))
                .header("X-Vault-Token", ROOT_TOKEN)
                .header("Content-Type", "application/json")
                .build();
            client.send(mount, HttpResponse.BodyHandlers.ofString());
        } catch (Exception ignored) {
            // mount may already exist
        }

        // Seed secrets at config/data/testara-automation/qa
        String secretPayload = "{\"data\":{\"type\":\"qa\",\"environment\":\"qa\",\"region\":\"ap-southeast-1\"}}";
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(vaultAddress + "/v1/config/data/testara-automation/qa"))
                .POST(HttpRequest.BodyPublishers.ofString(secretPayload, StandardCharsets.UTF_8))
                .header("X-Vault-Token", ROOT_TOKEN)
                .header("Content-Type", "application/json")
                .build();
            HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 400) {
                throw new RuntimeException("Vault secret PUT returned " + resp.statusCode() + ": " + resp.body());
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to seed Vault secrets", e);
        }
    }
}
