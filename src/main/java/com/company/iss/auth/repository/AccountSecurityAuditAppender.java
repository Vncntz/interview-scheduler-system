package com.company.iss.auth.repository;

import com.company.iss.auth.entity.AccountSecurityAudit;

public interface AccountSecurityAuditAppender {
    AccountSecurityAudit append(AccountSecurityAudit audit);
}
