package com.company.iss.position.repository;

import com.company.iss.position.entity.PositionOpening;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PositionOpeningRepository extends JpaRepository<PositionOpening, Long> {

    List<PositionOpening> findByTitleContainingIgnoreCaseOrClientCompanyNameContainingIgnoreCaseOrWorkLocationContainingIgnoreCase(String title, String clientCompanyName, String workLocation);

    List<PositionOpening> findByActiveTrue();

    @Query("""
                select count(p)
                from PositionOpening p
                where p.active = true
            """)
    Long countActiveOpenings();

}