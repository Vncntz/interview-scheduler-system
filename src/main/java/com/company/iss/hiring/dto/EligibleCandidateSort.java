package com.company.iss.hiring.dto;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public enum EligibleCandidateSort {
    APPLICANT("applicant", List.of("applicant.lastName", "applicant.firstName")),
    BRANCH("branch", List.of("applicant.branch.branchName")),
    POSITION("position", List.of("applicant.positionOpening.title")),
    CLIENT("client", List.of("applicant.positionOpening.client.companyName")),
    WORK_LOCATION("workLocation", List.of("applicant.positionOpening.workLocation")),
    EVALUATED_AT("evaluatedAt", List.of("evaluationDate"));

    private final String key;
    private final List<String> properties;

    EligibleCandidateSort(String key, List<String> properties) {
        this.key = key;
        this.properties = properties;
    }

    public List<String> properties() {
        return properties;
    }

    public static Optional<EligibleCandidateSort> fromKey(String key) {
        return Arrays.stream(values()).filter(value -> value.key.equals(key)).findFirst();
    }
}
