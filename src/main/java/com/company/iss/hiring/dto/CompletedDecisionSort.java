package com.company.iss.hiring.dto;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public enum CompletedDecisionSort {
    APPLICANT("applicant", List.of("applicant.lastName", "applicant.firstName")),
    BRANCH("branch", List.of("applicant.branch.branchName")),
    POSITION("position", List.of("position.title")),
    CLIENT("client", List.of("position.client.companyName")),
    STATUS("status", List.of("status")),
    OFFERED_AT("offeredAt", List.of("offeredAt")),
    RESOLVED_AT("resolvedAt", List.of("resolvedAt"));

    private final String key;
    private final List<String> properties;

    CompletedDecisionSort(String key, List<String> properties) {
        this.key = key;
        this.properties = properties;
    }

    public List<String> properties() {
        return properties;
    }

    public static Optional<CompletedDecisionSort> fromKey(String key) {
        return Arrays.stream(values()).filter(value -> value.key.equals(key)).findFirst();
    }
}
