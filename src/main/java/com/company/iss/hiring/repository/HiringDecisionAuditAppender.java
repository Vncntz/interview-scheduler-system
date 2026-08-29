package com.company.iss.hiring.repository;

import com.company.iss.hiring.entity.HiringDecisionAudit;

public interface HiringDecisionAuditAppender {

    HiringDecisionAudit append(HiringDecisionAudit audit);
}
