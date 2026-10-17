package com.jobtrail.profile;

import java.time.Instant;
import java.util.List;

public record ProfileResponse(
        String displayName,
        List<String> preferredLocations,
        List<String> skills,
        List<String> targetRoles,
        List<ProfileRequest.CareerFact> careerFacts,
        long version,
        Instant updatedAt) {

    public static ProfileResponse empty() {
        return new ProfileResponse("", List.of(), List.of(), List.of(), List.of(), 0, null);
    }
}
