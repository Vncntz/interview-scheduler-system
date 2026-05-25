package com.company.iss.position.service;

import com.company.iss.position.entity.PositionOpening;
import com.company.iss.position.entity.PositionStatus;
import com.company.iss.position.repository.PositionOpeningRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PositionOpeningService {

    @Autowired
    private PositionOpeningRepository positionOpeningRepository;

    public List<PositionOpening> search(String keyword) {

        if (keyword == null || keyword.isBlank()) {
            return positionOpeningRepository.findAll();
        }

        return positionOpeningRepository.findByTitleContainingIgnoreCaseOrClientCompanyNameContainingIgnoreCaseOrWorkLocationContainingIgnoreCase(keyword, keyword, keyword);
    }

    public List<PositionOpening> findActive() {
        return positionOpeningRepository.findByActiveTrue();
    }

    public PositionOpening save(PositionOpening positionOpening) {
        validate(positionOpening);

        return positionOpeningRepository.save(positionOpening);
    }

    public void activate(PositionOpening positionOpening) {
        positionOpening.setActive(true);
        positionOpeningRepository.save(positionOpening);
    }

    public void deactivate(PositionOpening positionOpening) {
        positionOpening.setActive(false);
        positionOpeningRepository.save(positionOpening);
    }

    public void markOnHold(PositionOpening positionOpening) {
        positionOpening.setStatus(PositionStatus.ON_HOLD);
        positionOpeningRepository.save(positionOpening);
    }

    public void markOpen(PositionOpening positionOpening) {
        positionOpening.setStatus(PositionStatus.OPEN);
        positionOpeningRepository.save(positionOpening);
    }

    public void markClosed(PositionOpening positionOpening) {
        positionOpening.setStatus(PositionStatus.CLOSED);
        positionOpeningRepository.save(positionOpening);
    }

    public void markCancelled(PositionOpening positionOpening) {
        positionOpening.setStatus(PositionStatus.CANCELLED);
        positionOpeningRepository.save(positionOpening);
    }

    public void incrementApplied(PositionOpening positionOpening) {
        positionOpening.setAppliedCount(positionOpening.getAppliedCount() + 1);

        positionOpeningRepository.save(positionOpening);
    }

    public void incrementInterviewed(PositionOpening positionOpening) {
        positionOpening.setInterviewedCount(positionOpening.getInterviewedCount() + 1);

        positionOpeningRepository.save(positionOpening);
    }

    public void incrementPassed(PositionOpening positionOpening) {
        positionOpening.setPassedCount(positionOpening.getPassedCount() + 1);

        positionOpeningRepository.save(positionOpening);
    }

    public void incrementHired(PositionOpening positionOpening) {
        positionOpening.setHiredCount(positionOpening.getHiredCount() + 1);

        if (positionOpening.getHiredCount() >= positionOpening.getRequiredHeadcount()) {
            positionOpening.setStatus(PositionStatus.FILLED);
        }

        positionOpeningRepository.save(positionOpening);
    }

    private void validate(PositionOpening positionOpening) {

        if (positionOpening.getTitle() == null || positionOpening.getTitle().isBlank()) {
            throw new RuntimeException("Position title is required.");
        }

        if (positionOpening.getClient() == null) {
            throw new RuntimeException("Client is required.");
        }

        if (positionOpening.getWorkLocation() == null || positionOpening.getWorkLocation().isBlank()) {
            throw new RuntimeException("Work location is required.");
        }

        if (positionOpening.getEmploymentType() == null) {
            throw new RuntimeException("Employment type is required.");
        }

        if (positionOpening.getRequiredHeadcount() == null || positionOpening.getRequiredHeadcount() <= 0) {
            throw new RuntimeException("Required headcount must be greater than zero.");
        }

        if (positionOpening.getHiredCount() > positionOpening.getRequiredHeadcount()) {
            throw new RuntimeException("Hired count cannot exceed required headcount.");
        }
    }
}