package com.company.iss.evaluation.repository;

import com.company.iss.booking.entity.Booking;
import com.company.iss.booking.entity.BookingStatus;
import com.company.iss.booking.entity.InterviewStage;
import com.company.iss.booking.repository.BookingRepository;
import com.company.iss.applicant.entity.Applicant;
import com.company.iss.applicant.entity.ApplicantStatus;
import com.company.iss.applicant.repository.ApplicantRepository;
import com.company.iss.auth.entity.Role;
import com.company.iss.auth.entity.User;
import com.company.iss.auth.repository.UserRepository;
import com.company.iss.branch.entity.Branch;
import com.company.iss.branch.repository.BranchRepository;
import com.company.iss.client.entity.Client;
import com.company.iss.client.repository.ClientRepository;
import com.company.iss.evaluation.entity.InterviewEvaluation;
import com.company.iss.evaluation.entity.InterviewResult;
import com.company.iss.position.entity.EmploymentType;
import com.company.iss.position.entity.PositionOpening;
import com.company.iss.position.entity.PositionStatus;
import com.company.iss.position.repository.PositionOpeningRepository;
import com.company.iss.schedule.entity.InterviewMode;
import com.company.iss.schedule.entity.Schedule;
import com.company.iss.schedule.entity.ScheduleStatus;
import com.company.iss.schedule.repository.ScheduleRepository;
import com.company.iss.shared.pagination.OffsetLimitPageable;
import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
class InterviewEvaluationRepositoryTest {

    @Autowired InterviewEvaluationRepository evaluationRepository;
    @Autowired BookingRepository bookingRepository;
    @Autowired ApplicantRepository applicantRepository;
    @Autowired BranchRepository branchRepository;
    @Autowired ClientRepository clientRepository;
    @Autowired PositionOpeningRepository positionOpeningRepository;
    @Autowired ScheduleRepository scheduleRepository;
    @Autowired UserRepository userRepository;
    @Autowired EntityManager entityManager;

