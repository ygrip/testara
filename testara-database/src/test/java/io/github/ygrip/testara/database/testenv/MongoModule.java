package io.github.ygrip.testara.database.testenv;

import io.github.ygrip.testara.testenv.EnvironmentModule;
import org.testcontainers.containers.MongoDBContainer;

/**
 * Manages a single MongoDB container (~256 MB expected memory).
 * Seeds an initial collection with sample documents.
 */
public class MongoModule implements EnvironmentModule {

    private MongoDBContainer mongo;

    @Override
    public void start() {
        mongo = new MongoDBContainer("mongo:7.0");
        mongo.start();

        System.setProperty("AGP_MONGO_DB_URI", mongo.getConnectionString());
        System.setProperty("AGP_MONGO_DB_NAME", "aggreagate_platform");
        System.setProperty("CONSUL_ENABLED", "false");
        System.setProperty("VAULT_ENABLED", "false");

        seedData();
    }

    @Override
    public void stop() {
        if (mongo != null && mongo.isRunning()) {
            mongo.stop();
        }
    }

    public String getConnectionString() {
        return mongo.getConnectionString();
    }

    private void seedData() {
        try {
            mongo.execInContainer("mongosh", "--eval",
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
