package com.jobtrail.profile;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;

class ProfileValidationTest {
    @Test
    void validatesRequiredListsAndVersionWithoutRequiringPersonalContactDetails() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(request(List.of(), List.of(), 0L))).isEmpty();
            ProfileRequest invalid = new ProfileRequest("", null, List.of(" "), List.of(), List.of(), null);
            assertThat(factory.getValidator().validate(invalid)).hasSize(3);
        }
    }

    @Test
    void validatesNestedCareerFactsAndInputSizeLimits() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            ProfileRequest invalid = request(List.of("x".repeat(81)),
                    List.of(new ProfileRequest.CareerFact("", "", "", null)), -1L);
            assertThat(factory.getValidator().validate(invalid)).hasSize(5);
        }
    }

    @Test
    void trimsAndDeduplicatesUserEntriesWithoutAddingClaims() {
        ProfileRequest normalized = ProfileService.normalize(new ProfileRequest("  Example Person  ",
                List.of(" Arlington ", "arlington"), List.of(" Java ", "JAVA", "Python"),
                List.of(" Software Engineer "), List.of(), 0L));
        assertThat(normalized.displayName()).isEqualTo("Example Person");
        assertThat(normalized.skills()).containsExactly("Java", "Python");
        assertThat(normalized.preferredLocations()).containsExactly("Arlington");
        assertThat(normalized.careerFacts()).isEmpty();
    }

    @Test
    void rejectsDuplicateCareerFactIds() {
        ProfileRequest request = request(List.of(), List.of(
                new ProfileRequest.CareerFact("fact-1", "First fact", "", false),
                new ProfileRequest.CareerFact(" fact-1 ", "Second fact", "", false)), 0L);
        assertThatThrownBy(() -> ProfileService.normalize(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Career fact IDs must be unique");
    }

    @Test
    void confirmedFactsRequireEvidenceSourceWhileUnconfirmedDraftsCanRemainIncomplete() {
        ProfileRequest unconfirmed = request(List.of(), List.of(
                new ProfileRequest.CareerFact("fact-1", "Example statement", "", false)), 0L);
        assertThatCode(() -> ProfileService.normalize(unconfirmed)).doesNotThrowAnyException();
        ProfileRequest confirmed = request(List.of(), List.of(
                new ProfileRequest.CareerFact("fact-1", "Example statement", " ", true)), 0L);
        assertThatThrownBy(() -> ProfileService.normalize(confirmed))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("need a source");
    }

    private static ProfileRequest request(List<String> skills, List<ProfileRequest.CareerFact> facts, Long version) {
        return new ProfileRequest("", List.of(), skills, List.of(), facts, version);
    }
}
