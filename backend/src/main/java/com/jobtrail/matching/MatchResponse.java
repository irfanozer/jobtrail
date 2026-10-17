package com.jobtrail.matching;

import java.util.List;

public record MatchResponse(
        Long jobId,
        long profileVersion,
        String method,
        String label,
        List<Finding> strengths,
        List<Finding> gaps,
        List<Finding> unknowns) {

    public record Finding(String type, String message, List<Evidence> evidence) {
    }

    public record Evidence(String source, String reference, String text) {
    }
}
