package com.company.iss.hiring.dto;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public enum OutstandingDecisionSort {
    APPLICANT("applicant", List.of("applicant.lastName", "applicant.firstName")),
    BRANCH("branch", List.of("applicant.branch.branchName")),
    POSITION("position", List.of("position.title")),
    CLIENT("client", List.of("position.client.companyName")),
    STATUS("status", List.of("status")),
    RESPONSE_DUE("responseDueAt", List.of("responseDueAt")),
    OFFERED_AT("offeredAt", List.of("offeredAt"));

    private final String key;
    private final List<String> properties;

    OutstandingDecisionSort(String key, List<String> properties) {
        this.key = key;
        this.properties = properties;
    }

    public List<String> properties() {
        return properties;
    }

    public static Optional<OutstandingDecisionSort> fromKey(String key) {
        return Arrays.stream(values()).filter(value -> value.key.equals(key)).findFirst();
    }
}
