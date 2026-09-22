package com.company.iss.evaluation.service;

import com.company.iss.applicant.entity.Applicant;
import com.company.iss.applicant.entity.ApplicantStatus;
import com.company.iss.applicant.repository.ApplicantRepository;
import com.company.iss.applicant.service.ApplicantService;
import com.company.iss.auth.entity.Role;
import com.company.iss.auth.entity.User;
import com.company.iss.auth.repository.UserRepository;
import com.company.iss.booking.entity.Booking;
import com.company.iss.booking.entity.BookingStatus;
import com.company.iss.booking.entity.InterviewStage;
import com.company.iss.booking.repository.BookingRepository;
import com.company.iss.booking.service.BookingService;
import com.company.iss.branch.entity.Branch;
import com.company.iss.branch.repository.BranchRepository;
import com.company.iss.client.entity.Client;
import com.company.iss.client.repository.ClientRepository;
import com.company.iss.evaluation.dto.CreateEvaluationCommand;
import com.company.iss.evaluation.entity.InterviewResult;
import com.company.iss.evaluation.repository.InterviewEvaluationRepository;
import com.company.iss.position.entity.EmploymentType;
import com.company.iss.position.entity.PositionOpening;
import com.company.iss.position.entity.PositionStatus;
import com.company.iss.position.repository.PositionOpeningRepository;
import com.company.iss.schedule.entity.InterviewMode;
import com.company.iss.schedule.entity.Schedule;
import com.company.iss.schedule.entity.ScheduleStatus;
import com.company.iss.schedule.repository.ScheduleRepository;
import com.company.iss.shared.exception.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@SpringBootTest
@ActiveProfiles("mysql-it")
@Testcontainers
class BranchTransferEvaluationConcurrencyIT {

    private static final int OPERATION_TIMEOUT_SECONDS = 20;

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4.6"))
            .withEnv("MYSQL_ROOT_HOST", "%");

