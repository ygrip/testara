package io.github.ygrip.testara.database;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.PostgreSQLContainer;

public class DatabaseContainerExtension implements BeforeAllCallback {

    private static final PostgreSQLContainer<?> POSTGRES;
    private static final MongoDBContainer MONGO;

    static {
        POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("x_message_template")
            .withUsername("testuser")
            .withPassword("testpass")
            .withInitScript("init-postgres.sql");
        POSTGRES.start();

        MONGO = new MongoDBContainer("mongo:7.0");
        MONGO.start();

        System.setProperty("XMESSAGE_DB_URI", POSTGRES.getJdbcUrl());
        System.setProperty("XMESSAGE_DB_USERNAME", POSTGRES.getUsername());
        System.setProperty("XMESSAGE_DB_PASSWORD", POSTGRES.getPassword());
        System.setProperty("AGP_MONGO_DB_URI", MONGO.getConnectionString());
        System.setProperty("AGP_MONGO_DB_NAME", "aggreagate_platform");
        System.setProperty("CONSUL_ENABLED", "false");
        System.setProperty("VAULT_ENABLED", "false");

        seedMongoData();
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        // containers started in static initializer
    }

    private static void seedMongoData() {
        try {
            MONGO.execInContainer("mongosh", "--eval",
                "db = db.getSiblingDB('aggreagate_platform');" +
                "db.createCollection('notification_inbox_notification_inboxes');" +
                "db.notification_inbox_notification_inboxes.insertMany([" +
                "  {memberId: 'user1@test.com', title: 'Notification 1', status: 'read'}," +
                "  {memberId: 'user2@test.com', title: 'Notification 2', status: 'unread'}," +
                "  {memberId: 'user3@test.com', title: 'Notification 3', status: 'read'}" +
                "]);");
        } catch (Exception e) {
            throw new RuntimeException("Failed to seed MongoDB data", e);
        }
    }
}
