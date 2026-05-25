package com.company.iss.applicant.service;

import com.company.iss.applicant.entity.Applicant;
import com.company.iss.applicant.entity.ApplicantStatus;
import com.company.iss.applicant.repository.ApplicantRepository;
import com.company.iss.position.entity.PositionOpening;
import com.company.iss.position.service.PositionOpeningService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ApplicantService {

    @Autowired
    private ApplicantRepository applicantRepository;

    @Autowired
    private PositionOpeningService positionOpeningService;

    public Applicant save(Applicant applicant) {

        validate(applicant);

        if (applicant.getId() == null) {
            incrementAppliedCount(
                    applicant.getPositionOpening()
            );

            applicant.setStatus(ApplicantStatus.NEW);

            applicant.setActive(true);

            return applicantRepository.save(applicant);
        }

        Applicant existing =
                applicantRepository.findById(
                        applicant.getId()
                ).orElseThrow(() ->
                        new RuntimeException(
                                "Applicant not found."
                        )
                );

        PositionOpening oldPosition =
                existing.getPositionOpening();

        PositionOpening newPosition =
                applicant.getPositionOpening();

        if (!oldPosition.getId().equals(newPosition.getId())) {

            decrementAppliedCount(oldPosition);

            incrementAppliedCount(newPosition);
        }

        existing.setFirstName(applicant.getFirstName());
        existing.setMiddleName(applicant.getMiddleName());
        existing.setLastName(applicant.getLastName());
        existing.setEmail(applicant.getEmail());
        existing.setMobileNumber(applicant.getMobileNumber());
        existing.setPositionOpening(newPosition);
        existing.setSource(applicant.getSource());
        existing.setRemarks(applicant.getRemarks());

        return applicantRepository.save(existing);
    }

    private void incrementAppliedCount(
            PositionOpening position
    ) {
        position.setAppliedCount(
                position.getAppliedCount() + 1
        );

        positionOpeningService.save(position);
    }

    private void decrementAppliedCount(
            PositionOpening position
    ) {
        if (position.getAppliedCount() > 0) {
            position.setAppliedCount(
                    position.getAppliedCount() - 1
            );
        }

        positionOpeningService.save(position);
    }

    private void validate(
            Applicant applicant
    ) {
        if (applicant.getPositionOpening() == null) {
            throw new RuntimeException(
                    "Position opening is required."
            );
        }
    }

    public List<Applicant> search(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return applicantRepository.findAll();
        }

        return applicantRepository.findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCaseOrEmailContainingIgnoreCase(keyword,keyword,keyword);
    }

    public void updateStatus(
            Applicant applicant,
            ApplicantStatus status
    ) {
        applicant.setStatus(status);
        applicantRepository.save(applicant);
    }

    public void activate(Applicant applicant) {
        applicant.setActive(true);
        applicantRepository.save(applicant);
    }

    public void deactivate(Applicant applicant) {
        applicant.setActive(false);
        applicantRepository.save(applicant);
    }
}