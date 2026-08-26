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

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

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
