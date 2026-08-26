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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
class ScheduleRepositoryTest {

    @Autowired
    private ScheduleRepository scheduleRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private UserRepository userRepository;

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
        Schedule schedule = new Schedule();
        schedule.setBranch(branch);
        schedule.setRecruiter(recruiter);
        schedule.setScheduleDate(date);
        schedule.setStartTime(LocalTime.of(11, 0));
        schedule.setEndTime(LocalTime.of(12, 0));
        schedule.setSlotCapacity(capacity);
        schedule.setBookedCount(bookedCount);
        schedule.setInterviewMode(InterviewMode.ONLINE);
        schedule.setStatus(status);
        schedule.setActive(active);
        return scheduleRepository.saveAndFlush(schedule);
    }
}
