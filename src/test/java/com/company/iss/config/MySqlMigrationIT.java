package com.company.iss.config;

import com.company.iss.applicant.entity.Applicant;
import com.company.iss.applicant.entity.ApplicantStatus;
import com.company.iss.booking.entity.Booking;
import com.company.iss.booking.entity.BookingStatus;
import com.company.iss.booking.entity.InterviewStage;
import com.company.iss.branch.entity.Branch;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("mysql-it")
@Testcontainers
@Transactional
class MySqlMigrationIT {

    private static final List<String> EXPECTED_MIGRATIONS = List.of("1", "2", "3", "4", "5", "6", "7", "8");

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
    void freshMySqlMigratesFromV1ThroughV8AndHibernateValidatesTheFullContext() {
        List<String> appliedVersions = Arrays.stream(flyway.info().applied())
                .map(info -> info.getVersion().getVersion())
                .toList();

        assertEquals(EXPECTED_MIGRATIONS, appliedVersions);
        assertEquals("8", flyway.info().current().getVersion().getVersion());
        assertDoesNotThrow(flyway::validate);
        assertEquals("classpath:db/migration/mysql", environment.getProperty("spring.flyway.locations"));
        assertEquals("validate", environment.getProperty("spring.jpa.hibernate.ddl-auto"));
        assertTrue(entityManagerFactory.isOpen());
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
}
