package com.fooddelivery.payment.migration;

import com.fooddelivery.payment.domain.model.IdempotencyKey;
import com.fooddelivery.payment.domain.model.Payment;
import com.fooddelivery.payment.domain.model.Refund;
import com.fooddelivery.payment.infrastructure.persistence.OutboxEvent;
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
class PaymentFlywayMigrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void upgradesExistingPaymentSchemaAndAddsOutboxRetryMetadata() throws Exception {
        migrateTo("5");
        Flyway latest = flyway();
        latest.migrate();

        assertThat(latest.info().current().getVersion().getVersion()).isEqualTo("6");
        assertThat(columnExists("outbox_events", "next_attempt_at")).isTrue();
        assertThat(columnExists("outbox_events", "dead_lettered")).isTrue();
        assertThat(indexExists("idx_payment_outbox_due")).isTrue();

        assertThatCode(this::validateHibernateSchema)
                .as("Hibernate ddl-auto=validate must succeed on schema after V6")
                .doesNotThrowAnyException();
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

    /**
     * Boots a minimal Hibernate SessionFactory with hbm2ddl.auto=validate against the
     * migrated PostgreSQL schema — mirrors production config-server settings.
     */
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
                    .addAnnotatedClass(Payment.class)
                    .addAnnotatedClass(Refund.class)
                    .addAnnotatedClass(IdempotencyKey.class)
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

    private boolean exists(String sql) throws Exception {
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            return resultSet.next();
        }
    }
}