    @Autowired ApplicantService applicantService;
    @Autowired BookingService bookingService;
    @Autowired InterviewEvaluationService evaluationService;
    @Autowired ApplicantRepository applicantRepository;
    @Autowired BookingRepository bookingRepository;
    @Autowired InterviewEvaluationRepository evaluationRepository;
    @Autowired BranchRepository branchRepository;
    @Autowired ClientRepository clientRepository;
    @Autowired PositionOpeningRepository positionRepository;
    @Autowired ScheduleRepository scheduleRepository;
    @Autowired UserRepository userRepository;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    @Timeout(60)
    void transferFirstMakesWaitingOldBranchEvaluationReauthorizeAgainstDestinationBranch() throws Exception {
        Fixture fixture = createFixture("TRANSFER-FIRST");
        CountDownLatch transferLockAcquired = new CountDownLatch(1);
        CountDownLatch finishTransfer = new CountDownLatch(1);
        CountDownLatch evaluationStarted = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<Void> transfer = null;
        Future<RuntimeException> oldBranchEvaluation = null;
        Throwable primaryFailure = null;

        try {
            transfer = executor.submit(() -> asUser(fixture.adminEmail(), () -> {
                inTransaction(() -> {
                    applicantRepository.findByIdForUpdate(fixture.applicantId()).orElseThrow();
                    transferLockAcquired.countDown();
                    await(finishTransfer, "release the transfer transaction");
                    applicantService.save(transferInput(fixture));
                    return null;
                });
                return null;
            }));

            assertTrue(transferLockAcquired.await(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            oldBranchEvaluation = executor.submit(() -> asUser(
                    fixture.recruiterAEmail(),
                    () -> {
                        evaluationStarted.countDown();
                        try {
                            evaluationService.create(command(fixture.bookingId()));
                            return null;
                        } catch (RuntimeException exception) {
                            return exception;
                        }
                    }
            ));

            assertTrue(evaluationStarted.await(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            awaitApplicantLockWait(oldBranchEvaluation);
            finishTransfer.countDown();

            transfer.get(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            RuntimeException denial = oldBranchEvaluation.get(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertInstanceOf(AccessDeniedException.class, denial);
        } catch (Exception | Error failure) {
            primaryFailure = failure;
            throw failure;
        } finally {
            finishTransfer.countDown();
            AssertionError cleanupFailure = terminateExecutor(executor, transfer, oldBranchEvaluation);
            if (cleanupFailure != null) {
                if (primaryFailure != null) {
                    primaryFailure.addSuppressed(cleanupFailure);
                } else {
                    throw cleanupFailure;
                }
            }
        }

        assertUnevaluatedState(fixture, fixture.branchBId());

        asUser(fixture.recruiterBEmail(), () -> evaluationService.create(command(fixture.bookingId())));
        asUser(fixture.recruiterBEmail(), () -> {
            assertThrows(
                    BusinessRuleViolationException.class,
                    () -> evaluationService.create(command(fixture.bookingId()))
            );
            return null;
        });

        assertEvaluatedState(fixture);
    }

    @Test
    @Timeout(60)
    void evaluationFirstCommitsBeforeWaitingTransferWithoutLosingEvaluationState() throws Exception {
        Fixture fixture = createFixture("EVALUATION-FIRST");
        CountDownLatch evaluationLockAcquired = new CountDownLatch(1);
        CountDownLatch finishEvaluation = new CountDownLatch(1);
        CountDownLatch transferStarted = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<Void> evaluation = null;
        Future<Void> transfer = null;
        Throwable primaryFailure = null;

        try {
            evaluation = executor.submit(() -> asUser(fixture.recruiterAEmail(), () -> {
                inTransaction(() -> {
                    applicantRepository.findByIdAndBranchIdForUpdate(
                            fixture.applicantId(), fixture.branchAId()
                    ).orElseThrow();
                    evaluationLockAcquired.countDown();
                    await(finishEvaluation, "release the evaluation transaction");
                    evaluationService.create(command(fixture.bookingId()));
                    return null;
                });
                return null;
            }));

            assertTrue(evaluationLockAcquired.await(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            transfer = executor.submit(() -> asUser(fixture.adminEmail(), () -> {
                transferStarted.countDown();
                applicantService.save(transferInput(fixture));
                return null;
            }));

            assertTrue(transferStarted.await(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            awaitApplicantLockWait(transfer);
            finishEvaluation.countDown();

            evaluation.get(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            transfer.get(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception | Error failure) {
            primaryFailure = failure;
            throw failure;
        } finally {
            finishEvaluation.countDown();
            AssertionError cleanupFailure = terminateExecutor(executor, evaluation, transfer);
            if (cleanupFailure != null) {
                if (primaryFailure != null) {
                    primaryFailure.addSuppressed(cleanupFailure);
                } else {
                    throw cleanupFailure;
                }
            }
        }

        asUser(fixture.recruiterAEmail(), () -> {
            assertThrows(
                    AccessDeniedException.class,
                    () -> evaluationService.create(command(fixture.bookingId()))
            );
            return null;
        });
        asUser(fixture.recruiterBEmail(), () -> {
            assertEquals(fixture.bookingId(), bookingService.findScopedById(fixture.bookingId()).getId());
            assertThrows(
                    BusinessRuleViolationException.class,
                    () -> evaluationService.create(command(fixture.bookingId()))
            );
            return null;
        });

        assertEvaluatedState(fixture);
    }

    private AssertionError terminateExecutor(ExecutorService executor, Future<?>... futures) {
        for (Future<?> future : futures) {
            if (future != null && !future.isDone()) {
                future.cancel(true);
            }
        }
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                return new AssertionError("Timed out waiting for concurrency-test workers to terminate.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new AssertionError("Interrupted while terminating concurrency-test workers.", exception);
        }
        return null;
    }

    private void awaitApplicantLockWait(Future<?> waitingOperation) {
        JdbcTemplate lockMonitor = new JdbcTemplate(new DriverManagerDataSource(
                MYSQL.getJdbcUrl(), "root", MYSQL.getPassword()
        ));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(OPERATION_TIMEOUT_SECONDS);
        while (System.nanoTime() < deadline) {
            Integer applicantWaits = lockMonitor.queryForObject(
                    """
                    select count(*)
                    from performance_schema.data_lock_waits waits
                    join performance_schema.data_locks requested
                      on requested.engine = waits.engine
                     and requested.engine_lock_id = waits.requesting_engine_lock_id
                     and requested.engine_transaction_id = waits.requesting_engine_transaction_id
                    where requested.object_schema = database()
                      and requested.object_name = 'applicants'
                    """,
                    Integer.class
            );
            if (applicantWaits != null && applicantWaits > 0) {
                return;
            }
            if (waitingOperation.isDone()) {
                fail("The competing operation completed instead of waiting for the applicant row lock.");
            }
            Thread.onSpinWait();
        }
        fail("Timed out waiting for MySQL to report an applicant row-lock wait.");
    }

    private Fixture createFixture(String key) {
        return inTransaction(() -> {
            Branch branchA = saveBranch(key + "-A");
            Branch branchB = saveBranch(key + "-B");
            User recruiterA = saveUser(key.toLowerCase() + "-a@example.test", Role.RECRUITER, branchA);
            User recruiterB = saveUser(key.toLowerCase() + "-b@example.test", Role.RECRUITER, branchB);
            User admin = saveUser(key.toLowerCase() + "-admin@example.test", Role.ADMIN, null);
            PositionOpening position = savePosition(key);
            Schedule schedule = saveSchedule(branchA, recruiterA);
            Applicant applicant = saveApplicant(key, branchA, position);
            Booking booking = saveAttendedBooking(key, applicant, schedule);
            return new Fixture(
                    applicant.getId(), booking.getId(), position.getId(),
                    branchA.getId(), branchB.getId(), recruiterA.getId(),
                    recruiterA.getEmail(), recruiterB.getEmail(), admin.getEmail(),
                    applicant.getFirstName(), applicant.getLastName(), applicant.getEmail(),
                    applicant.getMobileNumber(), applicant.getSource(), applicant.getRemarks()
            );
        });
    }

    private Applicant transferInput(Fixture fixture) {
        Applicant input = new Applicant();
        input.setId(fixture.applicantId());
        input.setFirstName(fixture.firstName());
        input.setLastName(fixture.lastName());
        input.setEmail(fixture.applicantEmail());
        input.setMobileNumber(fixture.mobileNumber());
        input.setSource(fixture.source());
        input.setRemarks(fixture.remarks());
        Branch destination = new Branch();
        destination.setId(fixture.branchBId());
        input.setBranch(destination);
        PositionOpening position = new PositionOpening();
        position.setId(fixture.positionId());
        input.setPositionOpening(position);
        return input;
    }

    private void assertUnevaluatedState(Fixture fixture, Long expectedBranchId) {
        inTransaction(() -> {
            Booking booking = bookingRepository.findDetailedById(fixture.bookingId()).orElseThrow();
            Applicant applicant = applicantRepository.findById(fixture.applicantId()).orElseThrow();
            PositionOpening position = positionRepository.findById(fixture.positionId()).orElseThrow();
            assertEquals(BookingStatus.ATTENDED, booking.getStatus());
            assertEquals(ApplicantStatus.INTERVIEWED, applicant.getStatus());
            assertEquals(expectedBranchId, applicant.getBranch().getId());
            assertEquals(fixture.branchAId(), booking.getSchedule().getBranch().getId());
            assertEquals(fixture.recruiterAId(), booking.getSchedule().getRecruiter().getId());
            assertEquals(0, position.getInterviewEvaluationCount());
            assertEquals(0, position.getPassedCount());
            assertTrue(evaluationRepository.findByApplicantIdOrderByEvaluationDateAscIdAsc(
                    fixture.applicantId()
            ).isEmpty());
            return null;
        });
    }

    private void assertEvaluatedState(Fixture fixture) {
        inTransaction(() -> {
            Booking booking = bookingRepository.findDetailedById(fixture.bookingId()).orElseThrow();
            Applicant applicant = applicantRepository.findById(fixture.applicantId()).orElseThrow();
            PositionOpening position = positionRepository.findById(fixture.positionId()).orElseThrow();
            assertEquals(BookingStatus.PASSED, booking.getStatus());
            assertEquals(ApplicantStatus.PASSED, applicant.getStatus());
            assertEquals(fixture.branchBId(), applicant.getBranch().getId());
            assertEquals(fixture.branchAId(), booking.getSchedule().getBranch().getId());
            assertEquals(fixture.recruiterAId(), booking.getSchedule().getRecruiter().getId());
            assertEquals(InterviewStage.INITIAL, booking.getInterviewStage());
            assertEquals(1, position.getInterviewEvaluationCount());
            assertEquals(1, position.getPassedCount());
            assertEquals(1, evaluationRepository.findByApplicantIdOrderByEvaluationDateAscIdAsc(
                    fixture.applicantId()
            ).size());
            return null;
        });
    }

    private CreateEvaluationCommand command(Long bookingId) {
        return new CreateEvaluationCommand(bookingId, 8, 9, 7, InterviewResult.PASS, "Concurrency result");
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

    private User saveUser(String email, Role role, Branch branch) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash("test-only-hash");
        user.setFullName(email);
        user.setRole(role);
        user.setBranch(branch);
        user.setActive(true);
        return userRepository.saveAndFlush(user);
    }

    private PositionOpening savePosition(String key) {
        Client client = new Client();
        client.setCompanyName(key + " Client");
        client.setAddress("Test address");
        client.setActive(true);
        client = clientRepository.saveAndFlush(client);

        PositionOpening position = new PositionOpening();
        position.setTitle(key + " Position");
        position.setClient(client);
        position.setWorkLocation("Test location");
        position.setEmploymentType(EmploymentType.FULL_TIME);
        position.setRequiredHeadcount(2);
        position.setAppliedCount(1);
        position.setInterviewEvaluationCount(0);
        position.setPassedCount(0);
        position.setHiredCount(0);
        position.setStatus(PositionStatus.OPEN);
        position.setActive(true);
        return positionRepository.saveAndFlush(position);
    }

    private Schedule saveSchedule(Branch branch, User recruiter) {
        Schedule schedule = new Schedule();
        schedule.setBranch(branch);
        schedule.setRecruiter(recruiter);
        schedule.setScheduleDate(LocalDate.of(2035, 6, 14));
        schedule.setStartTime(LocalTime.of(9, 0));
        schedule.setEndTime(LocalTime.of(10, 0));
        schedule.setSlotCapacity(2);
        schedule.setBookedCount(1);
        schedule.setInterviewMode(InterviewMode.ONLINE);
        schedule.setStatus(ScheduleStatus.OPEN);
        schedule.setActive(true);
        return scheduleRepository.saveAndFlush(schedule);
    }

    private Applicant saveApplicant(String key, Branch branch, PositionOpening position) {
        Applicant applicant = new Applicant();
        applicant.setFirstName(key);
        applicant.setLastName("Candidate");
        applicant.setEmail(key.toLowerCase() + "-candidate@example.test");
        applicant.setMobileNumber("09170000000");
        applicant.setBranch(branch);
        applicant.setPositionOpening(position);
        applicant.setSource("Concurrency test");
        applicant.setRemarks(key);
        applicant.setStatus(ApplicantStatus.INTERVIEWED);
        applicant.setActive(true);
        return applicantRepository.saveAndFlush(applicant);
    }

    private Booking saveAttendedBooking(String key, Applicant applicant, Schedule schedule) {
        Booking booking = Booking.forInterviewStage(InterviewStage.INITIAL);
        booking.setBookingReference("BK-" + key);
        booking.setApplicant(applicant);
        booking.setSchedule(schedule);
        booking.setRecruiter(schedule.getRecruiter());
        booking.setStatus(BookingStatus.ATTENDED);
        booking.setBookedDateTime(LocalDateTime.of(2035, 6, 1, 9, 0));
        return bookingRepository.saveAndFlush(booking);
    }

    private <T> T inTransaction(Supplier<T> work) {
        return new TransactionTemplate(transactionManager).execute(status -> work.get());
    }

    private <T> T asUser(String email, Callable<T> work) throws Exception {
        SecurityContext previous = SecurityContextHolder.getContext();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(email, "N/A", List.of()));
        SecurityContextHolder.setContext(context);
        try {
            return work.call();
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }

    private void await(CountDownLatch latch, String operation) {
        try {
            if (!latch.await(OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to " + operation + ".");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting to " + operation + ".", exception);
        }
    }

    private record Fixture(
            Long applicantId,
            Long bookingId,
            Long positionId,
            Long branchAId,
            Long branchBId,
            Long recruiterAId,
            String recruiterAEmail,
            String recruiterBEmail,
            String adminEmail,
            String firstName,
            String lastName,
            String applicantEmail,
            String mobileNumber,
            String source,
            String remarks
    ) {
    }
}
