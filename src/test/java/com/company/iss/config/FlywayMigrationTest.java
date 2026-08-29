package com.company.iss.config;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FlywayMigrationTest {

    private static final String LOCATIONS = "classpath:db/migration/h2";

    @Test
    void freshSchemaMigratesThroughV5WithoutPersistedNotificationSecrets() throws SQLException {
        String url = databaseUrl("fresh_v5");

        Flyway flyway = migrate(url, null);

        assertEquals("5", flyway.info().current().getVersion().getVersion());
        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement();
             var result = statement.executeQuery("""
                     SELECT COUNT(*)
                     FROM INFORMATION_SCHEMA.COLUMNS
                     WHERE TABLE_NAME = 'NOTIFICATION_SETTINGS'
                       AND COLUMN_NAME IN ('SMTP_PASSWORD', 'SMS_API_KEY')
                     """)) {
            result.next();
            assertEquals(0, result.getInt(1));
        }
    }

    @Test
    void freshMigrationRejectsApplicantWithoutBranch() throws SQLException {
        String url = databaseUrl("fresh");
        migrate(url, null);

        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement()) {
            assertThrows(SQLException.class, () -> statement.executeUpdate(branchlessApplicantInsert("fresh@example.test")));
        }
    }

    @Test
    void unresolvedLegacyApplicantPreventsV2Migration() throws SQLException {
        String url = databaseUrl("legacy");
        migrate(url, "1");

        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement()) {
            statement.executeUpdate(branchlessApplicantInsert("legacy@example.test"));
        }

        assertThrows(FlywayException.class, () -> migrate(url, null));
    }

    @Test
    void v3SchemaUpgradesToSecureAccountLifecycle() throws SQLException {
        String url = databaseUrl("v3_upgrade");
        migrate(url, "3");
        migrate(url, null);

        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement();
             var result = statement.executeQuery("""
                     SELECT COUNT(*)
                     FROM INFORMATION_SCHEMA.TABLES
                     WHERE TABLE_NAME IN ('PASSWORD_RESET_REQUESTS', 'ACCOUNT_SECURITY_AUDITS')
                     """)) {
            result.next();
            org.junit.jupiter.api.Assertions.assertEquals(2, result.getInt(1));
        }
    }

    @Test
    void v4UpgradeRemovesSecretsAndPreservesNonSecretNotificationMetadata() throws SQLException {
        String url = databaseUrl("v4_notification_upgrade");
        migrate(url, "4");

        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO notification_settings (
                        active, email_enabled, sms_enabled, smtp_port, created_at, updated_at,
                        version, smtp_password, sms_api_key, company_name, sms_provider,
                        sms_sender_name, smtp_from_name, smtp_host, smtp_username
                    ) VALUES (
                        TRUE, TRUE, TRUE, 587, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
                        0, 'legacy-password', 'legacy-api-key', 'ISS Notifications', 'legacy-provider',
                        'ISS SMS', 'ISS Mail', 'smtp.example.test', 'mailer@example.test'
                    )
                    """);
        }

        Flyway flyway = migrate(url, null);

        assertEquals("5", flyway.info().current().getVersion().getVersion());
        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement()) {
            try (var result = statement.executeQuery("""
                    SELECT email_enabled, sms_enabled, smtp_port, company_name, sms_provider,
                           sms_sender_name, smtp_from_name, smtp_host, smtp_username
                    FROM notification_settings
                    """)) {
                result.next();
                assertEquals(true, result.getBoolean("email_enabled"));
                assertEquals(false, result.getBoolean("sms_enabled"));
                assertEquals(587, result.getInt("smtp_port"));
                assertEquals("ISS Notifications", result.getString("company_name"));
                assertEquals("legacy-provider", result.getString("sms_provider"));
                assertEquals("ISS SMS", result.getString("sms_sender_name"));
                assertEquals("ISS Mail", result.getString("smtp_from_name"));
                assertEquals("smtp.example.test", result.getString("smtp_host"));
                assertEquals("mailer@example.test", result.getString("smtp_username"));
            }
            try (var result = statement.executeQuery("""
                    SELECT COUNT(*)
                    FROM INFORMATION_SCHEMA.COLUMNS
                    WHERE TABLE_NAME = 'NOTIFICATION_SETTINGS'
                      AND COLUMN_NAME IN ('SMTP_PASSWORD', 'SMS_API_KEY')
                    """)) {
                result.next();
                assertEquals(0, result.getInt(1));
            }
        }
    }

    private Flyway migrate(String url, String target) {
        var configuration = Flyway.configure()
                .dataSource(url, "sa", "")
                .locations(LOCATIONS)
                .cleanDisabled(true)
                .validateOnMigrate(true);
        if (target != null) {
            configuration.target(target);
        }
        Flyway flyway = configuration.load();
        flyway.migrate();
        return flyway;
    }

    private String databaseUrl(String suffix) {
        return "jdbc:h2:mem:flyway_" + suffix + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
    }

    private String branchlessApplicantInsert(String email) {
        return """
                INSERT INTO applicants (
                    active, created_at, updated_at, mobile_number, first_name, last_name, email, status, branch_id
                ) VALUES (
                    TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, '09170000000', 'Legacy', 'Applicant', '%s', 'NEW', NULL
                )
                """.formatted(email);
    }
}
