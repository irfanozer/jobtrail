package com.jobtrail.profile;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record ProfileRequest(
        @NotNull @Size(max = 100) String displayName,
        @NotNull @Size(max = 30) List<@NotBlank @Size(max = 160) String> preferredLocations,
        @NotNull @Size(max = 100) List<@NotBlank @Size(max = 80) String> skills,
        @NotNull @Size(max = 20) List<@NotBlank @Size(max = 120) String> targetRoles,
        @NotNull @Size(max = 100) List<@NotNull @Valid CareerFact> careerFacts,
        @NotNull @PositiveOrZero Long version) {

    public record CareerFact(
            @NotBlank @Size(max = 80) String id,
            @NotBlank @Size(max = 2000) String statement,
            @NotNull @Size(max = 500) String source,
            @NotNull Boolean confirmed) {
    }
}
