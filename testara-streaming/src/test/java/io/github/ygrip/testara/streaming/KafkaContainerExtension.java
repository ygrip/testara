package io.github.ygrip.testara.streaming;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.kafka.KafkaContainer;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

public class KafkaContainerExtension implements BeforeAllCallback {

    private static final KafkaContainer KAFKA;

    static {
        KAFKA = new KafkaContainer("apache/kafka:3.8.0");
        KAFKA.start();

        String bootstrapServers = KAFKA.getBootstrapServers();
        System.setProperty("KAFKA_SERVERS", bootstrapServers);
        System.setProperty("CONSUL_ENABLED", "false");
        System.setProperty("VAULT_ENABLED", "false");

        createTopicsAndSeedData(bootstrapServers);
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        // containers started in static initializer
    }

    private static void createTopicsAndSeedData(String bootstrapServers) {
        Properties adminProps = new Properties();
        adminProps.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        try (AdminClient admin = AdminClient.create(adminProps)) {
            List<NewTopic> topics = List.of(
                new NewTopic("io.github.ygrip.aggregate.notification.inbox.save", 1, (short) 1),
                new NewTopic("io.github.ygrip.quest.user.action.event", 1, (short) 1)
            );
            admin.createTopics(topics).all().get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create Kafka topics", e);
        }

        Properties producerProps = new Properties();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps)) {
            String topic = "io.github.ygrip.aggregate.notification.inbox.save";
            String msg1 = "{\"categoryCode\":\"order\",\"subCategoryCode\":\"push_retail\",\"memberId\":\"user1@test.com\"}";
            String msg2 = "{\"categoryCode\":\"order\",\"subCategoryCode\":\"push_retail\",\"memberId\":\"user2@test.com\"}";
            String msg3 = "{\"categoryCode\":\"promo\",\"subCategoryCode\":\"banner\",\"memberId\":\"user3@test.com\"}";

            producer.send(new ProducerRecord<>(topic, "key1", msg1)).get(10, TimeUnit.SECONDS);
            producer.send(new ProducerRecord<>(topic, "key2", msg2)).get(10, TimeUnit.SECONDS);
            producer.send(new ProducerRecord<>(topic, "key3", msg3)).get(10, TimeUnit.SECONDS);
            producer.flush();
        } catch (Exception e) {
            throw new RuntimeException("Failed to seed Kafka data", e);
        }
    }
}
