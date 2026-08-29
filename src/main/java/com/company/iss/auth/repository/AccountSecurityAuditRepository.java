package com.company.iss.auth.repository;

import com.company.iss.auth.entity.AccountSecurityAudit;
import org.springframework.data.repository.Repository;

import java.util.List;

public interface AccountSecurityAuditRepository
        extends Repository<AccountSecurityAudit, Long>, AccountSecurityAuditAppender {

    List<AccountSecurityAudit> findByTargetUserIdOrderByOccurredAtDesc(Long targetUserId);

    long count();
}
