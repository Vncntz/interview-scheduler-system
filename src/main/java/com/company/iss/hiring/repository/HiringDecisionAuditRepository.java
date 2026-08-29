package com.company.iss.hiring.repository;

import com.company.iss.hiring.entity.HiringDecisionAudit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.Repository;

import java.util.List;

public interface HiringDecisionAuditRepository
        extends Repository<HiringDecisionAudit, Long>, HiringDecisionAuditAppender {

    @EntityGraph(attributePaths = {"actor"})
    List<HiringDecisionAudit> findByDecisionIdOrderByOccurredAtAsc(Long decisionId);

    long count();
}
