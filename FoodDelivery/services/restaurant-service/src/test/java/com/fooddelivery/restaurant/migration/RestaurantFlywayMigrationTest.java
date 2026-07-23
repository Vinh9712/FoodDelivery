package com.fooddelivery.restaurant.migration;

import com.fooddelivery.restaurant.domain.MenuCategory;
import com.fooddelivery.restaurant.domain.MenuItem;
import com.fooddelivery.restaurant.domain.Restaurant;
import com.fooddelivery.restaurant.domain.RestaurantReview;
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
class RestaurantFlywayMigrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void upgradesExistingSchemaThroughV6AndMatchesEntities() throws Exception {
        migrateTo("5");
        seedExistingMenuData();

        Flyway latest = flyway();
        latest.migrate();

        assertThat(latest.info().current().getVersion().getVersion()).isEqualTo("6");
        assertThat(value("SELECT display_order FROM menu_categories WHERE name = 'Category'"))
                .isEqualTo("7");
        assertThat(value("SELECT is_active::text FROM menu_categories WHERE name = 'Category'"))
                .isEqualTo("true");
        assertThat(value("SELECT preparation_time_min FROM menu_items WHERE name = 'Item'"))
                .isEqualTo("20");
        assertThat(value("SELECT restaurant_id FROM menu_items WHERE name = 'Item'"))
                .isNotBlank();

        assertThatCode(this::validateHibernateSchema)
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

    private void seedExistingMenuData() throws Exception {
        execute("""
                INSERT INTO restaurants (id, owner_id, name, phone, address_line, district, city,
                    status, open_time, close_time, estimated_delivery_time_min)
                VALUES ('00000000-0000-0000-0000-000000000001',
                    '00000000-0000-0000-0000-000000000002', 'Restaurant', '0900000000',
                    'Address', 'District', 'City', 'ACTIVE', '08:00', '22:00', 30);
                INSERT INTO menu_categories (id, restaurant_id, name, sort_order, is_available)
                VALUES ('00000000-0000-0000-0000-000000000003',
                    '00000000-0000-0000-0000-000000000001', 'Category', 7, TRUE);
                INSERT INTO menu_items (id, category_id, name, price, prep_time_minutes)
                VALUES ('00000000-0000-0000-0000-000000000004',
                    '00000000-0000-0000-0000-000000000003', 'Item', 10000, 20);
                """);
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
                    .addAnnotatedClass(Restaurant.class)
                    .addAnnotatedClass(MenuCategory.class)
                    .addAnnotatedClass(MenuItem.class)
                    .addAnnotatedClass(RestaurantReview.class)
                    .buildMetadata()
                    .buildSessionFactory();
            sessionFactory.close();
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    private void execute(String sql) throws Exception {
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private String value(String sql) throws Exception {
        try (Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getString(1);
        }
    }
}
