package com.company.iss.schedule.repository;

import com.company.iss.auth.entity.Role;
import com.company.iss.auth.entity.User;
import com.company.iss.auth.repository.UserRepository;
import com.company.iss.branch.entity.Branch;
import com.company.iss.branch.repository.BranchRepository;
import com.company.iss.schedule.entity.InterviewMode;
import com.company.iss.schedule.entity.Schedule;
import com.company.iss.schedule.entity.ScheduleStatus;
import org.junit.jupiter.api.Test;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Sort;
import com.company.iss.shared.pagination.OffsetLimitPageable;
import jakarta.persistence.EntityManager;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class ScheduleRepositoryTest {

    @Autowired
    private ScheduleRepository scheduleRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void gridQueryPaginatesExactWindowsFiltersSortsAndKeepsCountInParity() {
        Branch north = saveBranch("NORTH");
        Branch south = saveBranch("SOUTH");
        User alice = saveRecruiter("alice@example.test", north);
        User bob = saveRecruiter("bob@example.test", south);
        Schedule first = saveSchedule(north, alice, LocalDate.of(2036, 1, 1), 0, 3,
                ScheduleStatus.OPEN, true, InterviewMode.ONSITE, LocalTime.of(9, 0));
        Schedule second = saveSchedule(north, alice, LocalDate.of(2036, 1, 2), 0, 3,
                ScheduleStatus.CLOSED, true, InterviewMode.ONLINE, LocalTime.of(9, 0));
        Schedule third = saveSchedule(south, bob, LocalDate.of(2036, 1, 3), 0, 3,
                ScheduleStatus.FULL, true, InterviewMode.PHONE, LocalTime.of(9, 0));
        Schedule fourth = saveSchedule(south, bob, LocalDate.of(2036, 1, 4), 0, 3,
                ScheduleStatus.CANCELLED, true, InterviewMode.ONLINE, LocalTime.of(9, 0));
        entityManager.clear();

        List<Schedule> firstPage = grid(null, false, false, false, false, false, false, false,
                new OffsetLimitPageable(0, 2, Sort.by("scheduleDate")));
        List<Schedule> secondPage = grid(null, false, false, false, false, false, false, false,
                new OffsetLimitPageable(2, 2, Sort.by("scheduleDate")));
        List<Schedule> nonAligned = grid(null, false, false, false, false, false, false, false,
                new OffsetLimitPageable(1, 2, Sort.by("scheduleDate")));

        assertEquals(List.of(first.getId(), second.getId()), ids(firstPage));
        assertEquals(List.of(third.getId(), fourth.getId()), ids(secondPage));
        assertEquals(List.of(second.getId(), third.getId()), ids(nonAligned));
        assertEquals(List.of(fourth.getId(), third.getId(), second.getId(), first.getId()), ids(grid(
                null, false, false, false, false, false, false, false,
                new OffsetLimitPageable(0, 10, Sort.by(Sort.Order.desc("scheduleDate")))
        )));
        assertTrue(Hibernate.isInitialized(firstPage.getFirst().getBranch()));
        assertTrue(Hibernate.isInitialized(firstPage.getFirst().getRecruiter()));

        assertEquals(List.of(first.getId(), second.getId()), ids(grid("%north%", false, false, false,
                false, false, false, false, pageable())));
        assertEquals(List.of(first.getId(), second.getId()), ids(grid("%alice%", false, false, false,
                false, false, false, false, pageable())));
        assertEquals(List.of(second.getId(), fourth.getId()), ids(grid("%online%", false, true, false,
                false, false, false, false, pageable())));
        assertEquals(List.of(third.getId()), ids(grid("%full%", false, false, false,
                false, true, false, false, pageable())));

        long northCount = count("%north%", false, false, false, false, false, false, false);
        assertEquals(northCount, grid("%north%", false, false, false, false, false, false, false,
                pageable()).size());
        assertEquals(0, count("%missing%", false, false, false, false, false, false, false));
    }

    @Test
    void eligibleRescheduleQueryFiltersAndLockQueryUsesStableIdOrder() {
        Branch inScopeBranch = saveBranch("RSC-A");
        Branch otherBranch = saveBranch("RSC-B");
        User inScopeRecruiter = saveRecruiter("reschedule-a@example.test", inScopeBranch);
        User otherRecruiter = saveRecruiter("reschedule-b@example.test", otherBranch);
        LocalDate today = LocalDate.of(2035, 1, 10);
        LocalTime now = LocalTime.of(10, 0);

        Schedule source = saveSchedule(inScopeBranch, inScopeRecruiter, today.plusDays(1), 0, 2, ScheduleStatus.OPEN, true);
        Schedule eligible = saveSchedule(inScopeBranch, inScopeRecruiter, today.plusDays(2), 1, 2, ScheduleStatus.OPEN, true);
        saveSchedule(inScopeBranch, inScopeRecruiter, today.plusDays(2), 2, 2, ScheduleStatus.FULL, true);
        saveSchedule(inScopeBranch, inScopeRecruiter, today.minusDays(1), 0, 2, ScheduleStatus.OPEN, true);
        saveSchedule(otherBranch, otherRecruiter, today.plusDays(2), 0, 2, ScheduleStatus.OPEN, true);

        List<Schedule> destinations = scheduleRepository.findEligibleRescheduleDestinations(
                source.getId(),
                today,
                now,
                ScheduleStatus.OPEN,
                inScopeBranch.getId()
        );

        assertEquals(List.of(eligible.getId()), destinations.stream().map(Schedule::getId).toList());

        List<Schedule> locked = scheduleRepository.findAllByIdForUpdate(List.of(source.getId(), eligible.getId()));
        assertEquals(
                List.of(source.getId(), eligible.getId()).stream().sorted().toList(),
                locked.stream().map(Schedule::getId).toList()
        );
    }

    private Branch saveBranch(String code) {
        Branch branch = new Branch();
        branch.setBranchCode(code);
        branch.setBranchName(code);
        branch.setAddress("Test address");
        branch.setCity("Test city");
        branch.setProvince("Test province");
        branch.setActive(true);
        return branchRepository.saveAndFlush(branch);
    }

    private User saveRecruiter(String email, Branch branch) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash("test-only-hash");
        user.setFullName(email);
        user.setRole(Role.RECRUITER);
        user.setBranch(branch);
        user.setActive(true);
        return userRepository.saveAndFlush(user);
    }

    private Schedule saveSchedule(
            Branch branch,
            User recruiter,
            LocalDate date,
            int bookedCount,
            int capacity,
            ScheduleStatus status,
            boolean active
    ) {
        return saveSchedule(branch, recruiter, date, bookedCount, capacity, status, active,
                InterviewMode.ONLINE, LocalTime.of(11, 0));
    }

    private Schedule saveSchedule(
            Branch branch,
            User recruiter,
            LocalDate date,
            int bookedCount,
            int capacity,
            ScheduleStatus status,
            boolean active,
            InterviewMode mode,
            LocalTime startTime
    ) {
        Schedule schedule = new Schedule();
        schedule.setBranch(branch);
        schedule.setRecruiter(recruiter);
        schedule.setScheduleDate(date);
        schedule.setStartTime(startTime);
        schedule.setEndTime(startTime.plusHours(1));
        schedule.setSlotCapacity(capacity);
        schedule.setBookedCount(bookedCount);
        schedule.setInterviewMode(mode);
        schedule.setStatus(status);
        schedule.setActive(active);
        return scheduleRepository.saveAndFlush(schedule);
    }

    private List<Schedule> grid(
            String keyword,
            boolean onsite,
            boolean online,
            boolean phone,
            boolean open,
            boolean full,
            boolean closed,
            boolean cancelled,
            OffsetLimitPageable pageable
    ) {
        return scheduleRepository.findGridPage(
                keyword, onsite, online, phone, open, full, closed, cancelled, pageable
        );
    }

    private long count(
            String keyword,
            boolean onsite,
            boolean online,
            boolean phone,
            boolean open,
            boolean full,
            boolean closed,
            boolean cancelled
    ) {
        return scheduleRepository.countGrid(keyword, onsite, online, phone, open, full, closed, cancelled);
    }

    private OffsetLimitPageable pageable() {
        return new OffsetLimitPageable(0, 20, Sort.by("scheduleDate"));
    }

    private List<Long> ids(List<Schedule> schedules) {
        return schedules.stream().map(Schedule::getId).toList();
    }
}
