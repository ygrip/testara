package io.github.ygrip.testara.testenv;

/**
 * Contract for a single infrastructure service (Kafka, Postgres, Elasticsearch, etc.).
 * Each implementation manages exactly one container.
 */
public interface EnvironmentModule {

    void start();

    default void stop() {}
}