    @Test
    void gridQueryUsesApplicantBranchExactFiltersStablePagesAndFetchCountParity() {
        Branch north = branch("EVAL-N", "North Hub");
        Branch south = branch("EVAL-S", "South Hub");
        User northEvaluator = recruiter("north.evaluator@example.test", "Nora Evaluator", north);
        User southEvaluator = recruiter("south.evaluator@example.test", "Sam Evaluator", south);
        Client acme = client("Acme Evaluation Client");
        PositionOpening engineer = position("Platform Engineer", acme);
        PositionOpening analyst = position("Business Analyst", acme);

        InterviewEvaluation alex = evaluation(
                "alex", "Alex", "Alpha", north, engineer, northEvaluator, north,
                InterviewStage.INITIAL, InterviewResult.FOR_FINAL_INTERVIEW,
                LocalDateTime.of(2037, 2, 1, 9, 0)
        );
        InterviewEvaluation bea = evaluation(
                "bea", "Bea", "Beta", north, analyst, northEvaluator, north,
                InterviewStage.FINAL, InterviewResult.PASS,
                LocalDateTime.of(2037, 2, 2, 9, 0)
        );
        InterviewEvaluation carl = evaluation(
                "carl", "Carl", "Gamma", south, engineer, southEvaluator, south,
                InterviewStage.CLIENT, InterviewResult.FAIL,
                LocalDateTime.of(2037, 2, 3, 9, 0)
        );
        InterviewEvaluation mismatch = evaluation(
                "mismatch", "Mina", "Mismatch", north, engineer, northEvaluator, south,
                InterviewStage.CLIENT, InterviewResult.ON_HOLD,
                LocalDateTime.of(2037, 2, 4, 9, 0)
        );
        InterviewEvaluation legacyWithoutApplicant = legacyEvaluationWithoutApplicant(
                northEvaluator, LocalDateTime.of(2037, 2, 5, 9, 0)
        );
        entityManager.clear();

        Sort defaultSort = Sort.by(Sort.Order.desc("evaluationDate"), Sort.Order.desc("id"));
        List<InterviewEvaluation> northFirst = page(north.getId(), null, null, null, null, null,
                new OffsetLimitPageable(0, 2, defaultSort));
        List<InterviewEvaluation> northSecond = page(north.getId(), null, null, null, null, null,
                new OffsetLimitPageable(2, 2, defaultSort));
        List<InterviewEvaluation> nonAligned = page(null, null, null, null, null, null,
                new OffsetLimitPageable(1, 2, defaultSort));

        assertEquals(List.of(mismatch.getId(), bea.getId()), ids(northFirst));
        assertEquals(List.of(alex.getId()), ids(northSecond));
        assertEquals(List.of(mismatch.getId(), carl.getId()), ids(nonAligned));
        assertEquals(List.of(alex.getId(), bea.getId(), carl.getId(), mismatch.getId()), ids(page(
                null, "%acme evaluation%", null, null, null, null,
                new OffsetLimitPageable(0, 10, Sort.by(Sort.Order.asc("applicant.lastName")))
        )));
        assertEquals(3, count(north.getId(), null, null, null, null, null));
        assertEquals(1, count(south.getId(), null, null, null, null, null));
        assertTrue(ids(page(north.getId(), null, null, null, null, null,
                new OffsetLimitPageable(0, 20, defaultSort))).contains(mismatch.getId()));
        assertFalse(ids(page(south.getId(), null, null, null, null, null,
                new OffsetLimitPageable(0, 20, defaultSort))).contains(mismatch.getId()));

        List<InterviewEvaluation> adminRows = page(
                null, null, null, null, null, null, new OffsetLimitPageable(0, 20, defaultSort)
        );
        assertTrue(ids(adminRows).contains(legacyWithoutApplicant.getId()));
        assertEquals(adminRows.size(), count(null, null, null, null, null, null));
        List<InterviewEvaluation> recruiterRows = page(
                north.getId(), null, null, null, null, null, new OffsetLimitPageable(0, 20, defaultSort)
        );
        assertFalse(ids(recruiterRows).contains(legacyWithoutApplicant.getId()));
        assertEquals(recruiterRows.size(), count(north.getId(), null, null, null, null, null));

        assertEquals(List.of(alex.getId()), ids(page(null, "%alex alpha%", null, null, null, null, all())));
        assertEquals(4, page(null, "%acme evaluation%", null, null, null, null, all()).size());
        assertEquals(List.of(bea.getId()), ids(page(null, null, InterviewStage.FINAL,
                InterviewResult.PASS, null, null, all())));
        LocalDate exactDate = LocalDate.of(2037, 2, 3);
        assertEquals(List.of(carl.getId()), ids(page(null, null, null, null,
                exactDate.atStartOfDay(), exactDate.plusDays(1).atStartOfDay(), all())));
        assertEquals(0, count(null, "%missing%", null, null, null, null));

        long filteredCount = count(north.getId(), "%platform%", null, null, null, null);
        List<InterviewEvaluation> filtered = page(north.getId(), "%platform%", null, null,
                null, null, all());
        assertEquals(filteredCount, filtered.size());
        assertEquals(filtered.size(), ids(filtered).stream().distinct().count());

        InterviewEvaluation loaded = northFirst.getFirst();
        assertTrue(Hibernate.isInitialized(loaded.getApplicant()));
        assertTrue(Hibernate.isInitialized(loaded.getApplicant().getBranch()));
        assertTrue(Hibernate.isInitialized(loaded.getApplicant().getPositionOpening()));
        assertTrue(Hibernate.isInitialized(loaded.getApplicant().getPositionOpening().getClient()));
        assertTrue(Hibernate.isInitialized(loaded.getBooking()));
        assertTrue(Hibernate.isInitialized(loaded.getBooking().getSchedule()));
        assertTrue(Hibernate.isInitialized(loaded.getEvaluator()));
    }

    @Test
    void bookingCanHaveOnlyOneEvaluation() {
        Booking booking = new Booking();
        booking.setBookingReference("BK-UNIQUE-EVAL");
        booking.setStatus(BookingStatus.ATTENDED);
        booking = bookingRepository.saveAndFlush(booking);

        evaluationRepository.saveAndFlush(evaluation(booking, "first"));

        Booking persistedBooking = booking;
        assertThrows(
                DataIntegrityViolationException.class,
                () -> evaluationRepository.saveAndFlush(evaluation(persistedBooking, "second"))
        );
    }

    private InterviewEvaluation evaluation(Booking booking, String remarks) {
        InterviewEvaluation evaluation = new InterviewEvaluation();
        evaluation.setBooking(booking);
        evaluation.setCommunicationScore(8);
        evaluation.setTechnicalScore(8);
        evaluation.setAttitudeScore(8);
        evaluation.setResult(InterviewResult.PASS);
        evaluation.setRemarks(remarks);
        return evaluation;
    }

