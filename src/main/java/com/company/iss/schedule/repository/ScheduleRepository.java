package com.company.iss.schedule.repository;

import com.company.iss.auth.entity.User;
import com.company.iss.schedule.entity.Schedule;
import com.company.iss.schedule.entity.ScheduleStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

    @EntityGraph(attributePaths = {"branch", "recruiter"})
    @Query("""
            select s
            from Schedule s
            left join s.branch branch
            left join s.recruiter recruiter
            where :keywordPattern is null
               or lower(branch.branchName) like :keywordPattern
               or lower(recruiter.fullName) like :keywordPattern
               or (:matchesOnsite = true and s.interviewMode = com.company.iss.schedule.entity.InterviewMode.ONSITE)
               or (:matchesOnline = true and s.interviewMode = com.company.iss.schedule.entity.InterviewMode.ONLINE)
               or (:matchesPhone = true and s.interviewMode = com.company.iss.schedule.entity.InterviewMode.PHONE)
               or (:matchesOpen = true and s.status = com.company.iss.schedule.entity.ScheduleStatus.OPEN)
               or (:matchesFull = true and s.status = com.company.iss.schedule.entity.ScheduleStatus.FULL)
               or (:matchesClosed = true and s.status = com.company.iss.schedule.entity.ScheduleStatus.CLOSED)
               or (:matchesCancelled = true and s.status = com.company.iss.schedule.entity.ScheduleStatus.CANCELLED)
            """)
    List<Schedule> findGridPage(
            @Param("keywordPattern") String keywordPattern,
            @Param("matchesOnsite") boolean matchesOnsite,
            @Param("matchesOnline") boolean matchesOnline,
            @Param("matchesPhone") boolean matchesPhone,
            @Param("matchesOpen") boolean matchesOpen,
            @Param("matchesFull") boolean matchesFull,
            @Param("matchesClosed") boolean matchesClosed,
            @Param("matchesCancelled") boolean matchesCancelled,
            Pageable pageable
    );

    @Query("""
            select count(s)
            from Schedule s
            left join s.branch branch
            left join s.recruiter recruiter
            where :keywordPattern is null
               or lower(branch.branchName) like :keywordPattern
               or lower(recruiter.fullName) like :keywordPattern
               or (:matchesOnsite = true and s.interviewMode = com.company.iss.schedule.entity.InterviewMode.ONSITE)
               or (:matchesOnline = true and s.interviewMode = com.company.iss.schedule.entity.InterviewMode.ONLINE)
               or (:matchesPhone = true and s.interviewMode = com.company.iss.schedule.entity.InterviewMode.PHONE)
               or (:matchesOpen = true and s.status = com.company.iss.schedule.entity.ScheduleStatus.OPEN)
               or (:matchesFull = true and s.status = com.company.iss.schedule.entity.ScheduleStatus.FULL)
               or (:matchesClosed = true and s.status = com.company.iss.schedule.entity.ScheduleStatus.CLOSED)
               or (:matchesCancelled = true and s.status = com.company.iss.schedule.entity.ScheduleStatus.CANCELLED)
            """)
    long countGrid(
            @Param("keywordPattern") String keywordPattern,
            @Param("matchesOnsite") boolean matchesOnsite,
            @Param("matchesOnline") boolean matchesOnline,
            @Param("matchesPhone") boolean matchesPhone,
            @Param("matchesOpen") boolean matchesOpen,
            @Param("matchesFull") boolean matchesFull,
            @Param("matchesClosed") boolean matchesClosed,
            @Param("matchesCancelled") boolean matchesCancelled
    );

    List<Schedule> findByScheduleDate(LocalDate scheduleDate);

    List<Schedule> findByStatus(ScheduleStatus status);

    List<Schedule> findByRecruiter(User recruiter);

    List<Schedule> findByRecruiterAndScheduleDate(User recruiter, LocalDate scheduleDate);

    List<Schedule> findByScheduleDateBetween(LocalDate startDate, LocalDate endDate);

    List<Schedule> findByScheduleDateAndActiveTrueAndStatusNot(LocalDate scheduleDate, ScheduleStatus status);

    List<Schedule> findByScheduleDateBetweenAndActiveTrueAndStatusNot(
            LocalDate startDate,
            LocalDate endDate,
            ScheduleStatus status
    );

    List<Schedule> findByActiveTrueAndStatus(ScheduleStatus status);

    @EntityGraph(attributePaths = {"branch", "recruiter"})
    List<Schedule> findByBranchIdAndActiveTrueAndStatusOrderByScheduleDateAscStartTimeAsc(
            Long branchId, ScheduleStatus status
    );

    @EntityGraph(attributePaths = {"branch", "recruiter"})
    @Query("""
            select s
            from Schedule s
            where s.id <> :excludedScheduleId
              and s.active = true
              and s.status = :openStatus
              and s.bookedCount < s.slotCapacity
              and (
                  s.scheduleDate > :today
                  or (s.scheduleDate = :today and s.startTime > :currentTime)
              )
              and (:branchId is null or s.branch.id = :branchId)
            order by s.scheduleDate, s.startTime, s.id
            """)
    List<Schedule> findEligibleRescheduleDestinations(
            @Param("excludedScheduleId") Long excludedScheduleId,
            @Param("today") LocalDate today,
            @Param("currentTime") LocalTime currentTime,
            @Param("openStatus") ScheduleStatus openStatus,
            @Param("branchId") Long branchId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"branch", "recruiter"})
    @Query("select s from Schedule s where s.id in :ids order by s.id")
    List<Schedule> findAllByIdForUpdate(@Param("ids") List<Long> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"branch", "recruiter"})
    @Query("select s from Schedule s where s.id = :id")
    Optional<Schedule> findByIdForUpdate(@Param("id") Long id);

    Long countByScheduleDate(LocalDate scheduleDate);

    Long countByScheduleDateAndActiveTrueAndStatusNot(LocalDate scheduleDate, ScheduleStatus status);
}
