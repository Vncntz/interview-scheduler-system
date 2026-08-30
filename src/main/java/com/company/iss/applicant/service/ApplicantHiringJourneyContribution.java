package com.company.iss.applicant.service;

import java.util.List;

public record ApplicantHiringJourneyContribution(
        boolean offerEligible,
        boolean outstandingOffer,
        List<ApplicantHiringJourneyEvent> events
) {
    public ApplicantHiringJourneyContribution {
        events = List.copyOf(events);
    }

    public static ApplicantHiringJourneyContribution empty() {
        return new ApplicantHiringJourneyContribution(false, false, List.of());
    }
}
