package com.company.iss.position.repository;

import com.company.iss.position.entity.PositionOpening;
import com.company.iss.position.entity.PositionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PositionOpeningRepository extends JpaRepository<PositionOpening, Long> {

    List<PositionOpening> findByTitleContainingIgnoreCaseOrClientCompanyNameContainingIgnoreCaseOrWorkLocationContainingIgnoreCase(String title, String clientCompanyName, String workLocation);

    List<PositionOpening> findByActiveTrue();

    Long countByActiveTrueAndStatus(PositionStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"client"})
    @Query("select p from PositionOpening p where p.id = :id")
    Optional<PositionOpening> findByIdForUpdate(@Param("id") Long id);

}