    private InterviewEvaluation evaluation(
            String key,
            String firstName,
            String lastName,
            Branch applicantBranch,
            PositionOpening position,
            User evaluator,
            Branch scheduleBranch,
            InterviewStage stage,
            InterviewResult result,
            LocalDateTime evaluationDate
    ) {
        Applicant applicant = new Applicant();
        applicant.setBranch(applicantBranch);
        applicant.setFirstName(firstName);
        applicant.setLastName(lastName);
        applicant.setEmail(key + "@candidate.test");
        applicant.setMobileNumber("09170000000");
        applicant.setPositionOpening(position);
        applicant.setStatus(ApplicantStatus.INTERVIEWED);
        applicant.setActive(true);
        applicant = applicantRepository.saveAndFlush(applicant);

        Schedule schedule = new Schedule();
        schedule.setBranch(scheduleBranch);
        schedule.setRecruiter(evaluator);
        schedule.setScheduleDate(evaluationDate.toLocalDate());
        schedule.setStartTime(LocalTime.of(8, 0));
        schedule.setEndTime(LocalTime.of(9, 0));
        schedule.setSlotCapacity(5);
        schedule.setBookedCount(1);
        schedule.setInterviewMode(InterviewMode.ONLINE);
        schedule.setStatus(ScheduleStatus.OPEN);
        schedule.setActive(true);
        schedule = scheduleRepository.saveAndFlush(schedule);

        Booking booking = Booking.forInterviewStage(stage);
        booking.setBookingReference("BK-EVAL-" + key.toUpperCase());
        booking.setApplicant(applicant);
        booking.setSchedule(schedule);
        booking.setRecruiter(evaluator);
        booking.setStatus(BookingStatus.ATTENDED);
        booking = bookingRepository.saveAndFlush(booking);

        InterviewEvaluation evaluation = evaluation(booking, key);
        evaluation.setApplicant(applicant);
        evaluation.setEvaluator(evaluator);
        evaluation.setResult(result);
        evaluation.setEvaluationDate(evaluationDate);
        return evaluationRepository.saveAndFlush(evaluation);
    }

    private InterviewEvaluation legacyEvaluationWithoutApplicant(User evaluator, LocalDateTime evaluationDate) {
        Booking booking = Booking.forInterviewStage(InterviewStage.INITIAL);
        booking.setBookingReference("BK-EVAL-LEGACY-NO-APPLICANT");
        booking.setStatus(BookingStatus.ATTENDED);
        booking = bookingRepository.saveAndFlush(booking);

        InterviewEvaluation evaluation = evaluation(booking, "legacy without applicant");
        evaluation.setApplicant(null);
        evaluation.setEvaluator(evaluator);
        evaluation.setEvaluationDate(evaluationDate);
        return evaluationRepository.saveAndFlush(evaluation);
    }

    private Branch branch(String code, String name) {
        Branch branch = new Branch();
        branch.setBranchCode(code);
        branch.setBranchName(name);
        branch.setAddress("Test address");
        branch.setCity("Test city");
        branch.setProvince("Test province");
        branch.setActive(true);
        return branchRepository.saveAndFlush(branch);
    }

    private User recruiter(String email, String name, Branch branch) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash("test-only-hash");
        user.setFullName(name);
        user.setRole(Role.RECRUITER);
        user.setBranch(branch);
        user.setActive(true);
        return userRepository.saveAndFlush(user);
    }

    private Client client(String name) {
        Client client = new Client();
        client.setCompanyName(name);
        client.setAddress("Test client address");
        client.setActive(true);
        return clientRepository.saveAndFlush(client);
    }

    private PositionOpening position(String title, Client client) {
        PositionOpening position = new PositionOpening();
        position.setTitle(title);
        position.setClient(client);
        position.setWorkLocation("Test location");
        position.setEmploymentType(EmploymentType.FULL_TIME);
        position.setRequiredHeadcount(5);
        position.setAppliedCount(0);
        position.setInterviewEvaluationCount(0);
        position.setPassedCount(0);
        position.setHiredCount(0);
        position.setStatus(PositionStatus.OPEN);
        position.setActive(true);
        return positionOpeningRepository.saveAndFlush(position);
    }

    private List<InterviewEvaluation> page(
            Long branchId,
            String keyword,
            InterviewStage stage,
            InterviewResult result,
            LocalDateTime from,
            LocalDateTime to,
            OffsetLimitPageable pageable
    ) {
        return evaluationRepository.findGridPage(branchId, keyword, stage, result, from, to, pageable);
    }

    private long count(
            Long branchId,
            String keyword,
            InterviewStage stage,
            InterviewResult result,
            LocalDateTime from,
            LocalDateTime to
    ) {
        return evaluationRepository.countGrid(branchId, keyword, stage, result, from, to);
    }

    private OffsetLimitPageable all() {
        return new OffsetLimitPageable(0, 20, Sort.by(Sort.Order.asc("evaluationDate"), Sort.Order.asc("id")));
    }

    private List<Long> ids(List<InterviewEvaluation> evaluations) {
        return evaluations.stream().map(InterviewEvaluation::getId).toList();
    }
}
