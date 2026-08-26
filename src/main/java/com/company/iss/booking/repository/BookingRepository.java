package com.company.iss.booking.repository;

import com.company.iss.applicant.entity.Applicant;
import com.company.iss.booking.entity.Booking;
import com.company.iss.booking.entity.BookingStatus;
import com.company.iss.schedule.entity.Schedule;
import com.company.iss.schedule.entity.ScheduleStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    @Query("""
                SELECT b
                FROM Booking b
                WHERE
                    LOWER(CONCAT(
                        b.applicant.firstName,
                        ' ',
                        COALESCE(b.applicant.middleName, ''),
                        ' ',
                        b.applicant.lastName
                    )) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR LOWER(b.bookingReference) LIKE LOWER(CONCAT('%', :keyword, '%'))
            """)
    List<Booking> search(String keyword);

    List<Booking> findByApplicant(Applicant applicant);

    List<Booking> findBySchedule(Schedule schedule);

    List<Booking> findByStatus(BookingStatus status);

    Optional<Booking> findFirstByApplicantAndStatusIn(Applicant applicant, List<BookingStatus> statuses);

    boolean existsByApplicantIdAndStatusIn(Long applicantId, List<BookingStatus> statuses);

    Long countByStatus(BookingStatus status);

    Optional<Booking> findByApplicantAndSchedule(Applicant applicant, Schedule schedule);

    @EntityGraph(attributePaths = {"applicant", "applicant.positionOpening", "schedule", "schedule.branch", "schedule.recruiter"})
    List<Booking> findByScheduleBranchIdOrderByScheduleScheduleDateDescScheduleStartTimeDesc(Long branchId);

    @EntityGraph(attributePaths = {"applicant", "applicant.positionOpening", "schedule", "schedule.branch", "schedule.recruiter"})
    @Query("""
            select b from Booking b
            where b.schedule.branch.id = :branchId
              and (lower(concat(b.applicant.firstName, ' ', b.applicant.lastName)) like lower(concat('%', :keyword, '%'))
                   or lower(b.bookingReference) like lower(concat('%', :keyword, '%')))
            order by b.schedule.scheduleDate desc, b.schedule.startTime desc
            """)
    List<Booking> searchByBranchId(@Param("branchId") Long branchId, @Param("keyword") String keyword);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"applicant", "schedule", "schedule.branch", "schedule.recruiter"})
    @Query("select b from Booking b where b.id = :id")
    Optional<Booking> findByIdForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = {"applicant", "applicant.positionOpening", "schedule", "schedule.branch", "schedule.recruiter"})
    List<Booking> findByScheduleRecruiterIdAndScheduleScheduleDateAndStatusInOrderByScheduleStartTime(
            Long recruiterId, LocalDate scheduleDate, List<BookingStatus> statuses
    );

    @EntityGraph(attributePaths = {"applicant", "applicant.positionOpening", "schedule", "schedule.branch", "schedule.recruiter"})
    @Query("""
            select b from Booking b
            where b.schedule.recruiter.id = :recruiterId
              and b.status in :statuses
              and (b.schedule.scheduleDate > :today
                   or (b.schedule.scheduleDate = :today and b.schedule.startTime > :now))
            order by b.schedule.scheduleDate, b.schedule.startTime
            """)
    List<Booking> findUpcomingAssigned(
            @Param("recruiterId") Long recruiterId,
            @Param("today") LocalDate today,
            @Param("now") LocalTime now,
            @Param("statuses") List<BookingStatus> statuses
    );

    @EntityGraph(attributePaths = {"applicant", "applicant.positionOpening", "schedule", "schedule.branch", "schedule.recruiter"})
    List<Booking> findByScheduleBranchIdAndStatusOrderByScheduleScheduleDateAscScheduleStartTimeAsc(
            Long branchId, BookingStatus status
    );

    @EntityGraph(attributePaths = {"applicant", "applicant.positionOpening", "schedule", "schedule.branch", "schedule.recruiter"})
    @Query("""
            select b from Booking b
            where b.schedule.branch.id = :branchId and b.status = :status
              and (b.schedule.scheduleDate < :today
                   or (b.schedule.scheduleDate = :today and b.schedule.endTime <= :now))
            order by b.schedule.scheduleDate, b.schedule.startTime
            """)
    List<Booking> findDueByBranchAndStatus(
            @Param("branchId") Long branchId,
            @Param("status") BookingStatus status,
            @Param("today") LocalDate today,
            @Param("now") LocalTime now
    );

    @EntityGraph(attributePaths = {"applicant", "applicant.positionOpening", "schedule", "schedule.branch", "schedule.recruiter"})
    @Query("""
            select b from Booking b
            where b.schedule.branch.id = :branchId and b.status = :status
              and (b.schedule.scheduleDate < :today
                   or (b.schedule.scheduleDate = :today and b.schedule.endTime <= :now))
              and not exists (select e.id from InterviewEvaluation e where e.booking = b)
            order by b.schedule.scheduleDate, b.schedule.startTime
            """)
    List<Booking> findOverdueUnevaluatedByBranch(
            @Param("branchId") Long branchId,
            @Param("status") BookingStatus status,
            @Param("today") LocalDate today,
            @Param("now") LocalTime now
    );

    @EntityGraph(attributePaths = {
            "applicant",
            "applicant.positionOpening",
            "applicant.positionOpening.client",
            "schedule",
            "schedule.recruiter"
    })
    @Query("select b from Booking b where b.id = :id")
    Optional<Booking> findDetailedById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"applicant", "applicant.positionOpening", "schedule", "schedule.branch", "schedule.recruiter"})
    @Query("""
                select b
                from Booking b
                where b.status in :statuses
                  and b.schedule.active = true
                  and b.schedule.status <> :cancelledScheduleStatus
                  and (
                      b.schedule.scheduleDate > :today
                      or (
                          b.schedule.scheduleDate = :today
                          and b.schedule.endTime >= :currentTime
                      )
                  )
                order by b.schedule.scheduleDate, b.schedule.startTime
            """)
    List<Booking> findUpcoming(
            @Param("today") LocalDate today,
            @Param("currentTime") LocalTime currentTime,
            @Param("statuses") List<BookingStatus> statuses,
            @Param("cancelledScheduleStatus") ScheduleStatus cancelledScheduleStatus,
            Pageable pageable
    );
}
