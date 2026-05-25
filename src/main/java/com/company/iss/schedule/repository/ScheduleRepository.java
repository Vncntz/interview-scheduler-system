package com.company.iss.schedule.repository;

import com.company.iss.auth.entity.User;
import com.company.iss.schedule.entity.Schedule;
import com.company.iss.schedule.entity.ScheduleStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

    List<Schedule> findByScheduleDate(LocalDate scheduleDate);

    List<Schedule> findByStatus(ScheduleStatus status);

    List<Schedule> findByRecruiter(User recruiter);

    List<Schedule> findByRecruiterAndScheduleDate(User recruiter, LocalDate scheduleDate);

    List<Schedule> findByScheduleDateBetween(LocalDate startDate, LocalDate endDate);

    List<Schedule> findByActiveTrueAndStatus(ScheduleStatus status);

    Long countByScheduleDate(LocalDate scheduleDate);
}