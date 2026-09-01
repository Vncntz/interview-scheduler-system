package com.company.iss.schedule.service;

import com.company.iss.auth.entity.Role;
import com.company.iss.auth.entity.User;
import com.company.iss.auth.repository.UserRepository;
import com.company.iss.auth.service.SecurityService;
import com.company.iss.branch.entity.Branch;
import com.company.iss.branch.repository.BranchRepository;
import com.company.iss.booking.repository.BookingRepository;
import com.company.iss.schedule.entity.InterviewMode;
import com.company.iss.schedule.entity.Schedule;
import com.company.iss.schedule.entity.ScheduleStatus;
import com.company.iss.schedule.dto.ScheduleGridFilter;
import com.company.iss.schedule.repository.ScheduleRepository;
import com.company.iss.shared.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduleServiceSecurityTest {

    @Mock ScheduleRepository scheduleRepository;
    @Mock BranchRepository branchRepository;
    @Mock UserRepository userRepository;
    @Mock SecurityService securityService;
    @Mock BookingRepository bookingRepository;

    private ScheduleService service;

    @BeforeEach
    void setUp() {
        service = new ScheduleService(
                scheduleRepository, branchRepository, userRepository, securityService, bookingRepository
        );
    }

    @Test
    void recruiterCannotUseAnyScheduleMasterDataEntryPoint() {
        Branch actorBranch = branch(1L);
        User recruiter = user(20L, Role.RECRUITER, actorBranch);
        Schedule detached = schedule(null, actorBranch, recruiter);
        when(securityService.requireOperationsUser()).thenReturn(recruiter);

        assertAll(
                () -> assertThrows(AccessDeniedException.class,
                        () -> service.findGridPage(ScheduleGridFilter.empty(), 0, 50, List.of())),
                () -> assertThrows(AccessDeniedException.class, () -> service.save(detached)),
                () -> assertThrows(AccessDeniedException.class, () -> service.activate(10L)),
                () -> assertThrows(AccessDeniedException.class, () -> service.deactivate(10L)),
                () -> assertThrows(AccessDeniedException.class, () -> service.close(10L)),
                () -> assertThrows(AccessDeniedException.class, () -> service.reopen(10L)),
                () -> assertThrows(AccessDeniedException.class, () -> service.cancel(10L)),
                () -> assertThrows(AccessDeniedException.class, () -> service.delete(10L)),
                () -> assertThrows(AccessDeniedException.class, () -> service.generateBulkSchedules(
                        1L, 20L, LocalDate.now(), LocalDate.now(), Set.of(DayOfWeek.MONDAY),
                        LocalTime.of(9, 0), LocalTime.of(10, 0), 30, 1, InterviewMode.ONSITE, null
                ))
        );

        verifyNoInteractions(scheduleRepository, branchRepository, userRepository);
    }

    @Test
    void recruiterCannotMutateCrossBranchDetachedSchedule() {
        Branch actorBranch = branch(1L);
        Branch otherBranch = branch(2L);
        User recruiter = user(20L, Role.RECRUITER, actorBranch);
        Schedule detached = schedule(10L, otherBranch, user(21L, Role.RECRUITER, otherBranch));
        when(securityService.requireOperationsUser()).thenReturn(recruiter);

        assertThrows(AccessDeniedException.class, () -> service.save(detached));

        verifyNoInteractions(scheduleRepository, branchRepository, userRepository);
    }

    @Test
    void adminCreateUsesAuthoritativeRelationshipsAndServerOwnedState() {
        User admin = user(99L, Role.ADMIN, null);
        Branch authoritativeBranch = branch(1L);
        User authoritativeRecruiter = user(20L, Role.RECRUITER, authoritativeBranch);
        Schedule input = schedule(null, branch(1L), user(20L, Role.RECRUITER, branch(999L)));
        input.setBookedCount(7);
        input.setStatus(ScheduleStatus.CANCELLED);
        input.setActive(false);

        when(securityService.requireOperationsUser()).thenReturn(admin);
        when(branchRepository.findById(1L)).thenReturn(Optional.of(authoritativeBranch));
        when(userRepository.findById(20L)).thenReturn(Optional.of(authoritativeRecruiter));
        when(scheduleRepository.findByRecruiterAndScheduleDate(
                authoritativeRecruiter, input.getScheduleDate())).thenReturn(List.of());
        when(scheduleRepository.save(any(Schedule.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Schedule result = service.save(input);

        assertSame(authoritativeBranch, result.getBranch());
        assertSame(authoritativeRecruiter, result.getRecruiter());
        assertEquals(0, result.getBookedCount());
        assertEquals(ScheduleStatus.OPEN, result.getStatus());
        assertTrue(result.isActive());
        verify(scheduleRepository).save(result);
    }

    @Test
    void adminUpdateReloadsLockedScheduleAndIgnoresDetachedWorkflowState() {
        User admin = user(99L, Role.ADMIN, null);
        Branch branch = branch(1L);
        User recruiter = user(20L, Role.RECRUITER, branch);
        Schedule existing = schedule(10L, branch, recruiter);
        existing.setBookedCount(1);
        existing.setStatus(ScheduleStatus.FULL);
        Schedule input = schedule(10L, branch(1L), user(20L, Role.RECRUITER, branch(1L)));
        input.setBookedCount(0);
        input.setStatus(ScheduleStatus.CANCELLED);
        input.setActive(false);
        input.setNotes("Updated");

        when(securityService.requireOperationsUser()).thenReturn(admin);
        when(branchRepository.findById(1L)).thenReturn(Optional.of(branch));
        when(userRepository.findById(20L)).thenReturn(Optional.of(recruiter));
        when(scheduleRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(existing));
        when(scheduleRepository.findByRecruiterAndScheduleDate(recruiter, input.getScheduleDate()))
                .thenReturn(List.of(existing));
        when(scheduleRepository.save(existing)).thenReturn(existing);

        Schedule result = service.save(input);

        assertSame(existing, result);
        assertEquals(1, result.getBookedCount());
        assertEquals(ScheduleStatus.FULL, result.getStatus());
        assertTrue(result.isActive());
        assertEquals("Updated", result.getNotes());
    }

    @Test
    void adminCannotChangeAppointmentFieldsWhenScheduleHasBookings() {
        User admin = user(99L, Role.ADMIN, null);
        Branch branch = branch(1L);
        User recruiter = user(20L, Role.RECRUITER, branch);
        Schedule existing = schedule(10L, branch, recruiter);
        existing.setBookedCount(0);
        Schedule input = schedule(10L, branch(1L), user(20L, Role.RECRUITER, branch(1L)));
        input.setStartTime(existing.getStartTime().plusHours(1));

        when(securityService.requireOperationsUser()).thenReturn(admin);
        when(branchRepository.findById(1L)).thenReturn(Optional.of(branch));
        when(userRepository.findById(20L)).thenReturn(Optional.of(recruiter));
        when(scheduleRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(existing));
        when(bookingRepository.existsByScheduleId(10L)).thenReturn(true);

        assertThrows(BusinessRuleViolationException.class, () -> service.save(input));

        verify(scheduleRepository, never()).save(any());
    }

    @Test
    void adminCloseReloadsAuthoritativeScheduleById() {
        User admin = user(99L, Role.ADMIN, null);
        Branch branch = branch(1L);
        Schedule schedule = schedule(10L, branch, user(20L, Role.RECRUITER, branch));
        when(securityService.requireOperationsUser()).thenReturn(admin);
        when(scheduleRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(schedule));

        service.close(10L);

        assertEquals(ScheduleStatus.CLOSED, schedule.getStatus());
        verify(scheduleRepository).save(schedule);
    }

    @Test
    void recruiterScheduleSelectionIsScopedToOwnBranch() {
        Branch branch = branch(1L);
        User recruiter = user(20L, Role.RECRUITER, branch);
        when(securityService.requireOperationsUser()).thenReturn(recruiter);
        when(scheduleRepository.findByBranchIdAndActiveTrueAndStatusOrderByScheduleDateAscStartTimeAsc(
                1L, ScheduleStatus.OPEN)).thenReturn(List.of());

        assertTrue(service.findAvailableForCurrentUser().isEmpty());

        verify(scheduleRepository).findByBranchIdAndActiveTrueAndStatusOrderByScheduleDateAscStartTimeAsc(
                1L, ScheduleStatus.OPEN);
        verify(scheduleRepository, never()).findByActiveTrueAndStatus(any());
    }

    @Test
    void recruiterCannotSelectSchedulesFromAnotherBranch() {
        when(securityService.requireOperationsUser()).thenReturn(user(20L, Role.RECRUITER, branch(1L)));

        assertThrows(AccessDeniedException.class, () -> service.findAvailableForCurrentUser(2L));

        verifyNoInteractions(scheduleRepository, branchRepository, userRepository);
    }

    private Schedule schedule(Long id, Branch branch, User recruiter) {
        Schedule schedule = new Schedule();
        schedule.setId(id);
        schedule.setBranch(branch);
        schedule.setRecruiter(recruiter);
        schedule.setScheduleDate(LocalDate.now().plusDays(2));
        schedule.setStartTime(LocalTime.of(9, 0));
        schedule.setEndTime(LocalTime.of(10, 0));
        schedule.setSlotCapacity(2);
        schedule.setBookedCount(0);
        schedule.setInterviewMode(InterviewMode.ONSITE);
        schedule.setStatus(ScheduleStatus.OPEN);
        schedule.setActive(true);
        return schedule;
    }

    private Branch branch(Long id) {
        Branch branch = new Branch();
        branch.setId(id);
        return branch;
    }

    private User user(Long id, Role role, Branch branch) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        user.setBranch(branch);
        user.setActive(true);
        return user;
    }
}
