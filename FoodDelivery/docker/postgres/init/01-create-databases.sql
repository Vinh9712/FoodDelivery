-- Runs once on first initialisation of the Postgres data volume
-- (mounted into /docker-entrypoint-initdb.d). Creates the per-service
-- databases. They are owned by POSTGRES_USER (DB_USER), so no extra grants
-- are needed for the services that connect with the same credentials.
--
-- Flyway in each service then creates/migrates the schema inside its database.
CREATE DATABASE food_order_db;
CREATE DATABASE food_delivery_db;
CREATE DATABASE user_db;
CREATE DATABASE food_payment_db;
