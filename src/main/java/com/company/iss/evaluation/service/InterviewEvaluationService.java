package com.company.iss.evaluation.service;

import com.company.iss.applicant.entity.Applicant;
import com.company.iss.applicant.entity.ApplicantStatus;
import com.company.iss.auth.entity.Role;
import com.company.iss.auth.entity.User;
import com.company.iss.auth.service.SecurityService;
import com.company.iss.booking.entity.Booking;
import com.company.iss.booking.entity.BookingStatus;
import com.company.iss.booking.entity.InterviewStage;
import com.company.iss.booking.repository.BookingRepository;
import com.company.iss.evaluation.dto.CreateEvaluationCommand;
import com.company.iss.evaluation.entity.InterviewEvaluation;
import com.company.iss.evaluation.entity.InterviewResult;
import com.company.iss.evaluation.repository.InterviewEvaluationRepository;
import com.company.iss.shared.exception.BusinessRuleViolationException;
import com.company.iss.position.entity.PositionOpening;
import com.company.iss.position.repository.PositionOpeningRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class InterviewEvaluationService {

    private final InterviewEvaluationRepository evaluationRepository;
    private final PositionOpeningRepository positionOpeningRepository;
    private final BookingRepository bookingRepository;
    private final SecurityService securityService;
    private final InterviewStageResultPolicy interviewStageResultPolicy = new InterviewStageResultPolicy();

    public InterviewEvaluationService(
            InterviewEvaluationRepository evaluationRepository,
            PositionOpeningRepository positionOpeningRepository,
            BookingRepository bookingRepository,
            SecurityService securityService
    ) {
        this.evaluationRepository = evaluationRepository;
        this.positionOpeningRepository = positionOpeningRepository;
        this.bookingRepository = bookingRepository;
        this.securityService = securityService;
    }

    @Transactional
    public InterviewEvaluation create(CreateEvaluationCommand command) {
        validateCommand(command);
        User actor = securityService.requireOperationsUser();
        Booking booking = bookingRepository.findByIdForUpdate(command.bookingId())
                .orElseThrow(() -> new BusinessRuleViolationException("Booking not found."));
        authorize(actor, booking);

        if (booking.getStatus() != BookingStatus.ATTENDED) {
            throw new BusinessRuleViolationException("Only attended bookings can be evaluated.");
        }
        if (evaluationRepository.existsByBookingId(booking.getId())) {
            throw new BusinessRuleViolationException("Booking already has an evaluation.");
        }
        interviewStageResultPolicy.validate(booking.getInterviewStage(), command.result());

        Applicant applicant = booking.getApplicant();
        if (applicant == null) {
            throw new BusinessRuleViolationException("Booking does not have an applicant.");
        }

        InterviewEvaluation evaluation = new InterviewEvaluation();
        evaluation.setBooking(booking);
        evaluation.setApplicant(applicant);
        evaluation.setEvaluator(actor);
        evaluation.setCommunicationScore(command.communicationScore());
        evaluation.setTechnicalScore(command.technicalScore());
        evaluation.setAttitudeScore(command.attitudeScore());
        evaluation.setResult(command.result());
        evaluation.setRemarks(command.remarks());
        evaluation.setEvaluationDate(LocalDateTime.now());

        applyResult(booking, applicant, command.result());
        updatePositionCounters(applicant, command.result());
        bookingRepository.save(booking);
        return evaluationRepository.save(evaluation);
    }

    /** Compatibility entry point for existing UI callers; identity is always derived from the current user. */
    public InterviewEvaluation save(InterviewEvaluation evaluation) {
        if (evaluation == null || evaluation.getBooking() == null) {
            throw new BusinessRuleViolationException("Booking is required.");
        }
        return create(new CreateEvaluationCommand(
                evaluation.getBooking().getId(),
                evaluation.getCommunicationScore(),
                evaluation.getTechnicalScore(),
                evaluation.getAttitudeScore(),
                evaluation.getResult(),
                evaluation.getRemarks()
        ));
    }

    @Transactional(readOnly = true)
    public List<InterviewEvaluation> findAll() {
        User actor = securityService.requireOperationsUser();
        if (actor.getRole() == Role.ADMIN) {
            return evaluationRepository.findAll();
        }
        return evaluationRepository.findByBookingScheduleBranchIdOrderByEvaluationDateDesc(
                actor.getBranch().getId()
        );
    }

    public List<InterviewResult> allowedResults(InterviewStage interviewStage) {
        return interviewStageResultPolicy.allowedResults(interviewStage);
    }

    private void validateCommand(CreateEvaluationCommand command) {
        if (command == null || command.bookingId() == null) {
            throw new BusinessRuleViolationException("Booking is required.");
        }
        validateScore(command.communicationScore());
        validateScore(command.technicalScore());
        validateScore(command.attitudeScore());
        if (command.result() == null) {
            throw new BusinessRuleViolationException("Interview result is required.");
        }
    }

    private void validateScore(Integer score) {
        if (score == null || score < 1 || score > 10) {
            throw new BusinessRuleViolationException("Scores must be between 1 and 10.");
        }
    }

    private void authorize(User actor, Booking booking) {
        if (actor.getRole() == Role.ADMIN) {
            return;
        }
        if (booking.getSchedule() == null || booking.getSchedule().getBranch() == null
                || !Objects.equals(actor.getBranch().getId(), booking.getSchedule().getBranch().getId())) {
            throw new AccessDeniedException("You may only evaluate interviews within your branch.");
        }
    }

    private void applyResult(Booking booking, Applicant applicant, InterviewResult result) {
        switch (result) {
            case PASS -> {
                applicant.setStatus(ApplicantStatus.PASSED);
                booking.setStatus(BookingStatus.PASSED);
            }
            case FAIL -> {
                applicant.setStatus(ApplicantStatus.FAILED);
                booking.setStatus(BookingStatus.FAILED);
            }
            case FOR_FINAL_INTERVIEW -> {
                applicant.setStatus(ApplicantStatus.FOR_FINAL_INTERVIEW);
                booking.setStatus(BookingStatus.FOR_FINAL_INTERVIEW);
            }
            case FOR_CLIENT_INTERVIEW -> {
                applicant.setStatus(ApplicantStatus.FOR_CLIENT_INTERVIEW);
                booking.setStatus(BookingStatus.FOR_CLIENT_INTERVIEW);
            }
            case ON_HOLD -> {
                applicant.setStatus(ApplicantStatus.ON_HOLD);
                booking.setStatus(BookingStatus.ON_HOLD);
            }
        }
    }

    private void updatePositionCounters(Applicant applicant, InterviewResult result) {
        PositionOpening position = applicant.getPositionOpening();
        if (position == null) {
            return;
        }
        position.setInterviewEvaluationCount(position.getInterviewEvaluationCount() + 1);
        if (result == InterviewResult.PASS) {
            position.setPassedCount(position.getPassedCount() + 1);
        }
        positionOpeningRepository.save(position);
    }
}
