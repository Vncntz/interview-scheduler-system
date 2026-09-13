package com.company.iss.hiring.dto;

public record OutstandingOfferFilter(String keyword, OfferDeadlineFilter deadline) {

    public OutstandingOfferFilter {
        deadline = deadline == null ? OfferDeadlineFilter.ALL : deadline;
    }

    public static OutstandingOfferFilter empty() {
        return new OutstandingOfferFilter(null, OfferDeadlineFilter.ALL);
    }
}
