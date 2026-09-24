package com.company.iss.config;

import com.company.iss.applicant.entity.Applicant;
import com.company.iss.applicant.entity.ApplicantStatus;
import com.company.iss.booking.entity.Booking;
import com.company.iss.booking.entity.BookingStatus;
import com.company.iss.booking.entity.InterviewStage;
import com.company.iss.branch.entity.Branch;
import com.company.iss.auth.entity.Role;
import com.company.iss.auth.entity.User;
import com.company.iss.notification.entity.InterviewReminderDelivery;
import com.company.iss.notification.entity.InterviewReminderDeliveryStatus;
import com.company.iss.notification.entity.InterviewReminderType;
import com.company.iss.notification.repository.InterviewReminderDeliveryRepository;
import com.company.iss.schedule.entity.InterviewMode;
import com.company.iss.schedule.entity.Schedule;
import com.company.iss.schedule.entity.ScheduleStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("mysql-it")
@Testcontainers
@Transactional
class MySqlMigrationIT {

    private static final String MYSQL_ROOT_USER = "root";
    private static final List<String> EXPECTED_MIGRATIONS = List.of(
            "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11"
    );

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4.6"));

    @Autowired Flyway flyway;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired EntityManager entityManager;
    @Autowired EntityManagerFactory entityManagerFactory;
    @Autowired Environment environment;
    @Autowired InterviewReminderDeliveryRepository deliveryRepository;

