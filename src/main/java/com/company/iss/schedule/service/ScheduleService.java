package com.company.iss.schedule.service;

import com.company.iss.auth.entity.Role;
import com.company.iss.auth.entity.User;
import com.company.iss.auth.repository.UserRepository;
import com.company.iss.auth.service.SecurityService;
import com.company.iss.branch.entity.Branch;
import com.company.iss.branch.repository.BranchRepository;
import com.company.iss.booking.repository.BookingRepository;
import com.company.iss.schedule.dto.BulkScheduleResult;
import com.company.iss.schedule.dto.ScheduleGridFilter;
import com.company.iss.schedule.dto.ScheduleGridSortOrder;
import com.company.iss.schedule.entity.InterviewMode;
import com.company.iss.schedule.entity.Schedule;
import com.company.iss.schedule.entity.ScheduleStatus;
import com.company.iss.schedule.repository.ScheduleRepository;
import com.company.iss.shared.exception.BusinessRuleViolationException;
import com.company.iss.shared.pagination.OffsetLimitPageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class ScheduleService {

    private static final int MAX_GRID_PAGE_SIZE = 100;

    private final ScheduleRepository scheduleRepository;
    private final BranchRepository branchRepository;
    private final UserRepository userRepository;
    private final SecurityService securityService;
    private final BookingRepository bookingRepository;

    public ScheduleService(
            ScheduleRepository scheduleRepository,
            BranchRepository branchRepository,
            UserRepository userRepository,
            SecurityService securityService,
            BookingRepository bookingRepository
    ) {
        this.scheduleRepository = scheduleRepository;
        this.branchRepository = branchRepository;
        this.userRepository = userRepository;
        this.securityService = securityService;
        this.bookingRepository = bookingRepository;
    }

    @Transactional(readOnly = true)
    public List<Schedule> findGridPage(
            ScheduleGridFilter filter,
            int offset,
            int limit,
            List<ScheduleGridSortOrder> sortOrders
    ) {
        requireAdmin();
        validateGridWindow(offset, limit);
        ScheduleKeywordCriteria criteria = scheduleCriteria(filter);
        return scheduleRepository.findGridPage(
                criteria.keywordPattern(),
                criteria.matchesMode(InterviewMode.ONSITE),
                criteria.matchesMode(InterviewMode.ONLINE),
                criteria.matchesMode(InterviewMode.PHONE),
                criteria.matchesStatus(ScheduleStatus.OPEN),
                criteria.matchesStatus(ScheduleStatus.FULL),
                criteria.matchesStatus(ScheduleStatus.CLOSED),
                criteria.matchesStatus(ScheduleStatus.CANCELLED),
                new OffsetLimitPageable(offset, limit, scheduleSort(sortOrders))
        );
    }

    @Transactional(readOnly = true)
    public long countGrid(ScheduleGridFilter filter) {
        requireAdmin();
        ScheduleKeywordCriteria criteria = scheduleCriteria(filter);
        return scheduleRepository.countGrid(
                criteria.keywordPattern(),
                criteria.matchesMode(InterviewMode.ONSITE),
                criteria.matchesMode(InterviewMode.ONLINE),
                criteria.matchesMode(InterviewMode.PHONE),
                criteria.matchesStatus(ScheduleStatus.OPEN),
                criteria.matchesStatus(ScheduleStatus.FULL),
                criteria.matchesStatus(ScheduleStatus.CLOSED),
                criteria.matchesStatus(ScheduleStatus.CANCELLED)
        );
    }

    @Transactional
    public Schedule save(Schedule input) {
        requireAdmin();
        if (input == null) {
            throw new BusinessRuleViolationException("Schedule is required.");
        }
        Branch branch = requireBranch(input.getBranch());
        User recruiter = requireRecruiter(input.getRecruiter());
        Schedule schedule;
        if (input.getId() == null) {
            schedule = new Schedule();
            schedule.setStatus(ScheduleStatus.OPEN);
            schedule.setBookedCount(0);
            schedule.setActive(true);
        } else {
            schedule = requireScheduleForUpdate(input.getId());
            preventBookedScheduleOwnershipChange(schedule, branch, recruiter);
            preventBookedScheduleAppointmentChange(schedule, input);
        }
        copyEditableFields(input, schedule, branch, recruiter);
        validate(schedule);
        validateNoOverlap(schedule);
        return scheduleRepository.save(schedule);
    }

    @Transactional
    public void activate(Long scheduleId) {
        requireAdmin();
        Schedule schedule = requireScheduleForUpdate(scheduleId);
        schedule.setActive(true);
        scheduleRepository.save(schedule);
    }

    @Transactional
    public void deactivate(Long scheduleId) {
        requireAdmin();
        Schedule schedule = requireScheduleForUpdate(scheduleId);
        schedule.setActive(false);
        scheduleRepository.save(schedule);
    }

    @Transactional
    public void close(Long scheduleId) {
        requireAdmin();
        Schedule schedule = requireScheduleForUpdate(scheduleId);
        schedule.setStatus(ScheduleStatus.CLOSED);
        scheduleRepository.save(schedule);
    }

    @Transactional
    public void reopen(Long scheduleId) {
        requireAdmin();
        Schedule schedule = requireScheduleForUpdate(scheduleId);
        schedule.setStatus(schedule.getBookedCount() >= schedule.getSlotCapacity()
                ? ScheduleStatus.FULL
                : ScheduleStatus.OPEN);
        scheduleRepository.save(schedule);
    }

    @Transactional
    public void cancel(Long scheduleId) {
        requireAdmin();
        Schedule schedule = requireScheduleForUpdate(scheduleId);
        if (schedule.getBookedCount() > 0) {
            throw new BusinessRuleViolationException("Cannot cancel a booked schedule.");
        }
        schedule.setStatus(ScheduleStatus.CANCELLED);
        scheduleRepository.save(schedule);
    }

    @Transactional
    public BulkScheduleResult generateBulkSchedules(
            Long branchId,
            Long recruiterId,
            LocalDate startDate,
            LocalDate endDate,
            Set<DayOfWeek> selectedDays,
            LocalTime workStart,
            LocalTime workEnd,
            Integer intervalMinutes,
            Integer slotCapacity,
            InterviewMode interviewMode,
            String notes
    ) {
        requireAdmin();
        Branch branch = requireBranch(branchId);
        User recruiter = requireRecruiter(recruiterId);
        validateBulkInput(branch, recruiter, startDate, endDate, selectedDays, workStart, workEnd,
                intervalMinutes, slotCapacity, interviewMode);

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
                    if (hasOverlap(recruiter, currentDate, slotStart, slotEnd, null)) {
                        result.setSkippedCount(result.getSkippedCount() + 1);
                    } else {
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
                    }
                    slotStart = slotEnd;
                }
            }
            currentDate = currentDate.plusDays(1);
        }
        return result;
    }

    @Transactional
    public void delete(Long scheduleId) {
        requireAdmin();
        Schedule schedule = requireScheduleForUpdate(scheduleId);
        if (schedule.getBookedCount() > 0) {
            throw new BusinessRuleViolationException("Cannot delete a schedule with booked applicants.");
        }
        scheduleRepository.delete(schedule);
    }

    @Transactional(readOnly = true)
    public List<Schedule> findAvailableForCurrentUser() {
        User actor = securityService.requireOperationsUser();
        if (actor.getRole() == Role.ADMIN) {
            return scheduleRepository.findByActiveTrueAndStatus(ScheduleStatus.OPEN);
        }
        return scheduleRepository.findByBranchIdAndActiveTrueAndStatusOrderByScheduleDateAscStartTimeAsc(
                actor.getBranch().getId(), ScheduleStatus.OPEN);
    }

    @Transactional(readOnly = true)
    public List<Schedule> findAvailableForCurrentUser(Long branchId) {
        User actor = securityService.requireOperationsUser();
        if (branchId == null) {
            return List.of();
        }
        if (actor.getRole() == Role.RECRUITER && !Objects.equals(actor.getBranch().getId(), branchId)) {
            throw new AccessDeniedException("You may only view schedules within your branch.");
        }
        return scheduleRepository.findByBranchIdAndActiveTrueAndStatusOrderByScheduleDateAscStartTimeAsc(
                branchId, ScheduleStatus.OPEN);
    }

    private User requireAdmin() {
        User actor = securityService.requireOperationsUser();
        if (actor.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Only an active administrator may manage schedules.");
        }
        return actor;
    }

    private void validateGridWindow(int offset, int limit) {
        if (offset < 0) {
            throw new IllegalArgumentException("Grid offset must not be negative.");
        }
        if (limit < 1 || limit > MAX_GRID_PAGE_SIZE) {
            throw new IllegalArgumentException("Grid limit must be between 1 and " + MAX_GRID_PAGE_SIZE + ".");
        }
    }

    private ScheduleKeywordCriteria scheduleCriteria(ScheduleGridFilter filter) {
        ScheduleGridFilter normalized = filter == null ? ScheduleGridFilter.empty() : filter;
        String keyword = normalized.keyword();
        if (keyword == null) {
            return new ScheduleKeywordCriteria(null, Set.of(), Set.of());
        }
        Set<InterviewMode> modes = java.util.Arrays.stream(InterviewMode.values())
                .filter(mode -> mode.name().toLowerCase(java.util.Locale.ROOT).contains(keyword))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Set<ScheduleStatus> statuses = java.util.Arrays.stream(ScheduleStatus.values())
                .filter(status -> status.name().toLowerCase(java.util.Locale.ROOT).contains(keyword))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return new ScheduleKeywordCriteria("%" + keyword + "%", modes, statuses);
    }

    private Sort scheduleSort(List<ScheduleGridSortOrder> sortOrders) {
        if (sortOrders == null || sortOrders.isEmpty()) {
            return Sort.by(
                    Sort.Order.asc("scheduleDate"),
                    Sort.Order.asc("startTime"),
                    Sort.Order.asc("id")
            );
        }
        List<Sort.Order> orders = sortOrders.stream()
                .map(order -> new Sort.Order(order.direction(), order.field().property()))
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        orders.add(Sort.Order.asc("id"));
        return Sort.by(orders);
    }

    private record ScheduleKeywordCriteria(
            String keywordPattern,
            Set<InterviewMode> modes,
            Set<ScheduleStatus> statuses
    ) {
        private boolean matchesMode(InterviewMode mode) {
            return modes.contains(mode);
        }

        private boolean matchesStatus(ScheduleStatus status) {
            return statuses.contains(status);
        }
    }

    private Schedule requireScheduleForUpdate(Long scheduleId) {
        if (scheduleId == null) {
            throw new BusinessRuleViolationException("Schedule is required.");
        }
        return scheduleRepository.findByIdForUpdate(scheduleId)
                .orElseThrow(() -> new BusinessRuleViolationException("Schedule not found."));
    }

    private Branch requireBranch(Branch requestedBranch) {
        return requireBranch(requestedBranch == null ? null : requestedBranch.getId());
    }

    private Branch requireBranch(Long branchId) {
        if (branchId == null) {
            throw new BusinessRuleViolationException("Branch is required.");
        }
        return branchRepository.findById(branchId)
                .orElseThrow(() -> new BusinessRuleViolationException("Branch not found."));
    }

    private User requireRecruiter(User requestedRecruiter) {
        return requireRecruiter(requestedRecruiter == null ? null : requestedRecruiter.getId());
    }

    private User requireRecruiter(Long recruiterId) {
        if (recruiterId == null) {
            throw new BusinessRuleViolationException("Recruiter is required.");
        }
        User recruiter = userRepository.findById(recruiterId)
                .orElseThrow(() -> new BusinessRuleViolationException("Recruiter not found."));
        if (recruiter.getRole() != Role.RECRUITER) {
            throw new BusinessRuleViolationException("Selected user is not a recruiter.");
        }
        return recruiter;
    }

    private void copyEditableFields(Schedule input, Schedule schedule, Branch branch, User recruiter) {
        schedule.setBranch(branch);
        schedule.setRecruiter(recruiter);
        schedule.setScheduleDate(input.getScheduleDate());
        schedule.setStartTime(input.getStartTime());
        schedule.setEndTime(input.getEndTime());
        schedule.setSlotCapacity(input.getSlotCapacity());
        schedule.setInterviewMode(input.getInterviewMode());
        schedule.setNotes(input.getNotes());
    }

    private void preventBookedScheduleOwnershipChange(Schedule existing, Branch branch, User recruiter) {
        if (existing.getBookedCount() == null || existing.getBookedCount() <= 0) {
            return;
        }
        if (!Objects.equals(existing.getBranch().getId(), branch.getId())
                || !Objects.equals(existing.getRecruiter().getId(), recruiter.getId())) {
            throw new BusinessRuleViolationException("A booked schedule cannot be reassigned to another branch or recruiter.");
        }
    }

    private void preventBookedScheduleAppointmentChange(Schedule existing, Schedule input) {
        boolean hasRecordedCapacity = existing.getBookedCount() != null && existing.getBookedCount() > 0;
        if (!hasRecordedCapacity && !bookingRepository.existsByScheduleId(existing.getId())) {
            return;
        }
        if (!Objects.equals(existing.getScheduleDate(), input.getScheduleDate())
                || !Objects.equals(existing.getStartTime(), input.getStartTime())
                || !Objects.equals(existing.getEndTime(), input.getEndTime())
                || existing.getInterviewMode() != input.getInterviewMode()) {
            throw new BusinessRuleViolationException(
                    "A schedule with bookings cannot change its appointment date, time, or interview mode. Reschedule each booking instead."
            );
        }
    }

    private void validate(Schedule schedule) {
        if (schedule.getScheduleDate() == null) {
            throw new BusinessRuleViolationException("Schedule date is required.");
        }
        if (schedule.getInterviewMode() == null) {
            throw new BusinessRuleViolationException("Interview mode is required.");
        }
        if (schedule.getStartTime() == null) {
            throw new BusinessRuleViolationException("Start time is required.");
        }
        if (schedule.getEndTime() == null) {
            throw new BusinessRuleViolationException("End time is required.");
        }
        if (schedule.getSlotCapacity() == null || schedule.getSlotCapacity() <= 0) {
            throw new BusinessRuleViolationException("Slot capacity must be greater than zero.");
        }
        if (!schedule.getEndTime().isAfter(schedule.getStartTime())) {
            throw new BusinessRuleViolationException("End time must be after start time.");
        }
        if (schedule.getRecruiter().getBranch() == null
                || !Objects.equals(schedule.getRecruiter().getBranch().getId(), schedule.getBranch().getId())) {
            throw new BusinessRuleViolationException("Recruiter does not belong to selected branch.");
        }
        if (schedule.getBookedCount() == null || schedule.getBookedCount() > schedule.getSlotCapacity()) {
            throw new BusinessRuleViolationException("Booked count cannot exceed slot capacity.");
        }
    }

    private void validateNoOverlap(Schedule schedule) {
        if (hasOverlap(schedule.getRecruiter(), schedule.getScheduleDate(), schedule.getStartTime(),
                schedule.getEndTime(), schedule.getId())) {
            throw new BusinessRuleViolationException("Recruiter already has an overlapping schedule.");
        }
    }

    private boolean hasOverlap(User recruiter, LocalDate date, LocalTime startTime, LocalTime endTime,
                               Long excludedScheduleId) {
        return scheduleRepository.findByRecruiterAndScheduleDate(recruiter, date).stream()
                .filter(existing -> !Objects.equals(existing.getId(), excludedScheduleId))
                .anyMatch(existing -> startTime.isBefore(existing.getEndTime())
                        && endTime.isAfter(existing.getStartTime()));
    }

    private void validateBulkInput(
            Branch branch,
            User recruiter,
            LocalDate startDate,
            LocalDate endDate,
            Set<DayOfWeek> selectedDays,
            LocalTime workStart,
            LocalTime workEnd,
            Integer intervalMinutes,
            Integer slotCapacity,
            InterviewMode interviewMode
    ) {
        if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
            throw new BusinessRuleViolationException("A valid date range is required.");
        }
        if (selectedDays == null || selectedDays.isEmpty()) {
            throw new BusinessRuleViolationException("Please select at least one day.");
        }
        if (workStart == null || workEnd == null || !workEnd.isAfter(workStart)) {
            throw new BusinessRuleViolationException("End time must be after start time.");
        }
        if (intervalMinutes == null || intervalMinutes <= 0) {
            throw new BusinessRuleViolationException("Interval must be greater than zero.");
        }
        if (slotCapacity == null || slotCapacity <= 0) {
            throw new BusinessRuleViolationException("Slot capacity must be greater than zero.");
        }
        if (interviewMode == null) {
            throw new BusinessRuleViolationException("Interview mode is required.");
        }
        if (recruiter.getBranch() == null || !Objects.equals(recruiter.getBranch().getId(), branch.getId())) {
            throw new BusinessRuleViolationException("Recruiter does not belong to selected branch.");
        }
    }
}
