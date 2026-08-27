package com.company.iss.booking.service;

import com.company.iss.applicant.entity.Applicant;
import com.company.iss.applicant.entity.ApplicantStatus;
import com.company.iss.applicant.service.ApplicantService;
import com.company.iss.auth.entity.Role;
import com.company.iss.auth.entity.User;
import com.company.iss.auth.service.SecurityService;
import com.company.iss.booking.dto.BookingRescheduleCommand;
import com.company.iss.booking.entity.Booking;
import com.company.iss.booking.entity.BookingRescheduleHistory;
import com.company.iss.booking.entity.BookingStatus;
import com.company.iss.booking.event.BookingCancelledEvent;
import com.company.iss.booking.event.BookingRescheduledEvent;
import com.company.iss.booking.exception.BookingCancellationException;
import com.company.iss.booking.exception.BookingRescheduleException;
import com.company.iss.booking.repository.BookingRepository;
import com.company.iss.booking.repository.BookingRescheduleHistoryRepository;
import com.company.iss.evaluation.repository.InterviewEvaluationRepository;
import com.company.iss.notification.entity.NotificationEvent;
import com.company.iss.notification.service.NotificationService;
import com.company.iss.schedule.entity.Schedule;
import com.company.iss.schedule.entity.ScheduleStatus;
import com.company.iss.schedule.repository.ScheduleRepository;
import com.company.iss.shared.exception.BusinessRuleViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private final NotificationService notificationService;
    private final BookingRepository bookingRepository;
    private final BookingRescheduleHistoryRepository bookingRescheduleHistoryRepository;
    private final InterviewEvaluationRepository interviewEvaluationRepository;
    private final ScheduleRepository scheduleRepository;
    private final ApplicantService applicantService;
    private final SecurityService securityService;
    private final ApplicationEventPublisher applicationEventPublisher;

    public BookingService(
            NotificationService notificationService,
            BookingRepository bookingRepository,
            BookingRescheduleHistoryRepository bookingRescheduleHistoryRepository,
            InterviewEvaluationRepository interviewEvaluationRepository,
            ScheduleRepository scheduleRepository,
            ApplicantService applicantService,
            SecurityService securityService,
            ApplicationEventPublisher applicationEventPublisher
    ) {
        this.notificationService = notificationService;
        this.bookingRepository = bookingRepository;
        this.bookingRescheduleHistoryRepository = bookingRescheduleHistoryRepository;
        this.interviewEvaluationRepository = interviewEvaluationRepository;
        this.scheduleRepository = scheduleRepository;
        this.applicantService = applicantService;
        this.securityService = securityService;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    public List<Booking> search(String keyword) {
        User actor = requireAuthorizedActor("You are not authorized to view bookings.");
        if (actor.getRole() == Role.ADMIN) {
            return keyword == null || keyword.isBlank()
                    ? bookingRepository.findAll()
                    : bookingRepository.search(keyword);
        }
        return keyword == null || keyword.isBlank()
                ? bookingRepository.findByScheduleBranchIdOrderByScheduleScheduleDateDescScheduleStartTimeDesc(
                        actor.getBranch().getId()
                )
                : bookingRepository.searchByBranchId(actor.getBranch().getId(), keyword);
    }

    @Transactional
    public Booking createBooking(Long applicantId, Long scheduleId, String remarks) {
        User actor = requireAuthorizedActor("You are not authorized to create bookings.");
        Applicant applicant = applicantService.findForBookingUpdate(applicantId, actor);
        if (scheduleId == null) {
            throw new BusinessRuleViolationException("Schedule is required.");
        }
        Schedule schedule = scheduleRepository.findByIdForUpdate(scheduleId)
                .orElseThrow(() -> new BusinessRuleViolationException("Schedule not found."));
        authorizeSchedule(actor, schedule, "You may only create bookings within your branch.");
        if (applicant.getBranch() == null || schedule.getBranch() == null
                || !Objects.equals(applicant.getBranch().getId(), schedule.getBranch().getId())) {
            throw new BusinessRuleViolationException("Applicant and schedule must belong to the same branch.");
        }
        return createBookingInternal(applicant, schedule, remarks);
    }

    @Deprecated(forRemoval = false)
    public Booking createBooking(Applicant applicant, Schedule schedule, String remarks) {
        if (applicant == null || applicant.getId() == null || schedule == null || schedule.getId() == null) {
            throw new BusinessRuleViolationException("Persisted applicant and schedule are required.");
        }
        return createBooking(applicant.getId(), schedule.getId(), remarks);
    }

    private Booking createBookingInternal(Applicant applicant, Schedule schedule, String remarks) {
        validateBooking(applicant, schedule);

        Booking booking = new Booking();

        booking.setApplicant(applicant);
        booking.setSchedule(schedule);
        booking.setRecruiter(schedule.getRecruiter());
        booking.setRemarks(remarks);
        booking.setStatus(BookingStatus.BOOKED);
        booking.setBookedDateTime(LocalDateTime.now());
        booking.setBookingReference(generateBookingReference());

        schedule.setBookedCount(schedule.getBookedCount() + 1);

        if (schedule.getBookedCount() >= schedule.getSlotCapacity()) {
            schedule.setStatus(ScheduleStatus.FULL);
        }

        scheduleRepository.save(schedule);

        applicantService.updateStatus(applicant, ApplicantStatus.SCHEDULED);

        Booking saved = bookingRepository.save(booking);

        log.info(
                "[BOOKING] Booking created bookingId={} applicantId={} scheduleId={}",
                saved.getId(),
                applicant.getId(),
                schedule.getId()
        );

        notificationService.send(NotificationEvent.BOOKING_CREATED, saved);

        return saved;
    }

    @Transactional
    public void markAttended(Long bookingId) {
        User actor = requireAuthorizedActor("You are not authorized to record attendance.");
        Booking booking = requireScopedBookingForUpdate(bookingId, actor);
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new BusinessRuleViolationException("Only confirmed interviews can be marked attended.");
        }
        booking.setStatus(BookingStatus.ATTENDED);
        booking.getApplicant().setStatus(ApplicantStatus.INTERVIEWED);
        bookingRepository.save(booking);
        log.info("[BOOKING] Booking marked attended bookingId={}", booking.getId());
    }

    @Transactional
    public void markNoShow(Long bookingId) {
        User actor = requireAuthorizedActor("You are not authorized to record attendance.");
        Booking booking = requireScopedBookingForUpdate(bookingId, actor);
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new BusinessRuleViolationException("Only confirmed interviews can be marked no-show.");
        }
        booking.setStatus(BookingStatus.NO_SHOW);
        bookingRepository.save(booking);
        log.info("[BOOKING] Booking marked no-show bookingId={}", booking.getId());
    }

    public boolean canReschedule(Booking booking) {
        if (booking == null || !isReschedulableStatus(booking.getStatus())) {
            return false;
        }

        User actor = securityService.getCurrentUser();
        if (actor == null || !actor.isActive()) {
            return false;
        }

        return actor.getRole() == Role.ADMIN
                || (actor.getRole() == Role.RECRUITER && isSameBranch(actor, booking.getSchedule()));
    }

    @Transactional(readOnly = true)
    public Booking findScopedById(Long bookingId) {
        User actor = requireAuthorizedActor("You are not authorized to view this booking.");
        Booking booking = bookingRepository.findDetailedById(bookingId)
                .orElseThrow(() -> new BusinessRuleViolationException("Booking not found."));
        authorizeSchedule(actor, booking.getSchedule(), "You may only view interviews within your branch.");
        return booking;
    }

    public List<Schedule> findEligibleRescheduleDestinations(Long bookingId) {
        if (bookingId == null) {
            throw new BookingRescheduleException("Booking is required.");
        }

        User actor = requireAuthorizedActor();
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingRescheduleException("Booking was not found."));

        validateReschedulableBooking(booking);
        authorizeSchedule(actor, booking.getSchedule());

        if (booking.getApplicant().getBranch() == null
                || booking.getApplicant().getBranch().getId() == null) {
            throw new BookingRescheduleException("The applicant is not assigned to a branch.");
        }

        LocalDateTime now = LocalDateTime.now();
        Long branchId = booking.getApplicant().getBranch().getId();

        return scheduleRepository.findEligibleRescheduleDestinations(
                booking.getSchedule().getId(),
                now.toLocalDate(),
                now.toLocalTime(),
                ScheduleStatus.OPEN,
                branchId
        );
    }

    @Transactional
    public Booking reschedule(BookingRescheduleCommand command) {
        validateCommand(command);

        User actor = requireAuthorizedActor();
        Booking booking = bookingRepository.findByIdForUpdate(command.bookingId())
                .orElseThrow(() -> new BookingRescheduleException("Booking was not found."));

        validateReschedulableBooking(booking);
        authorizeSchedule(actor, booking.getSchedule());

        Long sourceScheduleId = booking.getSchedule().getId();
        if (Objects.equals(sourceScheduleId, command.destinationScheduleId())) {
            throw new BookingRescheduleException("Select a different destination schedule.");
        }

        List<Long> scheduleIds = List.of(sourceScheduleId, command.destinationScheduleId()).stream()
                .sorted(Comparator.naturalOrder())
                .toList();
        List<Schedule> lockedSchedules = scheduleRepository.findAllByIdForUpdate(scheduleIds);
        if (lockedSchedules.size() != 2) {
            throw new BookingRescheduleException("The source or destination schedule was not found.");
        }

        Map<Long, Schedule> schedulesById = lockedSchedules.stream()
                .collect(Collectors.toMap(Schedule::getId, Function.identity()));
        Schedule sourceSchedule = schedulesById.get(sourceScheduleId);
        Schedule destinationSchedule = schedulesById.get(command.destinationScheduleId());
        if (sourceSchedule == null || destinationSchedule == null) {
            throw new BookingRescheduleException("The source or destination schedule was not found.");
        }

        authorizeSchedule(actor, sourceSchedule);
        authorizeSchedule(actor, destinationSchedule);
        validateApplicantScheduleBranch(booking.getApplicant(), destinationSchedule);
        validateDestination(destinationSchedule, LocalDateTime.now());
        validateSourceCapacity(sourceSchedule);

        sourceSchedule.setBookedCount(sourceSchedule.getBookedCount() - 1);
        if (sourceSchedule.getStatus() == ScheduleStatus.FULL) {
            sourceSchedule.setStatus(ScheduleStatus.OPEN);
        }

        destinationSchedule.setBookedCount(destinationSchedule.getBookedCount() + 1);
        if (destinationSchedule.getBookedCount() >= destinationSchedule.getSlotCapacity()) {
            destinationSchedule.setStatus(ScheduleStatus.FULL);
        }

        booking.setSchedule(destinationSchedule);
        booking.setRecruiter(destinationSchedule.getRecruiter());

        scheduleRepository.saveAll(List.of(sourceSchedule, destinationSchedule));
        Booking saved = bookingRepository.save(booking);
        bookingRescheduleHistoryRepository.save(new BookingRescheduleHistory(
                saved,
                sourceSchedule,
                destinationSchedule,
                actor,
                LocalDateTime.now(),
                command.reason().trim()
        ));

        applicationEventPublisher.publishEvent(new BookingRescheduledEvent(saved.getId()));

        log.info(
                "[BOOKING] Booking rescheduled bookingId={} sourceScheduleId={} destinationScheduleId={} actorId={}",
                saved.getId(),
                sourceSchedule.getId(),
                destinationSchedule.getId(),
                actor.getId()
        );

        return saved;
    }

    @Transactional
    public void cancel(Long bookingId) {
        if (bookingId == null) {
            throw new BookingCancellationException("Booking is required.");
        }

        User actor = requireAuthorizedActor("You are not authorized to cancel interviews.");
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new BookingCancellationException("Booking was not found."));

        authorizeSchedule(actor, booking.getSchedule(), "You may only cancel interviews within your branch.");

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            return;
        }

        if (!isCancellableStatus(booking.getStatus())) {
            throw new BookingCancellationException("Only booked or confirmed interviews can be cancelled.");
        }

        if (interviewEvaluationRepository.existsByBookingId(booking.getId())) {
            throw new BookingCancellationException("Evaluated interviews cannot be cancelled.");
        }

        if (booking.getSchedule() == null || booking.getSchedule().getId() == null) {
            throw new BookingCancellationException("The booking does not have a valid schedule.");
        }

        Schedule schedule = scheduleRepository.findByIdForUpdate(booking.getSchedule().getId())
                .orElseThrow(() -> new BookingCancellationException("The booking schedule was not found."));
        authorizeSchedule(actor, schedule, "You may only cancel interviews within your branch.");

        if (schedule.getBookedCount() != null && schedule.getBookedCount() > 0) {
            schedule.setBookedCount(schedule.getBookedCount() - 1);
        }

        if (schedule.getStatus() == ScheduleStatus.FULL) {
            schedule.setStatus(ScheduleStatus.OPEN);
        }

        booking.setStatus(BookingStatus.CANCELLED);

        scheduleRepository.save(schedule);
        Booking saved = bookingRepository.save(booking);
        applicationEventPublisher.publishEvent(new BookingCancelledEvent(saved.getId()));

        log.info(
                "[BOOKING] Booking cancelled bookingId={} applicantId={} scheduleId={} actorId={}",
                saved.getId(),
                saved.getApplicant() == null ? null : saved.getApplicant().getId(),
                schedule.getId(),
                actor.getId()
        );
    }

    private boolean isCancellableStatus(BookingStatus status) {
        return status == BookingStatus.BOOKED || status == BookingStatus.CONFIRMED;
    }

    private void validateBooking(Applicant applicant, Schedule schedule) {

        if (applicant == null) {
            throw new BusinessRuleViolationException("Applicant is required.");
        }

        if (!applicant.isActive()) {
            throw new BusinessRuleViolationException("Applicant is inactive.");
        }

        Optional<Booking> activeBooking = bookingRepository.findFirstByApplicantAndStatusIn(applicant, List.of(BookingStatus.BOOKED, BookingStatus.CONFIRMED, BookingStatus.RESCHEDULED));

        if (activeBooking.isPresent()) {
            throw new BusinessRuleViolationException("Applicant already has an active booking.");
        }

        if (schedule == null) {
            throw new BusinessRuleViolationException("Schedule is required.");
        }

        if (!schedule.isActive()) {
            throw new BusinessRuleViolationException("Schedule is inactive.");
        }

        if (schedule.getStatus() != ScheduleStatus.OPEN) {
            throw new BusinessRuleViolationException("Schedule is not open.");
        }

        if (schedule.getBookedCount() >= schedule.getSlotCapacity()) {
            throw new BusinessRuleViolationException("Schedule is already full.");
        }

        Optional<Booking> existing = bookingRepository.findByApplicantAndSchedule(applicant, schedule);

        if (existing.isPresent()) {
            throw new BusinessRuleViolationException("Applicant is already booked for this schedule.");
        }
    }

    private void validateCommand(BookingRescheduleCommand command) {
        if (command == null || command.bookingId() == null) {
            throw new BookingRescheduleException("Booking is required.");
        }
        if (command.destinationScheduleId() == null) {
            throw new BookingRescheduleException("Destination schedule is required.");
        }
        if (command.reason() == null || command.reason().isBlank()) {
            throw new BookingRescheduleException("Reschedule reason is required.");
        }
        if (command.reason().trim().length() > 1000) {
            throw new BookingRescheduleException("Reschedule reason must not exceed 1000 characters.");
        }
    }

    private void validateReschedulableBooking(Booking booking) {
        if (!isReschedulableStatus(booking.getStatus())) {
            throw new BookingRescheduleException("Only booked or confirmed interviews can be rescheduled.");
        }
        if (booking.getApplicant() == null || !booking.getApplicant().isActive()) {
            throw new BookingRescheduleException("The applicant is inactive or unavailable.");
        }
        if (booking.getApplicant().getStatus() != ApplicantStatus.SCHEDULED) {
            throw new BookingRescheduleException("Only scheduled applicants can be rescheduled.");
        }
        if (booking.getSchedule() == null || booking.getSchedule().getId() == null) {
            throw new BookingRescheduleException("The booking does not have a valid source schedule.");
        }
        if (booking.getId() != null && interviewEvaluationRepository.existsByBookingId(booking.getId())) {
            throw new BookingRescheduleException("Evaluated interviews cannot be rescheduled.");
        }
    }

    private boolean isReschedulableStatus(BookingStatus status) {
        return status == BookingStatus.BOOKED || status == BookingStatus.CONFIRMED;
    }

    private User requireAuthorizedActor() {
        return requireAuthorizedActor("You are not authorized to reschedule interviews.");
    }

    private User requireAuthorizedActor(String unauthorizedMessage) {
        User actor = securityService.getCurrentUser();
        if (actor == null || !actor.isActive()) {
            throw new AccessDeniedException("An active authenticated user is required.");
        }
        if (actor.getRole() != Role.ADMIN && actor.getRole() != Role.RECRUITER) {
            throw new AccessDeniedException(unauthorizedMessage);
        }
        if (actor.getRole() == Role.RECRUITER
                && (actor.getBranch() == null || actor.getBranch().getId() == null)) {
            throw new AccessDeniedException("Your recruiter account is not assigned to a branch.");
        }
        return actor;
    }

    private void authorizeSchedule(User actor, Schedule schedule) {
        authorizeSchedule(actor, schedule, "You may only reschedule interviews within your branch.");
    }

    private void authorizeSchedule(User actor, Schedule schedule, String outOfScopeMessage) {
        if (actor.getRole() == Role.ADMIN) {
            return;
        }
        if (!isSameBranch(actor, schedule)) {
            throw new AccessDeniedException(outOfScopeMessage);
        }
    }

    private boolean isSameBranch(User actor, Schedule schedule) {
        return actor.getBranch() != null
                && schedule != null
                && schedule.getBranch() != null
                && Objects.equals(actor.getBranch().getId(), schedule.getBranch().getId());
    }

    private void validateDestination(Schedule destinationSchedule, LocalDateTime now) {
        if (!destinationSchedule.isActive()) {
            throw new BookingRescheduleException("The destination schedule is inactive.");
        }
        if (destinationSchedule.getStatus() != ScheduleStatus.OPEN) {
            throw new BookingRescheduleException("The destination schedule is not open.");
        }
        if (destinationSchedule.getSlotCapacity() == null
                || destinationSchedule.getBookedCount() == null
                || destinationSchedule.getBookedCount() >= destinationSchedule.getSlotCapacity()) {
            throw new BookingRescheduleException("The destination schedule is full.");
        }
        if (destinationSchedule.getRecruiter() == null) {
            throw new BookingRescheduleException("The destination schedule does not have a recruiter.");
        }
        LocalDate scheduleDate = destinationSchedule.getScheduleDate();
        LocalTime startTime = destinationSchedule.getStartTime();
        if (scheduleDate == null
                || startTime == null
                || scheduleDate.isBefore(now.toLocalDate())
                || (scheduleDate.equals(now.toLocalDate()) && !startTime.isAfter(now.toLocalTime()))) {
            throw new BookingRescheduleException("The destination schedule must be in the future.");
        }
    }

    private void validateApplicantScheduleBranch(Applicant applicant, Schedule schedule) {
        if (applicant == null || applicant.getBranch() == null || schedule == null || schedule.getBranch() == null
                || !Objects.equals(applicant.getBranch().getId(), schedule.getBranch().getId())) {
            throw new BookingRescheduleException("Applicant and destination schedule must belong to the same branch.");
        }
    }

    private void validateSourceCapacity(Schedule sourceSchedule) {
        if (sourceSchedule.getBookedCount() == null || sourceSchedule.getBookedCount() <= 0) {
            throw new BookingRescheduleException("The source schedule has an invalid booked count.");
        }
    }

    private String generateBookingReference() {
        return "BK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    @Transactional
    public void confirm(Long bookingId) {
        User actor = requireAuthorizedActor("You are not authorized to confirm interviews.");
        Booking booking = requireScopedBookingForUpdate(bookingId, actor);
        if (booking.getStatus() != BookingStatus.BOOKED) {
            throw new BusinessRuleViolationException("Only booked interviews can be confirmed.");
        }
        booking.setStatus(BookingStatus.CONFIRMED);

        Booking saved = bookingRepository.save(booking);

        log.info("[BOOKING] Booking confirmed bookingId={}", saved.getId());

        notificationService.send(NotificationEvent.BOOKING_CONFIRMED, saved);
    }

    private Booking requireScopedBookingForUpdate(Long bookingId, User actor) {
        if (bookingId == null) {
            throw new BusinessRuleViolationException("Booking is required.");
        }
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new BusinessRuleViolationException("Booking not found."));
        authorizeSchedule(actor, booking.getSchedule(), "You may only manage interviews within your branch.");
        return booking;
    }
}
