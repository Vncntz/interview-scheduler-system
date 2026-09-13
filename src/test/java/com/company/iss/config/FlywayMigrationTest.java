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
    void freshSchemaMigratesThroughV10WithNullableOfferResponseDeadline() throws SQLException {
        String url = databaseUrl("fresh_v10");

        Flyway flyway = migrate(url, null);

        assertEquals("10", flyway.info().current().getVersion().getVersion());
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
        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement();
             var result = statement.executeQuery("""
                     SELECT IS_NULLABLE
                     FROM INFORMATION_SCHEMA.COLUMNS
                     WHERE TABLE_NAME = 'HIRING_DECISIONS' AND COLUMN_NAME = 'RESPONSE_DUE_AT'
                     """)) {
            result.next();
            assertEquals("YES", result.getString(1));
        }
    }

    @Test
    void v10PreservesExistingV9OfferWithNullResponseDeadline() throws SQLException {
        String url = databaseUrl("v10_offer_deadline_upgrade");
        migrate(url, "9");

        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO branches (
                        id, active, created_at, updated_at, version, branch_code,
                        city, province, branch_name, address
                    ) VALUES (
                        91, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 'V10',
                        'Manila', 'Metro Manila', 'V10 Branch', 'Test Address'
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO users (
                        id, active, failed_login_attempts, must_change_password, created_at,
                        updated_at, version, email, full_name, password_hash, role
                    ) VALUES (
                        91, TRUE, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
                        'v10-actor@example.test', 'V10 Actor', 'test-only-hash', 'ADMIN'
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO position_openings (
                        id, active, applied_count, hired_count, interviewed_count, passed_count,
                        required_headcount, created_at, updated_at, version, title, work_location,
                        employment_type, status
                    ) VALUES (
                        91, TRUE, 1, 0, 1, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
                        'V10 Position', 'Manila', 'FULL_TIME', 'OPEN'
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO applicants (
                        id, active, branch_id, position_opening_id, created_at, updated_at, version,
                        mobile_number, first_name, last_name, email, status
                    ) VALUES (
                        91, TRUE, 91, 91, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
                        '09170000000', 'Legacy', 'Offer', 'v10-offer@example.test', 'OFFERED'
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO bookings (
                        id, applicant_id, booked_date_time, created_at, updated_at, version,
                        booking_reference, status, interview_stage, reminder_generation
                    ) VALUES (
                        91, 91, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
                        'BK-V10-OFFER', 'PASSED', 'INITIAL', 0
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO interview_evaluations (
                        id, attitude_score, communication_score, technical_score, applicant_id,
                        booking_id, created_at, evaluation_date, updated_at, version, result
                    ) VALUES (
                        91, 8, 8, 8, 91, 91, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
                        CURRENT_TIMESTAMP, 0, 'PASS'
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO hiring_decisions (
                        id, applicant_id, evaluation_id, offered_at, offered_by_id, position_id,
                        created_at, updated_at, version, status
                    ) VALUES (
                        91, 91, 91, CURRENT_TIMESTAMP, 91, 91,
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 'OFFERED'
                    )
                    """);
        }

        Flyway flyway = migrate(url, null);

        assertEquals("10", flyway.info().current().getVersion().getVersion());
        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement();
             var result = statement.executeQuery("""
                     SELECT status, response_due_at
                     FROM hiring_decisions
                     WHERE id = 91
                     """)) {
            result.next();
            assertEquals("OFFERED", result.getString("status"));
            assertEquals(null, result.getObject("response_due_at"));
        }
    }

    @Test
    void v9AddsLifecycleHistoryAndPreservesLegacyReschedulesWithoutInventedSnapshots() throws SQLException {
        String url = databaseUrl("v9_lifecycle_upgrade");
        migrate(url, "8");

        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO branches (
                        id, active, created_at, updated_at, version, branch_code,
                        city, province, branch_name, address
                    ) VALUES (
                        1, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 'LIFE',
                        'Manila', 'Metro Manila', 'Lifecycle Branch', 'Test Address'
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO users (
                        id, active, failed_login_attempts, must_change_password, created_at,
                        updated_at, version, email, full_name, password_hash, role, branch_id
                    ) VALUES (
                        1, TRUE, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
                        'lifecycle-actor@example.test', 'Lifecycle Actor', 'test-only-hash', 'RECRUITER', 1
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO applicants (
                        id, active, created_at, updated_at, version, mobile_number,
                        first_name, last_name, email, status, branch_id
                    ) VALUES (
                        1, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, '09170000000',
                        'Lifecycle', 'Applicant', 'lifecycle-applicant@example.test', 'SCHEDULED', 1
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO schedules (
                        id, active, booked_count, end_time, schedule_date, slot_capacity,
                        start_time, branch_id, recruiter_id, created_at, updated_at, version,
                        interview_mode, status
                    ) VALUES
                        (1, TRUE, 0, '10:00:00', '2026-09-10', 2, '09:00:00', 1, 1,
                         CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 'ONLINE', 'OPEN'),
                        (2, TRUE, 1, '12:00:00', '2026-09-11', 2, '11:00:00', 1, 1,
                         CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 'ONSITE', 'OPEN')
                    """);
            statement.executeUpdate("""
                    INSERT INTO bookings (
                        id, applicant_id, booked_date_time, created_at, recruiter_id, schedule_id,
                        updated_at, version, booking_reference, status, interview_stage, reminder_generation
                    ) VALUES (
                        1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1, 2,
                        CURRENT_TIMESTAMP, 0, 'BK-V9-LIFECYCLE', 'BOOKED', 'INITIAL', 0
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO booking_reschedule_history (
                        id, actor_id, booking_id, created_at, destination_schedule_id,
                        rescheduled_at, source_schedule_id, updated_at, version, reason
                    ) VALUES (
                        1, 1, 1, CURRENT_TIMESTAMP, 2, CURRENT_TIMESTAMP, 1,
                        CURRENT_TIMESTAMP, 0, 'Legacy reason'
                    )
                    """);
        }

        Flyway flyway = migrate(url, null);

        assertEquals("10", flyway.info().current().getVersion().getVersion());
        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement()) {
            try (var result = statement.executeQuery("""
                    SELECT snapshot_version, source_appointment_date, destination_appointment_date
                    FROM booking_reschedule_history WHERE id = 1
                    """)) {
                result.next();
                assertEquals(null, result.getObject("snapshot_version"));
                assertEquals(null, result.getObject("source_appointment_date"));
                assertEquals(null, result.getObject("destination_appointment_date"));
            }
            statement.executeUpdate(lifecycleHistoryInsert(1, "BOOKING_CREATED", "BOOKED", null));
            assertThrows(SQLException.class,
                    () -> statement.executeUpdate(lifecycleHistoryInsert(2, "BOOKING_CREATED", "BOOKED", null)));
            assertThrows(SQLException.class,
                    () -> statement.executeUpdate(lifecycleHistoryInsert(3, "BOOKING_CONFIRMED", "CONFIRMED", null)));
            assertThrows(SQLException.class,
                    () -> statement.executeUpdate(lifecycleHistoryInsert(4, "UNKNOWN_ACTION", "BOOKED", null)));
            assertThrows(SQLException.class, () -> statement.executeUpdate("DELETE FROM bookings WHERE id = 1"));
        }
    }

    @Test
    void v7BackfillsSmtpProviderSecurityAndFromAddressWithoutAddingSecrets() throws SQLException {
        String url = databaseUrl("v7_smtp_upgrade");
        migrate(url, "6");

        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO notification_settings (
                        active, email_enabled, sms_enabled, smtp_port, created_at, updated_at,
                        version, company_name, smtp_from_name, smtp_host, smtp_username
                    ) VALUES (
                        TRUE, TRUE, FALSE, 587, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
                        0, 'ISS Notifications', 'Interview Scheduler',
                        'SMTP.GMAIL.COM', 'mailer@example.test'
                    )
                    """);
        }

        Flyway flyway = migrate(url, null);

        assertEquals("10", flyway.info().current().getVersion().getVersion());
        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement()) {
            try (var result = statement.executeQuery("""
                    SELECT smtp_provider, smtp_security, smtp_from_address
                    FROM notification_settings
                    """)) {
                result.next();
                assertEquals("GMAIL", result.getString("smtp_provider"));
                assertEquals("STARTTLS", result.getString("smtp_security"));
                assertEquals("mailer@example.test", result.getString("smtp_from_address"));
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
            try (var result = statement.executeQuery("""
                    SELECT COUNT(*)
                    FROM INFORMATION_SCHEMA.TABLES
                    WHERE TABLE_NAME = 'NOTIFICATION_SETTINGS_AUDITS'
                    """)) {
                result.next();
                assertEquals(1, result.getInt(1));
            }
        }
    }

    @Test
    void v8BackfillsReminderGenerationAndEnforcesDeliveryIdentityAndForeignKey() throws SQLException {
        String url = databaseUrl("v8_reminder_upgrade");
        migrate(url, "7");

        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO branches (
                        id, active, created_at, updated_at, version, branch_code,
                        city, province, branch_name, address
                    ) VALUES (
                        1, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 'REM',
                        'Manila', 'Metro Manila', 'Reminder Branch', 'Test Address'
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO applicants (
                        id, active, created_at, updated_at, version, mobile_number,
                        first_name, last_name, email, status, branch_id
                    ) VALUES (
                        1, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, '09170000000',
                        'Reminder', 'Applicant', 'v8-reminder@example.test', 'SCHEDULED', 1
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO schedules (
                        id, active, booked_count, end_time, schedule_date, slot_capacity,
                        start_time, branch_id, created_at, updated_at, version,
                        interview_mode, status
                    ) VALUES (
                        1, TRUE, 1, '10:00:00', '2026-09-02', 2,
                        '09:00:00', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
                        'ONLINE', 'OPEN'
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO bookings (
                        id, applicant_id, booked_date_time, created_at, schedule_id,
                        updated_at, version, booking_reference, status, interview_stage
                    ) VALUES (
                        1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1,
                        CURRENT_TIMESTAMP, 0, 'BK-V8-REMINDER', 'BOOKED', 'INITIAL'
                    )
                    """);
        }

        Flyway flyway = migrate(url, null);
        assertEquals("10", flyway.info().current().getVersion().getVersion());

        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement()) {
            try (var result = statement.executeQuery("SELECT reminder_generation FROM bookings WHERE id = 1")) {
                result.next();
                assertEquals(0, result.getInt(1));
            }
            try (var result = statement.executeQuery("""
                    SELECT IS_NULLABLE, COLUMN_DEFAULT
                    FROM INFORMATION_SCHEMA.COLUMNS
                    WHERE TABLE_NAME = 'BOOKINGS' AND COLUMN_NAME = 'REMINDER_GENERATION'
                    """)) {
                result.next();
                assertEquals("NO", result.getString("IS_NULLABLE"));
                assertEquals(null, result.getString("COLUMN_DEFAULT"));
            }
            statement.executeUpdate(reminderDeliveryInsert(1));
            assertThrows(SQLException.class, () -> statement.executeUpdate(reminderDeliveryInsert(2)));
            assertThrows(SQLException.class, () -> statement.executeUpdate("DELETE FROM bookings WHERE id = 1"));
            statement.executeUpdate("""
                    INSERT INTO notification_templates (
                        active, created_at, updated_at, version, subject, body, channel, event
                    ) VALUES (
                        TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 'Reminder', 'Body',
                        'EMAIL', 'INTERVIEW_REMINDER_24H'
                    )
                    """);
            try (var result = statement.executeQuery("""
                    SELECT COUNT(*) FROM INFORMATION_SCHEMA.INDEXES
                    WHERE INDEX_NAME IN ('IDX_INTERVIEW_REMINDER_RETRY_CLAIM', 'IDX_SCHEDULES_REMINDER_SCAN')
                    """)) {
                result.next();
                assertEquals(2, result.getInt(1));
            }
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

        assertEquals("10", flyway.info().current().getVersion().getVersion());
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

        assertEquals("10", flyway.info().current().getVersion().getVersion());
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

    private String reminderDeliveryInsert(long id) {
        return """
                INSERT INTO interview_reminder_deliveries (
                    id, attempt_count, reminder_generation, booking_id, created_at,
                    scheduled_start_at, updated_at, version, reminder_type, status
                ) VALUES (
                    %d, 1, 0, 1, CURRENT_TIMESTAMP,
                    '2026-09-02 09:00:00', CURRENT_TIMESTAMP, 0, 'REMINDER_24H', 'SENT'
                )
                """.formatted(id);
    }

    private String lifecycleHistoryInsert(long id, String action, String newStatus, String previousStatus) {
        String previousStatusValue = previousStatus == null ? "NULL" : "'" + previousStatus + "'";
        return """
                INSERT INTO booking_lifecycle_history (
                    id, action, appointment_date, end_time, actor_id, booking_id, created_at,
                    occurred_at, schedule_id, start_time, updated_at, version, booking_reference,
                    interview_mode, interview_stage, new_status, previous_status
                ) VALUES (
                    %d, '%s', '2026-09-11', '12:00:00', 1, 1, CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP, 2, '11:00:00', CURRENT_TIMESTAMP, 0, 'BK-V9-LIFECYCLE',
                    'ONSITE', 'INITIAL', '%s', %s
                )
                """.formatted(id, action, newStatus, previousStatusValue);
    }
}
