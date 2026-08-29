package com.company.iss.auth.repository;

import com.company.iss.auth.entity.AccountSecurityAudit;
import jakarta.persistence.EntityManager;
import org.springframework.transaction.annotation.Transactional;

public class AccountSecurityAuditAppenderImpl implements AccountSecurityAuditAppender {

    private final EntityManager entityManager;

    public AccountSecurityAuditAppenderImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public AccountSecurityAudit append(AccountSecurityAudit audit) {
        if (audit.getId() != null) {
            throw new IllegalArgumentException("A persisted account security audit cannot be appended again.");
        }
        entityManager.persist(audit);
        return audit;
    }
}
