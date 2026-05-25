package com.company.iss.evaluation.service;

import com.company.iss.applicant.entity.ApplicantStatus;
import com.company.iss.applicant.service.ApplicantService;
import com.company.iss.booking.entity.Booking;
import com.company.iss.booking.entity.BookingStatus;
import com.company.iss.booking.repository.BookingRepository;
import com.company.iss.evaluation.entity.InterviewEvaluation;
import com.company.iss.evaluation.entity.InterviewResult;
import com.company.iss.evaluation.repository.InterviewEvaluationRepository;
import com.company.iss.position.entity.PositionOpening;
import com.company.iss.position.service.PositionOpeningService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class InterviewEvaluationService {

    @Autowired
    private InterviewEvaluationRepository interviewEvaluationRepository;

    @Autowired
    private PositionOpeningService positionOpeningService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private ApplicantService applicantService;

    public InterviewEvaluation save(InterviewEvaluation evaluation) {
        validate(evaluation);

        updateStatuses(evaluation);

        updatePositionCounters(evaluation);

        bookingRepository.save(evaluation.getBooking());

        return interviewEvaluationRepository.save(evaluation);
    }

    public Optional<InterviewEvaluation> findByBooking(Booking booking) {
        return interviewEvaluationRepository.findByBooking(booking);
    }

    public List<InterviewEvaluation> findAll() {
        return interviewEvaluationRepository.findAll();
    }

    private void validate(InterviewEvaluation evaluation) {
        if (evaluation.getBooking() == null) {
            throw new RuntimeException("Booking is required.");
        }

        if (evaluation.getApplicant() == null) {
            throw new RuntimeException("Applicant is required.");
        }

        if (evaluation.getEvaluator() == null) {
            throw new RuntimeException("Evaluator is required.");
        }

        if (evaluation.getBooking().getStatus() != BookingStatus.ATTENDED) {
            throw new RuntimeException("Only attended bookings can be evaluated.");
        }

        Optional<InterviewEvaluation> existing = interviewEvaluationRepository.findByBooking(evaluation.getBooking());

        if (existing.isPresent() && evaluation.getId() == null) {
            throw new RuntimeException("Booking already has an evaluation.");
        }

        validateScore(evaluation.getCommunicationScore());

        validateScore(evaluation.getTechnicalScore());

        validateScore(evaluation.getAttitudeScore());

        if (evaluation.getResult() == null) {
            throw new RuntimeException("Interview result is required.");
        }
    }

    private void validateScore(Integer score) {
        if (score == null || score < 1 || score > 10) {
            throw new RuntimeException("Scores must be between 1 and 10.");
        }
    }

    private void updateStatuses(InterviewEvaluation evaluation) {
        InterviewResult result = evaluation.getResult();

        switch (result) {

            case PASS -> {
                applicantService.updateStatus(evaluation.getApplicant(), ApplicantStatus.PASSED);

                evaluation.getBooking().setStatus(BookingStatus.PASSED);
            }

            case FAIL -> {
                applicantService.updateStatus(evaluation.getApplicant(), ApplicantStatus.FAILED);

                evaluation.getBooking().setStatus(BookingStatus.FAILED);
            }

            case FOR_FINAL_INTERVIEW -> {
                applicantService.updateStatus(evaluation.getApplicant(), ApplicantStatus.FOR_FINAL_INTERVIEW);

                evaluation.getBooking().setStatus(BookingStatus.FOR_FINAL_INTERVIEW);
            }

            case FOR_CLIENT_INTERVIEW -> {
                applicantService.updateStatus(evaluation.getApplicant(), ApplicantStatus.FOR_CLIENT_INTERVIEW);

                evaluation.getBooking().setStatus(BookingStatus.FOR_CLIENT_INTERVIEW);
            }

            case ON_HOLD -> {
                applicantService.updateStatus(evaluation.getApplicant(), ApplicantStatus.ON_HOLD);

                evaluation.getBooking().setStatus(BookingStatus.ON_HOLD);
            }
        }
    }

    private void updatePositionCounters(InterviewEvaluation evaluation) {
        PositionOpening position = evaluation.getApplicant().getPositionOpening();

        position.setInterviewedCount(position.getInterviewedCount() + 1);

        if (evaluation.getResult() == InterviewResult.PASS) {
            position.setPassedCount(position.getPassedCount() + 1);
        }

        positionOpeningService.save(position);
    }
}