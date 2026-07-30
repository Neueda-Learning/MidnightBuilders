package com.example.demo.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlywayMigrationTest {

    @Test
    void migrate_createsRequiredTablesAndConstraints() throws Exception {
        String dbName = "flyway_it_" + System.nanoTime();
        String url = "jdbc:h2:mem:" + dbName + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";

        Flyway flyway = Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .load();

        int executed = flyway.migrate().migrationsExecuted;
        assertEquals(5, executed, "Flyway should execute V1 through V5 migrations");

        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            assertTrue(tableExists(connection, "PAYMENTS"));
            assertTrue(tableExists(connection, "PAYMENT_STATUS_HISTORY"));
            assertTrue(tableExists(connection, "ACCOUNTS"));

            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT COUNT(*) FROM accounts WHERE account_number = ?")) {
                ps.setString(1, "ACC-SOURCE-01");
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    assertEquals(1, rs.getInt(1), "Expected seeded payer account ACC-SOURCE-01");
                }
            }

            insertPayment(connection, "pay-1", "idem-1");
            insertHistory(connection, "his-1", "pay-1");

            // Verify FK works by checking inserted history record count.
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT COUNT(*) FROM payment_status_history WHERE payment_id = ?")) {
                ps.setString(1, "pay-1");
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    assertEquals(1, rs.getInt(1));
                }
            }

            // Verify UNIQUE constraint on idempotency_key.
            boolean uniqueViolation = false;
            try {
                insertPayment(connection, "pay-2", "idem-1");
            } catch (SQLException ex) {
                uniqueViolation = true;
            }
            assertTrue(uniqueViolation, "Expected UNIQUE constraint violation for duplicated idempotency_key");
        }
    }

    private boolean tableExists(Connection connection, String tableName) throws SQLException {
        try (ResultSet rs = connection.getMetaData().getTables(null, null, tableName, null)) {
            return rs.next();
        }
    }

    private void insertPayment(Connection connection, String id, String idempotencyKey) throws SQLException {
        String sql = "INSERT INTO payments (id, source_account, destination_account, amount, currency, reference, status, "
                + "idempotency_key, request_fingerprint, error_code, error_message, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, "ACC001");
            ps.setString(3, "ACC002");
            ps.setBigDecimal(4, new java.math.BigDecimal("100.00"));
            ps.setString(5, "USD");
            ps.setString(6, "test-ref");
            ps.setString(7, "CREATED");
            ps.setString(8, idempotencyKey);
            ps.setString(9, "fp-" + id);
            ps.setString(10, null);
            ps.setString(11, null);
            ps.executeUpdate();
        }
    }

    private void insertHistory(Connection connection, String id, String paymentId) throws SQLException {
        String sql = "INSERT INTO payment_status_history (id, payment_id, from_status, to_status, triggered_by, error_code, notes, changed_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, paymentId);
            ps.setString(3, null);
            ps.setString(4, "CREATED");
            ps.setString(5, "USER");
            ps.setString(6, null);
            ps.setString(7, "created");
            ps.executeUpdate();
        }
    }
}
