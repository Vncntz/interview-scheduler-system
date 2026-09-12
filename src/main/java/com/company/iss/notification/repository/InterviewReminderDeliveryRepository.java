package com.company.iss.notification.repository;

import com.company.iss.booking.entity.BookingStatus;
import com.company.iss.notification.entity.InterviewReminderDelivery;
import com.company.iss.notification.entity.InterviewReminderType;
import com.company.iss.notification.dto.InterviewReminderRetryCandidate;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public interface InterviewReminderDeliveryRepository extends JpaRepository<InterviewReminderDelivery, Long> {

    @Query("""
            select d.id as deliveryId,
                   b.bookingReference as bookingReference,
                   d.reminderGeneration as reminderGeneration,
                   d.reminderType as reminderType,
                   d.status as deliveryStatus,
                   d.scheduledStartAt as scheduledStartAt,
                   d.attemptCount as attemptCount,
                   d.claimedAt as claimedAt,
                   d.nextAttemptAt as nextAttemptAt,
                   d.sentAt as sentAt,
                   d.statusReason as statusReason,
                   b.reminderGeneration as currentReminderGeneration,
                   b.status as currentBookingStatus,
                   a.active as applicantActive,
                   a.status as currentApplicantStatus,
                   s.active as scheduleActive,
                   s.status as currentScheduleStatus,
                   s.scheduleDate as currentScheduleDate,
                   s.startTime as currentScheduleStartTime
            from InterviewReminderDelivery d
            join d.booking b
            left join b.applicant a
            left join b.schedule s
            where (:status is null or d.status = :status)
              and (:reminderType is null or d.reminderType = :reminderType)
              and (:scheduledFrom is null or d.scheduledStartAt >= :scheduledFrom)
              and (:scheduledThrough is null or d.scheduledStartAt < :scheduledThrough)
            order by d.scheduledStartAt desc, d.id desc
            """)
    List<ReminderDeliveryHealthProjection> findHealthPage(
            @Param("status") com.company.iss.notification.entity.InterviewReminderDeliveryStatus status,
            @Param("reminderType") InterviewReminderType reminderType,
            @Param("scheduledFrom") java.time.LocalDateTime scheduledFrom,
            @Param("scheduledThrough") java.time.LocalDateTime scheduledThrough,
            Pageable pageable
    );

    @Query("""
            select count(d)
            from InterviewReminderDelivery d
            where (:status is null or d.status = :status)
              and (:reminderType is null or d.reminderType = :reminderType)
              and (:scheduledFrom is null or d.scheduledStartAt >= :scheduledFrom)
              and (:scheduledThrough is null or d.scheduledStartAt < :scheduledThrough)
            """)
    long countHealth(
            @Param("status") com.company.iss.notification.entity.InterviewReminderDeliveryStatus status,
            @Param("reminderType") InterviewReminderType reminderType,
            @Param("scheduledFrom") java.time.LocalDateTime scheduledFrom,
            @Param("scheduledThrough") java.time.LocalDateTime scheduledThrough
    );

    Optional<InterviewReminderDelivery> findByBookingIdAndReminderGenerationAndReminderType(
            Long bookingId,
            int reminderGeneration,
            InterviewReminderType reminderType
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select d from InterviewReminderDelivery d
            where d.booking.id = :bookingId
              and d.reminderGeneration = :generation
              and d.reminderType = :reminderType
            """)
    Optional<InterviewReminderDelivery> findForUpdate(
            @Param("bookingId") Long bookingId,
            @Param("generation") int generation,
            @Param("reminderType") InterviewReminderType reminderType
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from InterviewReminderDelivery d where d.id = :id")
    Optional<InterviewReminderDelivery> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select new com.company.iss.notification.dto.InterviewReminderRetryCandidate(
                d.id, d.booking.id, d.reminderType
            )
            from InterviewReminderDelivery d
            where d.reminderType = :reminderType
              and d.attemptCount < :maxAttempts
              and ((d.status = com.company.iss.notification.entity.InterviewReminderDeliveryStatus.FAILED
                    and d.nextAttemptAt <= :nowUtc)
                   or (d.status = com.company.iss.notification.entity.InterviewReminderDeliveryStatus.PENDING
                       and d.claimedAt <= :staleBeforeUtc))
              and d.booking.status in :statuses
              and d.booking.applicant.active = true
              and d.booking.applicant.status = com.company.iss.applicant.entity.ApplicantStatus.SCHEDULED
              and d.booking.schedule.active = true
              and d.booking.schedule.status <> com.company.iss.schedule.entity.ScheduleStatus.CANCELLED
              and (d.booking.schedule.scheduleDate > :lowerDate
                   or (d.booking.schedule.scheduleDate = :lowerDate
                       and d.booking.schedule.startTime > :lowerTime))
              and (d.booking.schedule.scheduleDate < :upperDate
                   or (d.booking.schedule.scheduleDate = :upperDate
                       and d.booking.schedule.startTime <= :upperTime))
            order by d.nextAttemptAt, d.claimedAt, d.id
            """)
    List<InterviewReminderRetryCandidate> findRetryCandidates(
            @Param("reminderType") InterviewReminderType reminderType,
            @Param("statuses") List<BookingStatus> statuses,
            @Param("maxAttempts") int maxAttempts,
            @Param("nowUtc") LocalDateTime nowUtc,
            @Param("staleBeforeUtc") LocalDateTime staleBeforeUtc,
            @Param("lowerDate") LocalDate lowerDate,
            @Param("lowerTime") LocalTime lowerTime,
            @Param("upperDate") LocalDate upperDate,
            @Param("upperTime") LocalTime upperTime,
            Pageable pageable
    );
}
