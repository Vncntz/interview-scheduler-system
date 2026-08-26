package com.company.iss.position.repository;

import com.company.iss.position.entity.PositionOpening;
import com.company.iss.position.entity.PositionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PositionOpeningRepository extends JpaRepository<PositionOpening, Long> {

    List<PositionOpening> findByTitleContainingIgnoreCaseOrClientCompanyNameContainingIgnoreCaseOrWorkLocationContainingIgnoreCase(String title, String clientCompanyName, String workLocation);

    List<PositionOpening> findByActiveTrue();

    Long countByActiveTrueAndStatus(PositionStatus status);

}
