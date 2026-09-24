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
    void freshSchemaMigratesThroughV11WithApplicantOwnershipAndWithoutPersistedNotificationSecrets() throws SQLException {
        String url = databaseUrl("fresh_v11");

        Flyway flyway = migrate(url, null);

        assertEquals("11", flyway.info().current().getVersion().getVersion());
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
                     WHERE TABLE_NAME = 'USERS' AND COLUMN_NAME = 'APPLICANT_ID'
                     """)) {
            result.next();
            assertEquals("YES", result.getString(1));
        }
    }

    @Test
    void v11PreservesOperationsUsersAndEnforcesApplicantOwnershipConstraints() throws SQLException {
        String url = databaseUrl("v11_user_applicant_ownership");
        migrate(url, "10");

        try (var connection = DriverManager.getConnection(url, "sa", "");
            var statement = connection.createStatement()) {
            statement.executeUpdate(branchInsert(111));
            statement.executeUpdate(applicantInsert(111, "ownership@example.test"));
            statement.executeUpdate(userInsertBeforeV11(111, "ownership@example.test", "ADMIN"));
            statement.executeUpdate(userInsertBeforeV11(112, "recruiter@example.test", "RECRUITER"));
        }

        Flyway flyway = migrate(url, null);

        assertEquals("11", flyway.info().current().getVersion().getVersion());
        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement()) {
            try (var result = statement.executeQuery("""
                    SELECT COUNT(*)
                    FROM users
                    WHERE id IN (111, 112) AND applicant_id IS NULL
                    """)) {
                result.next();
                assertEquals(2, result.getInt(1));
            }
            try (var result = statement.executeQuery("""
                    SELECT applicant_id
                    FROM users
                    WHERE email = 'ownership@example.test'
                    """)) {
                result.next();
                assertEquals(null, result.getObject(1));
            }
            try (var result = statement.executeQuery("""
                    SELECT COUNT(*)
                    FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
                    WHERE TABLE_NAME = 'USERS'
                      AND CONSTRAINT_NAME IN (
                          'UK_USERS_APPLICANT',
                          'FK_USERS_APPLICANT',
                          'CHK_USERS_ROLE_APPLICANT_LINK'
                      )
                    """)) {
                result.next();
                assertEquals(3, result.getInt(1));
            }

            statement.executeUpdate(userInsertAfterV11(
                    113, "applicant@example.test", "APPLICANT", "111"
            ));
            assertThrows(SQLException.class, () -> statement.executeUpdate(userInsertAfterV11(
                    114, "duplicate-owner@example.test", "APPLICANT", "111"
            )));
            assertThrows(SQLException.class, () -> statement.executeUpdate(userInsertAfterV11(
                    115, "missing-owner@example.test", "APPLICANT", "NULL"
            )));
            assertThrows(SQLException.class, () -> statement.executeUpdate(userInsertAfterV11(
                    116, "linked-admin@example.test", "ADMIN", "111"
            )));
            assertThrows(SQLException.class, () -> statement.executeUpdate(userInsertAfterV11(
                    117, "unknown-owner@example.test", "APPLICANT", "999999"
            )));
        }
    }

    @Test
    void v11RejectsLegacyApplicantInsteadOfInferringOwnershipFromEmail() throws SQLException {
        String url = databaseUrl("v11_legacy_applicant");
        migrate(url, "10");

        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement()) {
            statement.executeUpdate(branchInsert(121));
            statement.executeUpdate(applicantInsert(121, "same-email@example.test"));
            statement.executeUpdate(userInsertBeforeV11(
                    121, "same-email@example.test", "APPLICANT"
            ));
        }

        assertThrows(FlywayException.class, () -> migrate(url, null));

        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement()) {
            try (var result = statement.executeQuery("""
                     SELECT COUNT(*)
                     FROM INFORMATION_SCHEMA.COLUMNS
                     WHERE TABLE_NAME = 'USERS' AND COLUMN_NAME = 'APPLICANT_ID'
                     """)) {
                result.next();
                assertEquals(0, result.getInt(1));
            }
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

        assertEquals("11", flyway.info().current().getVersion().getVersion());
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

        assertEquals("11", flyway.info().current().getVersion().getVersion());
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
        assertEquals("11", flyway.info().current().getVersion().getVersion());

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

        assertEquals("11", flyway.info().current().getVersion().getVersion());
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

        assertEquals("11", flyway.info().current().getVersion().getVersion());
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

    private String branchInsert(long id) {
        return """
                INSERT INTO branches (
                    id, active, created_at, updated_at, version, branch_code,
                    city, province, branch_name, address
                ) VALUES (
                    %d, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 'OWN-%d',
                    'Manila', 'Metro Manila', 'Ownership Branch', 'Test Address'
                )
                """.formatted(id, id);
    }

    private String applicantInsert(long id, String email) {
        return """
                INSERT INTO applicants (
                    id, active, created_at, updated_at, version, mobile_number,
                    first_name, last_name, email, status, branch_id
                ) VALUES (
                    %d, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, '09170000000',
                    'Ownership', 'Applicant', '%s', 'NEW', %d
                )
                """.formatted(id, email, id);
    }

    private String userInsertBeforeV11(long id, String email, String role) {
        return """
                INSERT INTO users (
                    id, active, failed_login_attempts, must_change_password, created_at,
                    updated_at, version, email, full_name, password_hash, role
                ) VALUES (
                    %d, TRUE, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
                    '%s', 'Ownership User', 'test-only-hash', '%s'
                )
                """.formatted(id, email, role);
    }

    private String userInsertAfterV11(long id, String email, String role, String applicantId) {
        return """
                INSERT INTO users (
                    id, active, failed_login_attempts, must_change_password, created_at,
                    updated_at, version, email, full_name, password_hash, role, applicant_id
                ) VALUES (
                    %d, TRUE, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
                    '%s', 'Ownership User', 'test-only-hash', '%s', %s
                )
                """.formatted(id, email, role, applicantId);
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
