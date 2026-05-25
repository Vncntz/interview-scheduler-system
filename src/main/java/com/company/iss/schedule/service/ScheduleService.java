package com.company.iss.schedule.service;

import com.company.iss.auth.entity.User;
import com.company.iss.branch.entity.Branch;
import com.company.iss.schedule.dto.BulkScheduleResult;
import com.company.iss.schedule.entity.InterviewMode;
import com.company.iss.schedule.entity.Schedule;
import com.company.iss.schedule.entity.ScheduleStatus;
import com.company.iss.schedule.repository.ScheduleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

@Service
public class ScheduleService {

    @Autowired
    private ScheduleRepository scheduleRepository;

    public List<Schedule> search(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return scheduleRepository.findAll();
        }

        String search = keyword.toLowerCase();

        return scheduleRepository.findAll().stream().filter(schedule -> schedule.getBranch().getBranchName().toLowerCase().contains(search) || schedule.getRecruiter().getFullName().toLowerCase().contains(search) || schedule.getInterviewMode().name().toLowerCase().contains(search) || schedule.getStatus().name().toLowerCase().contains(search)).toList();
    }

    public Schedule save(Schedule schedule) {
        validate(schedule);

        if (schedule.getId() == null) {
            schedule.setStatus(ScheduleStatus.OPEN);
            schedule.setBookedCount(0);
            schedule.setActive(true);
        }

        List<Schedule> existingSchedules = scheduleRepository.findByRecruiterAndScheduleDate(schedule.getRecruiter(), schedule.getScheduleDate());

        for (Schedule existing : existingSchedules) {

            if (schedule.getId() != null && existing.getId().equals(schedule.getId())) {
                continue;
            }

            boolean overlaps = schedule.getStartTime().isBefore(existing.getEndTime()) && schedule.getEndTime().isAfter(existing.getStartTime());

            if (overlaps) {
                throw new RuntimeException("Recruiter already has an overlapping schedule.");
            }
        }

        return scheduleRepository.save(schedule);
    }

    public void activate(Schedule schedule) {
        schedule.setActive(true);
        scheduleRepository.save(schedule);
    }

    public void deactivate(Schedule schedule) {
        schedule.setActive(false);
        scheduleRepository.save(schedule);
    }

    public void close(Schedule schedule) {
        schedule.setStatus(ScheduleStatus.CLOSED);
        scheduleRepository.save(schedule);
    }

    public void reopen(Schedule schedule) {
        schedule.setStatus(ScheduleStatus.OPEN);
        scheduleRepository.save(schedule);
    }

    public void cancel(Schedule schedule) {
        if (schedule.getBookedCount() > 0) {
            throw new RuntimeException("Cannot cancel a booked schedule.");
        }

        schedule.setStatus(ScheduleStatus.CANCELLED);
        scheduleRepository.save(schedule);
    }

    public BulkScheduleResult generateBulkSchedules(Branch branch, User recruiter, LocalDate startDate, LocalDate endDate, Set<DayOfWeek> selectedDays, LocalTime workStart, LocalTime workEnd, Integer intervalMinutes, Integer slotCapacity, InterviewMode interviewMode, String notes) {

        if (branch == null) {
            throw new RuntimeException("Branch is required.");
        }

        if (recruiter == null) {
            throw new RuntimeException("Recruiter is required.");
        }

        if (startDate == null || endDate == null) {
            throw new RuntimeException("Date range is required.");
        }

        if (selectedDays == null || selectedDays.isEmpty()) {
            throw new RuntimeException("Please select at least one day.");
        }

        if (workStart == null || workEnd == null) {
            throw new RuntimeException("Working hours are required.");
        }

        if (intervalMinutes == null || intervalMinutes <= 0) {
            throw new RuntimeException("Interval must be greater than zero.");
        }

        if (slotCapacity == null || slotCapacity <= 0) {
            throw new RuntimeException("Slot capacity must be greater than zero.");
        }

        if (!workEnd.isAfter(workStart)) {
            throw new RuntimeException("End time must be after start time.");
        }

        if (recruiter.getBranch() == null || !recruiter.getBranch().getId().equals(branch.getId())) {
            throw new RuntimeException("Recruiter does not belong to selected branch.");
        }

        BulkScheduleResult result = new BulkScheduleResult();

        LocalDate currentDate = startDate;

        while (!currentDate.isAfter(endDate)) {

            if (selectedDays.contains(currentDate.getDayOfWeek())) {

                LocalTime slotStart = workStart;

                while (slotStart.isBefore(workEnd)) {

                    LocalTime slotEnd = slotStart.plusMinutes(intervalMinutes);

                    if (slotEnd.isAfter(workEnd)) {
                        break;
                    }

                    boolean overlapExists = false;

                    List<Schedule> existingSchedules = scheduleRepository.findByRecruiterAndScheduleDate(recruiter, currentDate);

                    for (Schedule existing : existingSchedules) {

                        boolean overlaps = slotStart.isBefore(existing.getEndTime()) && slotEnd.isAfter(existing.getStartTime());

                        if (overlaps) {
                            overlapExists = true;
                            break;
                        }
                    }

                    if (!overlapExists) {

                        Schedule schedule = new Schedule();
                        schedule.setBranch(branch);
                        schedule.setRecruiter(recruiter);
                        schedule.setScheduleDate(currentDate);
                        schedule.setStartTime(slotStart);
                        schedule.setEndTime(slotEnd);
                        schedule.setSlotCapacity(slotCapacity);
                        schedule.setBookedCount(0);
                        schedule.setStatus(ScheduleStatus.OPEN);
                        schedule.setInterviewMode(interviewMode);
                        schedule.setNotes(notes);
                        schedule.setActive(true);

                        scheduleRepository.save(schedule);

                        result.setCreatedCount(result.getCreatedCount() + 1);

                    } else {

                        result.setSkippedCount(result.getSkippedCount() + 1);
                    }

                    slotStart = slotEnd;
                }
            }

            currentDate = currentDate.plusDays(1);
        }

        return result;
    }

    private void validate(Schedule schedule) {

        if (schedule.getBranch() == null) {
            throw new RuntimeException("Branch is required.");
        }

        if (schedule.getRecruiter() == null) {
            throw new RuntimeException("Recruiter is required.");
        }

        if (schedule.getScheduleDate() == null) {
            throw new RuntimeException("Schedule date is required.");
        }

        if (schedule.getInterviewMode() == null) {
            throw new RuntimeException("Interview mode is required.");
        }

        if (schedule.getStartTime() == null) {
            throw new RuntimeException("Start time is required.");
        }

        if (schedule.getEndTime() == null) {
            throw new RuntimeException("End time is required.");
        }

        if (schedule.getSlotCapacity() == null || schedule.getSlotCapacity() <= 0) {
            throw new RuntimeException("Slot capacity must be greater than zero.");
        }

        if (!schedule.getEndTime().isAfter(schedule.getStartTime())) {
            throw new RuntimeException("End time must be after start time.");
        }

        User recruiter = schedule.getRecruiter();

        if (recruiter.getBranch() == null || !recruiter.getBranch().getId().equals(schedule.getBranch().getId())) {
            throw new RuntimeException("Recruiter does not belong to selected branch.");
        }

        if (schedule.getBookedCount() > schedule.getSlotCapacity()) {
            throw new RuntimeException("Booked count cannot exceed slot capacity.");
        }
    }

    public void delete(Schedule schedule) {
        if (schedule.getBookedCount() > 0) {
            throw new RuntimeException("Cannot delete a schedule with booked applicants.");
        }

        scheduleRepository.delete(schedule);
    }

    public List<Schedule> findAvailable() {
        return scheduleRepository.findByActiveTrueAndStatus(ScheduleStatus.OPEN);
    }
}