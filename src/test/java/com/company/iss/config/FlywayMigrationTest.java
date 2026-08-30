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
    void freshSchemaMigratesThroughV6WithoutPersistedNotificationSecrets() throws SQLException {
        String url = databaseUrl("fresh_v6");

        Flyway flyway = migrate(url, null);

        assertEquals("6", flyway.info().current().getVersion().getVersion());
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
    void v6BackfillsExistingBookingsAsInitialAndRequiresStage() throws SQLException {
        String url = databaseUrl("v6_booking_stage_upgrade");
        migrate(url, "5");

        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO branches (
                        id, active, created_at, updated_at, version, branch_code,
                        city, province, branch_name, address
                    ) VALUES (
                        1, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 'MNL',
                        'Manila', 'Metro Manila', 'Manila', 'Test Address'
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO applicants (
                        id, active, created_at, updated_at, version, mobile_number,
                        first_name, last_name, email, status, branch_id
                    ) VALUES (
                        1, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, '09170000000',
                        'Legacy', 'Applicant', 'legacy-stage@example.test', 'SCHEDULED', 1
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO schedules (
                        id, active, booked_count, end_time, schedule_date, slot_capacity,
                        start_time, branch_id, created_at, updated_at, version,
                        interview_mode, status
                    ) VALUES (
                        1, TRUE, 1, '10:00:00', '2026-09-01', 2,
                        '09:00:00', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
                        'ONSITE', 'OPEN'
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO bookings (
                        id, applicant_id, booked_date_time, created_at, schedule_id,
                        updated_at, version, booking_reference, status
                    ) VALUES (
                        1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1,
                        CURRENT_TIMESTAMP, 0, 'BK-LEGACY-STAGE', 'BOOKED'
                    )
                    """);
        }

        Flyway flyway = migrate(url, null);

        assertEquals("6", flyway.info().current().getVersion().getVersion());
        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement()) {
            try (var result = statement.executeQuery(
                    "SELECT interview_stage FROM bookings WHERE id = 1"
            )) {
                result.next();
                assertEquals("INITIAL", result.getString(1));
            }
            try (var result = statement.executeQuery("""
                    SELECT IS_NULLABLE, COLUMN_DEFAULT
                    FROM INFORMATION_SCHEMA.COLUMNS
                    WHERE TABLE_NAME = 'BOOKINGS' AND COLUMN_NAME = 'INTERVIEW_STAGE'
                    """)) {
                result.next();
                assertEquals("NO", result.getString("IS_NULLABLE"));
                assertEquals(null, result.getString("COLUMN_DEFAULT"));
            }
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

        assertEquals("6", flyway.info().current().getVersion().getVersion());
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
