package com.company.iss.applicant.service;

import java.time.LocalDateTime;

public record ApplicantHiringJourneyEvent(
        Long sourceId,
        ApplicantHiringEventType type,
        LocalDateTime occurredAt,
        String actor,
        String remarks
) {
}
