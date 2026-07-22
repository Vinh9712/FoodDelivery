package com.fooddelivery.order.migration;

import com.fooddelivery.order.domain.model.OutboxEvent;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@Testcontainers(disabledWithoutDocker = true)
class OrderFlywayMigrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void upgradesExistingOrderSchemaWithFulfillmentAndOrderedOutboxColumns() throws Exception {
        migrateTo("7");
        UUID firstEvent = UUID.randomUUID();
        UUID secondEvent = UUID.randomUUID();
        UUID aggregateId = UUID.randomUUID();
        insertLegacyOrder(aggregateId);
        insertLegacyOutboxEvent(firstEvent, aggregateId, "2026-07-22T00:00:00Z");
        insertLegacyOutboxEvent(secondEvent, aggregateId, "2026-07-22T00:01:00Z");

        Flyway latest = flyway();
        latest.migrate();

        assertThat(latest.info().current().getVersion().getVersion()).isEqualTo("8");
        assertThat(columnExists("outbox_events", "attempts")).isTrue();
        assertThat(columnExists("outbox_events", "next_attempt_at")).isTrue();
        assertThat(columnExists("outbox_events", "last_error")).isTrue();
        assertThat(columnExists("outbox_events", "dead_lettered")).isTrue();
        assertThat(columnExists("outbox_events", "dead_lettered_at")).isTrue();
        assertThat(indexExists("idx_order_outbox_due")).isTrue();
        assertThat(columnExists("orders", "paid_at")).isTrue();
        assertThat(columnExists("orders", "restaurant_response_deadline")).isTrue();
        assertThat(columnExists("orders", "pickup_address_snapshot")).isTrue();
        assertThat(columnExists("orders", "refund_status")).isTrue();
        assertThat(columnExists("orders", "cancellation_code")).isTrue();
        assertThat(columnExists("orders", "cancellation_reason")).isTrue();
        assertThat(columnExists("orders", "event_sequence")).isTrue();
        assertThat(columnExists("outbox_events", "event_version")).isTrue();
        assertThat(columnExists("outbox_events", "aggregate_sequence")).isTrue();
        assertThat(columnExists("outbox_events", "partition_key")).isTrue();
        assertThat(indexExists("idx_orders_restaurant_status_created")).isTrue();
        assertThat(indexExists("idx_orders_restaurant_deadline")).isTrue();
        assertThat(constraintExists("uq_order_outbox_aggregate_sequence")).isTrue();
        assertThat(outboxSequence(firstEvent)).isEqualTo(1L);
        assertThat(outboxSequence(secondEvent)).isEqualTo(2L);
        assertThat(outboxPartitionKey(firstEvent)).isEqualTo(aggregateId.toString());
        assertThat(orderEventSequence(aggregateId)).isEqualTo(2L);

        assertThatCode(this::validateOutboxEntitySchema)
                .as("Hibernate ddl-auto=validate must succeed for OutboxEvent after V7")
                .doesNotThrowAnyException();
    }

    private void insertLegacyOutboxEvent(UUID id, UUID aggregateId, String createdAt) throws Exception {
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO outbox_events (id, aggregate_type, aggregate_id, event_type, payload, created_at,
                    attempts, dead_lettered) VALUES ('%s', 'Order', '%s', 'OrderCreated', '{}'::jsonb, '%s', 0, FALSE)
                    """.formatted(id, aggregateId, createdAt));
        }
    }

    private void insertLegacyOrder(UUID id) throws Exception {
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO orders (id, customer_id, restaurant_id, status, total_amount, created_at, updated_at)
                    VALUES ('%s', '%s', '%s', 'PENDING', 100.00, NOW(), NOW())
                    """.formatted(id, UUID.randomUUID(), UUID.randomUUID()));
        }
    }

    private void migrateTo(String version) {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion(version))
                .load()
                .migrate();
    }

    private Flyway flyway() {
        return Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load();
    }

    private void validateOutboxEntitySchema() {
        Map<String, Object> settings = new HashMap<>();
        settings.put("hibernate.connection.driver_class", "org.postgresql.Driver");
        settings.put("hibernate.connection.url", POSTGRES.getJdbcUrl());
        settings.put("hibernate.connection.username", POSTGRES.getUsername());
        settings.put("hibernate.connection.password", POSTGRES.getPassword());
        settings.put("hibernate.hbm2ddl.auto", "validate");
        settings.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");

        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySettings(settings)
                .build();
        try {
            SessionFactory sessionFactory = new MetadataSources(registry)
                    .addAnnotatedClass(OutboxEvent.class)
                    .buildMetadata()
                    .buildSessionFactory();
            sessionFactory.close();
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    private boolean columnExists(String table, String column) throws Exception {
        return exists("SELECT 1 FROM information_schema.columns WHERE table_name = '"
                + table + "' AND column_name = '" + column + "'");
    }

    private boolean indexExists(String index) throws Exception {
        return exists("SELECT 1 FROM pg_indexes WHERE indexname = '" + index + "'");
    }

    private boolean constraintExists(String constraint) throws Exception {
        return exists("SELECT 1 FROM pg_constraint WHERE conname = '" + constraint + "'");
    }

    private long outboxSequence(UUID eventId) throws Exception {
        return scalarLong("SELECT aggregate_sequence FROM outbox_events WHERE id = '" + eventId + "'");
    }

    private String outboxPartitionKey(UUID eventId) throws Exception {
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT partition_key FROM outbox_events WHERE id = '" + eventId + "'")) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getString(1);
        }
    }

    private long orderEventSequence(UUID orderId) throws Exception {
        return scalarLong("SELECT event_sequence FROM orders WHERE id = '" + orderId + "'");
    }

    private long scalarLong(String sql) throws Exception {
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getLong(1);
        }
    }

    private boolean exists(String sql) throws Exception {
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            return resultSet.next();
        }
    }
}
