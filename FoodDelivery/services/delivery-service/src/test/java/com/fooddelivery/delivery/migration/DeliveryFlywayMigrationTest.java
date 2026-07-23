package com.fooddelivery.delivery.migration;

import com.fooddelivery.delivery.domain.model.Delivery;
import com.fooddelivery.delivery.domain.model.DeliveryTracking;
import com.fooddelivery.delivery.domain.model.Driver;
import com.fooddelivery.delivery.domain.model.DriverReview;
import com.fooddelivery.delivery.infrastructure.persistence.OutboxEvent;
import com.fooddelivery.delivery.infrastructure.persistence.ProcessedEvent;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@Testcontainers(disabledWithoutDocker = true)
class DeliveryFlywayMigrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void upgradesExistingDeliverySchemaThroughLifecycleMigrations() throws Exception {
        migrateTo("4");
        migrateTo("5");
        assertThat(flyway().info().current().getVersion().getVersion()).isEqualTo("5");
        assertThat(columnExists("deliveries", "version")).isTrue();
        assertThat(indexExists("uq_deliveries_active_driver")).isTrue();
        assertThat(indexExists("idx_drivers_assignment_candidates")).isTrue();

        migrateTo("6");
        assertThat(columnExists("outbox_events", "published_at")).isTrue();
        assertThat(columnExists("outbox_events", "attempts")).isTrue();
        assertThat(columnExists("outbox_events", "next_attempt_at")).isTrue();
        assertThat(columnExists("outbox_events", "last_error")).isTrue();
        assertThat(columnExists("outbox_events", "dead_lettered")).isTrue();
        assertThat(columnExists("outbox_events", "dead_lettered_at")).isTrue();
        assertThat(indexExists("idx_delivery_outbox_due")).isTrue();

        migrateTo("7");
        assertThat(columnExists("deliveries", "assignment_attempts")).isTrue();
        assertThat(columnExists("deliveries", "next_assignment_at")).isTrue();
        assertThat(columnExists("deliveries", "customer_id")).isTrue();
        assertThat(indexExists("idx_deliveries_assignment_due")).isTrue();

        migrateTo("8");
        assertThat(flyway().info().current().getVersion().getVersion()).isEqualTo("8");
        assertThat(columnExists("deliveries", "restaurant_id")).isTrue();
        assertThat(columnExists("deliveries", "schedule_request_hash")).isTrue();
        assertThat(columnExists("deliveries", "schedule_idempotency_key")).isTrue();
        assertThat(columnExists("deliveries", "event_sequence")).isTrue();
        assertThat(columnExists("outbox_events", "event_version")).isTrue();
        assertThat(columnExists("outbox_events", "aggregate_sequence")).isTrue();
        assertThat(columnExists("outbox_events", "partition_key")).isTrue();
        assertThat(indexExists("uq_deliveries_schedule_idempotency_key")).isTrue();
        assertThat(constraintExists("uq_delivery_outbox_aggregate_sequence")).isTrue();

        Flyway latest = flyway();
        latest.migrate();
        assertThat(latest.info().current().getVersion().getVersion()).isEqualTo("11");
        assertThat(indexExists("idx_delivery_outbox_due_sequence")).isTrue();
        assertThat(constraintExists("uq_delivery_outbox_aggregate_sequence")).isTrue();
        assertThat(rowExists(
                "drivers",
                "user_id",
                "019f7567-133e-7bfa-bd16-e788321cec33")).isTrue();

        assertThatCode(this::validateHibernateSchema)
                .as("Hibernate ddl-auto=validate must succeed on schema after V11")
                .doesNotThrowAnyException();
    }

    private boolean constraintExists(String constraint) throws Exception {
        return exists("SELECT 1 FROM pg_constraint WHERE conname = '" + constraint + "'");
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

    private void validateHibernateSchema() {
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
                    .addAnnotatedClass(Driver.class)
                    .addAnnotatedClass(Delivery.class)
                    .addAnnotatedClass(DeliveryTracking.class)
                    .addAnnotatedClass(DriverReview.class)
                    .addAnnotatedClass(OutboxEvent.class)
                    .addAnnotatedClass(ProcessedEvent.class)
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

    private boolean rowExists(String table, String column, String value) throws Exception {
        return exists("SELECT 1 FROM " + table + " WHERE " + column + " = '" + value + "'");
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
