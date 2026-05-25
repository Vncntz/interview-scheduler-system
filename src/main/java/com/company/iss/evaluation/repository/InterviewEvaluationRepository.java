package com.company.iss.evaluation.repository;

import com.company.iss.applicant.entity.Applicant;
import com.company.iss.booking.entity.Booking;
import com.company.iss.evaluation.entity.InterviewEvaluation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InterviewEvaluationRepository extends JpaRepository<InterviewEvaluation, Long> {

    Optional<InterviewEvaluation> findByBooking(Booking booking);

    List<InterviewEvaluation> findByApplicant(Applicant applicant);

}