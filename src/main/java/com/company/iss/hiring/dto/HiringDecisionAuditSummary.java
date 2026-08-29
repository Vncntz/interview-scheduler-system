package com.company.iss.hiring.dto;

import com.company.iss.hiring.entity.HiringDecisionAction;
import com.company.iss.hiring.entity.HiringDecisionStatus;

import java.time.LocalDateTime;

public record HiringDecisionAuditSummary(
        HiringDecisionAction action,
        HiringDecisionStatus previousStatus,
        HiringDecisionStatus newStatus,
        String actor,
        LocalDateTime occurredAt,
        String remarks
) {
}