    @Test
    void freshMySqlMigratesFromV1ThroughV11AndHibernateValidatesTheFullContext() {
        List<String> appliedVersions = Arrays.stream(flyway.info().applied())
                .map(info -> info.getVersion().getVersion())
                .toList();

        assertEquals(EXPECTED_MIGRATIONS, appliedVersions);
        assertEquals("11", flyway.info().current().getVersion().getVersion());
        assertDoesNotThrow(flyway::validate);
        assertEquals("classpath:db/migration/mysql", environment.getProperty("spring.flyway.locations"));
        assertEquals("validate", environment.getProperty("spring.jpa.hibernate.ddl-auto"));
        assertTrue(entityManagerFactory.isOpen());
        assertEquals("YES", jdbcTemplate.queryForObject(
                """
                SELECT IS_NULLABLE
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'users'
                  AND COLUMN_NAME = 'applicant_id'
                """, String.class));
        assertEquals(3, jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'users'
                  AND CONSTRAINT_NAME IN (
                      'uk_users_applicant',
                      'fk_users_applicant',
                      'chk_users_role_applicant_link'
                  )
                """, Integer.class));
        assertEquals("applicants", jdbcTemplate.queryForObject(
                """
                SELECT REFERENCED_TABLE_NAME
                FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'users'
                  AND COLUMN_NAME = 'applicant_id'
                  AND CONSTRAINT_NAME = 'fk_users_applicant'
                """, String.class));
        assertEquals(0, jdbcTemplate.queryForObject(
                """
                SELECT NON_UNIQUE
                FROM INFORMATION_SCHEMA.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'users'
                  AND INDEX_NAME = 'uk_users_applicant'
                  AND COLUMN_NAME = 'applicant_id'
                """, Integer.class));
        String ownershipCheck = jdbcTemplate.queryForObject(
                """
                SELECT CHECK_CLAUSE
                FROM INFORMATION_SCHEMA.CHECK_CONSTRAINTS
                WHERE CONSTRAINT_SCHEMA = DATABASE()
                  AND CONSTRAINT_NAME = 'chk_users_role_applicant_link'
                """, String.class);
        assertNotNull(ownershipCheck);
        assertTrue(ownershipCheck.toLowerCase(Locale.ROOT).contains("applicant_id"));
    }

    @Test
    void userApplicantOwnershipConstraintsRejectInvalidAndDuplicateLinksOnMySql() {
        jdbcTemplate.update("""
                INSERT INTO branches (
                    id, active, created_at, updated_at, version, branch_code,
                    city, province, branch_name, address
                ) VALUES (
                    811, TRUE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), 0, 'OWN811',
                    'Manila', 'Metro Manila', 'Ownership Branch', 'Test Address'
                )
                """);
        jdbcTemplate.update("""
                INSERT INTO applicants (
                    id, active, created_at, updated_at, version, mobile_number,
                    first_name, last_name, email, status, branch_id
                ) VALUES (
                    811, TRUE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), 0, '09170000000',
                    'Ownership', 'Applicant', 'mysql-owner@example.test', 'NEW', 811
                )
                """);
        jdbcTemplate.update(userOwnershipInsert(
                811, "mysql-applicant@example.test", "APPLICANT", "811"
        ));

        DataAccessException duplicateOwner = assertThrows(DataAccessException.class,
                () -> jdbcTemplate.update(userOwnershipInsert(
                        812, "mysql-duplicate@example.test", "APPLICANT", "811"
                )));
        assertMySqlConstraintViolation(duplicateOwner, 1062, "23000", "uk_users_applicant");

        DataAccessException missingOwner = assertThrows(DataAccessException.class,
                () -> jdbcTemplate.update(userOwnershipInsert(
                        813, "mysql-unlinked@example.test", "APPLICANT", "NULL"
                )));
        assertMySqlConstraintViolation(missingOwner, 3819, "HY000", "chk_users_role_applicant_link");

        DataAccessException linkedAdmin = assertThrows(DataAccessException.class,
                () -> jdbcTemplate.update(userOwnershipInsert(
                        814, "mysql-linked-admin@example.test", "ADMIN", "811"
                )));
        assertMySqlConstraintViolation(linkedAdmin, 3819, "HY000", "chk_users_role_applicant_link");

        DataAccessException unknownOwner = assertThrows(DataAccessException.class,
                () -> jdbcTemplate.update(userOwnershipInsert(
                        815, "mysql-unknown-owner@example.test", "APPLICANT", "999999"
                )));
        assertMySqlConstraintViolation(unknownOwner, 1452, "23000", "fk_users_applicant");
    }

    @Test
    void v11MySqlUpgradePreservesOperationsUsersAndRejectsUnresolvedLegacyApplicants() throws SQLException {
        String validSchemaUrl = createIsolatedSchema("m1_s1_valid_upgrade");
        migrateIsolatedSchema(validSchemaUrl, "10");
        try (var connection = DriverManager.getConnection(
                validSchemaUrl, MYSQL_ROOT_USER, MYSQL.getPassword()
        ); var statement = connection.createStatement()) {
            statement.executeUpdate(mysqlBranchInsert(911));
            statement.executeUpdate(mysqlApplicantInsert(911, "matching-email@example.test"));
            statement.executeUpdate(mysqlUserBeforeV11Insert(
                    911, "matching-email@example.test", "ADMIN"
            ));
            statement.executeUpdate(mysqlUserBeforeV11Insert(
                    912, "legacy-recruiter@example.test", "RECRUITER"
            ));
        }

        Flyway validUpgrade = migrateIsolatedSchema(validSchemaUrl, null);
        assertEquals("11", validUpgrade.info().current().getVersion().getVersion());
        try (var connection = DriverManager.getConnection(
                validSchemaUrl, MYSQL_ROOT_USER, MYSQL.getPassword()
        ); var statement = connection.createStatement();
             var result = statement.executeQuery("""
                     SELECT COUNT(*)
                     FROM users
                     WHERE id IN (911, 912) AND applicant_id IS NULL
                     """)) {
            result.next();
            assertEquals(2, result.getInt(1));
        }

        String legacySchemaUrl = createIsolatedSchema("m1_s1_legacy_applicant");
        migrateIsolatedSchema(legacySchemaUrl, "10");
        try (var connection = DriverManager.getConnection(
                legacySchemaUrl, MYSQL_ROOT_USER, MYSQL.getPassword()
        ); var statement = connection.createStatement()) {
            statement.executeUpdate(mysqlBranchInsert(921));
            statement.executeUpdate(mysqlApplicantInsert(921, "legacy-applicant@example.test"));
            statement.executeUpdate(mysqlUserBeforeV11Insert(
                    921, "legacy-applicant@example.test", "APPLICANT"
            ));
        }

        assertThrows(FlywayException.class, () -> migrateIsolatedSchema(legacySchemaUrl, null));
        try (var connection = DriverManager.getConnection(
                legacySchemaUrl, MYSQL_ROOT_USER, MYSQL.getPassword()
        ); var statement = connection.createStatement();
             var result = statement.executeQuery("""
                     SELECT COUNT(*)
                     FROM INFORMATION_SCHEMA.COLUMNS
                     WHERE TABLE_SCHEMA = 'm1_s1_legacy_applicant'
                       AND TABLE_NAME = 'users'
                       AND COLUMN_NAME = 'applicant_id'
                     """)) {
            result.next();
            assertEquals(0, result.getInt(1));
        }
    }

    @Test
    void v8MySqlEnumAcceptsBothSeededReminderEvents() {
        List<String> events = jdbcTemplate.queryForList(
                """
                SELECT event
                FROM notification_templates
                WHERE event IN ('INTERVIEW_REMINDER_24H', 'INTERVIEW_REMINDER_2H')
                ORDER BY event
                """,
                String.class
        );

        assertEquals(List.of("INTERVIEW_REMINDER_24H", "INTERVIEW_REMINDER_2H"), events);
    }

    @Test
    void reminderMappingRoundTripsEnumsAndMicrosecondTimestamps() {
        Booking booking = persistBooking("MAP");
        LocalDateTime scheduledStart = LocalDateTime.of(2026, 9, 3, 9, 15, 30, 123_456_000);
        LocalDateTime claimedAt = LocalDateTime.of(2026, 9, 2, 9, 15, 30, 654_321_000);
        InterviewReminderDelivery delivery = InterviewReminderDelivery.pending(
                booking,
                InterviewReminderType.REMINDER_24H,
                scheduledStart
        );
        delivery.claim("11111111-1111-1111-1111-111111111111", claimedAt);
        Long deliveryId = deliveryRepository.saveAndFlush(delivery).getId();

        entityManager.clear();
        InterviewReminderDelivery reloaded = deliveryRepository.findById(deliveryId).orElseThrow();

        assertEquals(InterviewReminderType.REMINDER_24H, reloaded.getReminderType());
        assertEquals(InterviewReminderDeliveryStatus.PENDING, reloaded.getStatus());
        assertEquals(0, reloaded.getReminderGeneration());
        assertEquals(scheduledStart, reloaded.getScheduledStartAt());
        assertEquals(claimedAt, reloaded.getClaimedAt());
        assertEquals(1, reloaded.getAttemptCount());
        assertNotNull(reloaded.getCreatedAt());
        assertNotNull(reloaded.getUpdatedAt());
    }

    @Test
    void duplicateBookingGenerationAndReminderTypeIsRejected() {
        Booking booking = persistBooking("DUP");
        LocalDateTime scheduledStart = LocalDateTime.of(2026, 9, 3, 10, 0);
        deliveryRepository.saveAndFlush(InterviewReminderDelivery.pending(
                booking,
                InterviewReminderType.REMINDER_24H,
                scheduledStart
        ));

        assertThrows(DataIntegrityViolationException.class, () -> deliveryRepository.saveAndFlush(
                InterviewReminderDelivery.pending(
                        booking,
                        InterviewReminderType.REMINDER_24H,
                        scheduledStart
                )
        ));
    }

    @Test
    void reminderDeliveryRejectsMissingBookingForeignKey() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update("""
                INSERT INTO interview_reminder_deliveries (
                    attempt_count, reminder_generation, booking_id, created_at,
                    scheduled_start_at, updated_at, version, reminder_type, status
                ) VALUES (
                    0, 0, 9223372036854775807, CURRENT_TIMESTAMP(6),
                    '2026-09-03 09:00:00.123456', CURRENT_TIMESTAMP(6), 0,
                    'REMINDER_24H', 'PENDING'
                )
                """));
    }

    @Test
    void v8CreatesTheTwoCriticalProcessingIndexesWithOrderedColumns() {
        Map<String, List<String>> indexes = new LinkedHashMap<>();
        jdbcTemplate.queryForList("""
                SELECT table_name, index_name, column_name
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND ((table_name = 'interview_reminder_deliveries'
                        AND index_name = 'idx_interview_reminder_retry_claim')
                       OR (table_name = 'schedules'
                           AND index_name = 'idx_schedules_reminder_scan'))
                ORDER BY table_name, index_name, seq_in_index
                """).forEach(row -> indexes.computeIfAbsent(
                        row.get("table_name") + "." + row.get("index_name"),
                        ignored -> new java.util.ArrayList<>()
                ).add((String) row.get("column_name")));

        assertEquals(Map.of(
                "interview_reminder_deliveries.idx_interview_reminder_retry_claim",
                List.of("status", "next_attempt_at", "claimed_at", "attempt_count", "id"),
                "schedules.idx_schedules_reminder_scan",
                List.of("schedule_date", "start_time", "id")
        ), indexes);
    }

    @Test
    void v9LifecycleSchemaEnforcesIdentityForeignKeysSnapshotsAndTimelineIndex() {
        Booking booking = persistBooking("LIFECYCLE");
        User actor = persistLifecycleActor("schema");

        jdbcTemplate.update(lifecycleHistoryInsert(booking, actor, 1,
                "BOOKING_CREATED", "BOOKED", null));

        Map<String, Object> stored = jdbcTemplate.queryForMap("""
                SELECT action, appointment_date, start_time, end_time, interview_mode,
                       booking_reference, interview_stage, new_status, previous_status
                FROM booking_lifecycle_history
                WHERE booking_id = ?
                """, booking.getId());
        assertEquals("BOOKING_CREATED", stored.get("action"));
        assertEquals("BK-MYSQL-LIFECYCLE", stored.get("booking_reference"));
        assertEquals("INITIAL", stored.get("interview_stage"));
        assertEquals("BOOKED", stored.get("new_status"));
        assertEquals(null, stored.get("previous_status"));

        assertThrows(DataIntegrityViolationException.class,
                () -> jdbcTemplate.update(lifecycleHistoryInsert(booking, actor, 2,
                        "BOOKING_CREATED", "BOOKED", null)));
        DataAccessException missingBookingException = assertThrows(DataAccessException.class,
                () -> jdbcTemplate.update("""
                        INSERT INTO booking_lifecycle_history (
                            action, appointment_date, end_time, actor_id, booking_id, created_at,
                            occurred_at, schedule_id, start_time, updated_at, version, booking_reference,
                            interview_mode, interview_stage, new_status
                        ) VALUES (
                            'BOOKING_CREATED', '2026-09-03', '10:00:00', ?, 9223372036854775807,
                            CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), ?, '09:00:00', CURRENT_TIMESTAMP(6),
                            0, 'BK-MISSING', 'ONLINE', 'INITIAL', 'BOOKED'
                        )
                        """, actor.getId(), booking.getSchedule().getId()));
        assertMySqlConstraintViolation(
                missingBookingException, 1452, "23000", "fk_booking_lifecycle_booking"
        );

        List<String> timelineColumns = jdbcTemplate.queryForList("""
                SELECT column_name
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = 'booking_lifecycle_history'
                  AND index_name = 'idx_booking_lifecycle_timeline'
                ORDER BY seq_in_index
                """, String.class);
        assertEquals(List.of("booking_id", "occurred_at", "id"), timelineColumns);
        assertEquals(13, jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'booking_reschedule_history'
                  AND column_name IN (
                      'snapshot_version',
                      'source_appointment_date', 'source_start_time', 'source_end_time',
                      'source_interview_mode', 'source_recruiter_display_name', 'source_branch_display_name',
                      'destination_appointment_date', 'destination_start_time', 'destination_end_time',
                      'destination_interview_mode', 'destination_recruiter_display_name',
                      'destination_branch_display_name'
                  )
                """, Integer.class));
    }

    @ParameterizedTest(name = "{1}: {2} -> {3}")
    @MethodSource("acceptedLifecycleTransitions")
    void v9LifecycleConstraintAcceptsEverySupportedTransition(
            long historyId,
            String action,
            String previousStatus,
            String newStatus
    ) {
        Booking booking = persistBooking("LIFE-OK-" + historyId);
        User actor = persistLifecycleActor("accepted-" + historyId);

        assertEquals(1, jdbcTemplate.update(lifecycleHistoryInsert(
                booking, actor, historyId, action, newStatus, previousStatus
        )));
    }

    @ParameterizedTest(name = "reject {1}: {2} -> {3}")
    @MethodSource("rejectedLifecycleTransitions")
    void v9LifecycleConstraintRejectsContradictoryTransitions(
            long historyId,
            String action,
            String previousStatus,
            String newStatus
    ) {
        Booking booking = persistBooking("LIFE-BAD-" + historyId);
        User actor = persistLifecycleActor("rejected-" + historyId);

        DataAccessException transitionException = assertThrows(DataAccessException.class,
                () -> jdbcTemplate.update(
                        lifecycleHistoryInsert(booking, actor, historyId, action, newStatus, previousStatus)
                ));
        assertMySqlConstraintViolation(
                transitionException, 3819, "HY000", "chk_booking_lifecycle_transition"
        );
    }

    private static Stream<Arguments> acceptedLifecycleTransitions() {
        return Stream.of(
                Arguments.of(101L, "BOOKING_CREATED", null, "BOOKED"),
                Arguments.of(102L, "BOOKING_CONFIRMED", "BOOKED", "CONFIRMED"),
                Arguments.of(103L, "ATTENDANCE_RECORDED", "CONFIRMED", "ATTENDED"),
                Arguments.of(104L, "NO_SHOW_RECORDED", "CONFIRMED", "NO_SHOW"),
                Arguments.of(105L, "BOOKING_CANCELLED", "BOOKED", "CANCELLED"),
                Arguments.of(106L, "BOOKING_CANCELLED", "CONFIRMED", "CANCELLED")
        );
    }

    private static Stream<Arguments> rejectedLifecycleTransitions() {
        return Stream.of(
                Arguments.of(201L, "BOOKING_CREATED", "BOOKED", "BOOKED"),
                Arguments.of(202L, "BOOKING_CREATED", null, "CONFIRMED"),
                Arguments.of(203L, "BOOKING_CONFIRMED", null, "CONFIRMED"),
                Arguments.of(204L, "BOOKING_CONFIRMED", "BOOKED", "ATTENDED"),
                Arguments.of(205L, "ATTENDANCE_RECORDED", "BOOKED", "ATTENDED"),
                Arguments.of(206L, "ATTENDANCE_RECORDED", "CONFIRMED", "NO_SHOW"),
                Arguments.of(207L, "NO_SHOW_RECORDED", "BOOKED", "NO_SHOW"),
                Arguments.of(208L, "NO_SHOW_RECORDED", "CONFIRMED", "ATTENDED"),
                Arguments.of(209L, "BOOKING_CANCELLED", "ATTENDED", "CANCELLED"),
                Arguments.of(210L, "BOOKING_CANCELLED", "BOOKED", "CONFIRMED")
        );
    }

    private Booking persistBooking(String suffix) {
        Branch branch = new Branch();
        branch.setBranchCode("MYSQL-" + suffix);
        branch.setBranchName("MySQL " + suffix + " Branch");
        branch.setAddress("Test Address");
        branch.setCity("Manila");
        branch.setProvince("Metro Manila");
        branch.setActive(true);
        entityManager.persist(branch);

        Applicant applicant = new Applicant();
        applicant.setBranch(branch);
        applicant.setFirstName("MySQL");
        applicant.setLastName(suffix);
        applicant.setEmail("mysql-" + suffix.toLowerCase() + "@example.test");
        applicant.setMobileNumber("09170000000");
        applicant.setStatus(ApplicantStatus.SCHEDULED);
        applicant.setActive(true);
        entityManager.persist(applicant);

        Schedule schedule = new Schedule();
        schedule.setBranch(branch);
        schedule.setScheduleDate(LocalDate.of(2026, 9, 3));
        schedule.setStartTime(LocalTime.of(9, 0));
        schedule.setEndTime(LocalTime.of(10, 0));
        schedule.setSlotCapacity(2);
        schedule.setBookedCount(1);
        schedule.setInterviewMode(InterviewMode.ONLINE);
        schedule.setStatus(ScheduleStatus.OPEN);
        schedule.setActive(true);
        entityManager.persist(schedule);

        Booking booking = Booking.forInterviewStage(InterviewStage.INITIAL);
        booking.setBookingReference("BK-MYSQL-" + suffix);
        booking.setApplicant(applicant);
        booking.setSchedule(schedule);
        booking.setStatus(BookingStatus.BOOKED);
        booking.setBookedDateTime(LocalDateTime.of(2026, 9, 1, 12, 0));
        entityManager.persist(booking);
        entityManager.flush();
        return booking;
    }

    private String userOwnershipInsert(long id, String email, String role, String applicantId) {
        return """
                INSERT INTO users (
                    id, active, failed_login_attempts, must_change_password, created_at,
                    updated_at, version, email, full_name, password_hash, role, applicant_id
                ) VALUES (
                    %d, TRUE, 0, FALSE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), 0,
                    '%s', 'Ownership User', 'test-only-hash', '%s', %s
                )
                """.formatted(id, email, role, applicantId);
    }

    private String createIsolatedSchema(String schema) throws SQLException {
        try (var connection = DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL_ROOT_USER, MYSQL.getPassword()
        ); var statement = connection.createStatement()) {
            statement.executeUpdate("CREATE DATABASE " + schema);
        }

        String databaseMarker = "/" + MYSQL.getDatabaseName();
        String containerUrl = MYSQL.getJdbcUrl();
        int markerIndex = containerUrl.lastIndexOf(databaseMarker);
        return containerUrl.substring(0, markerIndex + 1)
                + schema
                + containerUrl.substring(markerIndex + databaseMarker.length());
    }

    private Flyway migrateIsolatedSchema(String schemaUrl, String target) {
        var configuration = Flyway.configure()
                .dataSource(schemaUrl, MYSQL_ROOT_USER, MYSQL.getPassword())
                .locations("classpath:db/migration/mysql")
                .cleanDisabled(true)
                .validateOnMigrate(true);
        if (target != null) {
            configuration.target(target);
        }
        Flyway isolatedFlyway = configuration.load();
        isolatedFlyway.migrate();
        return isolatedFlyway;
    }

    private String mysqlBranchInsert(long id) {
        return """
                INSERT INTO branches (
                    id, active, created_at, updated_at, version, branch_code,
                    city, province, branch_name, address
                ) VALUES (
                    %d, TRUE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), 0, 'UPG-%d',
                    'Manila', 'Metro Manila', 'Upgrade Branch', 'Test Address'
                )
                """.formatted(id, id);
    }

    private String mysqlApplicantInsert(long id, String email) {
        return """
                INSERT INTO applicants (
                    id, active, created_at, updated_at, version, mobile_number,
                    first_name, last_name, email, status, branch_id
                ) VALUES (
                    %d, TRUE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), 0, '09170000000',
                    'Upgrade', 'Applicant', '%s', 'NEW', %d
                )
                """.formatted(id, email, id);
    }

    private String mysqlUserBeforeV11Insert(long id, String email, String role) {
        return """
                INSERT INTO users (
                    id, active, failed_login_attempts, must_change_password, created_at,
                    updated_at, version, email, full_name, password_hash, role
                ) VALUES (
                    %d, TRUE, 0, FALSE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), 0,
                    '%s', 'Upgrade User', 'test-only-hash', '%s'
                )
                """.formatted(id, email, role);
    }

    private User persistLifecycleActor(String suffix) {
        User actor = new User();
        actor.setEmail("mysql-lifecycle-" + suffix + "@example.test");
        actor.setPasswordHash("test-only-hash");
        actor.setFullName("MySQL Lifecycle Actor " + suffix);
        actor.setRole(Role.ADMIN);
        actor.setActive(true);
        entityManager.persist(actor);
        entityManager.flush();
        return actor;
    }

    private void assertMySqlConstraintViolation(
            DataAccessException exception,
            int expectedErrorCode,
            String expectedSqlState,
            String expectedConstraint
    ) {
        SQLException sqlException = assertInstanceOf(SQLException.class, exception.getMostSpecificCause());
        assertEquals(expectedErrorCode, sqlException.getErrorCode());
        assertEquals(expectedSqlState, sqlException.getSQLState());
        assertNotNull(sqlException.getMessage());
        assertTrue(sqlException.getMessage().toLowerCase(Locale.ROOT)
                .contains(expectedConstraint.toLowerCase(Locale.ROOT)));
    }

    private String lifecycleHistoryInsert(Booking booking, User actor, long id, String action,
                                          String newStatus, String previousStatus) {
        String previousStatusValue = previousStatus == null ? "NULL" : "'" + previousStatus + "'";
        return """
                INSERT INTO booking_lifecycle_history (
                    id, action, appointment_date, end_time, actor_id, booking_id, created_at,
                    occurred_at, schedule_id, start_time, updated_at, version, booking_reference,
                    interview_mode, interview_stage, new_status, previous_status
                ) VALUES (
                    %d, '%s', '2026-09-03', '10:00:00', %d, %d, CURRENT_TIMESTAMP(6),
                    '2026-09-01 12:00:00.123456', %d, '09:00:00', CURRENT_TIMESTAMP(6), 0,
                    '%s', 'ONLINE', 'INITIAL', '%s', %s
                )
                """.formatted(
                id, action, actor.getId(), booking.getId(), booking.getSchedule().getId(),
                booking.getBookingReference(), newStatus, previousStatusValue
        );
    }
}
