package com.company.iss.hiring.repository;

import com.company.iss.hiring.entity.HiringDecisionAudit;
import jakarta.persistence.EntityManager;
import org.springframework.transaction.annotation.Transactional;

public class HiringDecisionAuditAppenderImpl implements HiringDecisionAuditAppender {

    private final EntityManager entityManager;

    public HiringDecisionAuditAppenderImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public HiringDecisionAudit append(HiringDecisionAudit audit) {
        if (audit.getId() != null) {
            throw new IllegalArgumentException("A persisted hiring audit cannot be appended again.");
        }
        entityManager.persist(audit);
        return audit;
    }
}
