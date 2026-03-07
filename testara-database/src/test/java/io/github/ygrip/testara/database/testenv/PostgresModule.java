package io.github.ygrip.testara.database.testenv;

import io.github.ygrip.testara.testenv.EnvironmentModule;
import org.testcontainers.containers.PostgreSQLContainer;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Manages a single PostgreSQL container (~256 MB expected memory).
 * Initialises the schema and seed rows via init-postgres.sql.
 */
public class PostgresModule implements EnvironmentModule {

    private PostgreSQLContainer<?> postgres;

    @Override
    public void start() {
        postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("x_message_template")
            .withUsername("testuser")
            .withPassword("testpass")
            .withInitScript("init-postgres.sql");
        postgres.start();

        // SqlModel.constructUri() expects a query-string in the URI;
        // embed user/password so the framework can parse it directly.
        String baseUrl = postgres.getJdbcUrl();
        String separator = baseUrl.contains("?") ? "&" : "?";
        String jdbcUrl = baseUrl + separator
            + "user=" + URLEncoder.encode(postgres.getUsername(), StandardCharsets.UTF_8)
            + "&password=" + URLEncoder.encode(postgres.getPassword(), StandardCharsets.UTF_8);

        System.setProperty("XMESSAGE_DB_URI", jdbcUrl);
        System.setProperty("XMESSAGE_DB_USERNAME", postgres.getUsername());
        System.setProperty("XMESSAGE_DB_PASSWORD", postgres.getPassword());
        System.setProperty("CONSUL_ENABLED", "false");
        System.setProperty("VAULT_ENABLED", "false");
    }

    @Override
    public void stop() {
        if (postgres != null && postgres.isRunning()) {
            postgres.stop();
        }
    }

    public String getJdbcUrl() {
        return postgres.getJdbcUrl();
    }
}
