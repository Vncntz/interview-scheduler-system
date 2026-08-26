package com.company.iss.evaluation.repository;

import com.company.iss.booking.entity.Booking;
import com.company.iss.booking.entity.BookingStatus;
import com.company.iss.booking.repository.BookingRepository;
import com.company.iss.evaluation.entity.InterviewEvaluation;
import com.company.iss.evaluation.entity.InterviewResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
class InterviewEvaluationRepositoryTest {

    @Autowired InterviewEvaluationRepository evaluationRepository;
    @Autowired BookingRepository bookingRepository;

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
}
