package com.company.iss.hiring.dto;

public record HiringWorklistFilter(String keyword) {

    public static HiringWorklistFilter empty() {
        return new HiringWorklistFilter(null);
    }
}
