package com.jobtrail.matching;

import com.jobtrail.profile.ProfileRequest.CareerFact;
import com.jobtrail.profile.ProfileResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class RulesMatchEngineTest {
    private final RulesMatchEngine engine = new RulesMatchEngine();

    @Test
    void respectsSkillBoundariesInsteadOfTreatingJavaScriptAsJava() {
        MatchResponse result = engine.compare(job("Build with JavaScript and PostgreSQL."), profile(List.of("Java", "SQL")));
        assertThat(result.strengths()).noneMatch(finding -> finding.type().equals("SKILL"));
        assertThat(result.gaps()).extracting(MatchResponse.Finding::message)
                .contains("JavaScript appears in the job text but is not listed in your profile.",
                        "PostgreSQL appears in the job text but is not listed in your profile.")
                .noneMatch(message -> message.startsWith("Java ") || message.startsWith("SQL "));
    }

    @Test
    void resolvesExplicitAliasesAndCarriesEvidenceFromBothSides() {
        MatchResponse result = engine.compare(job("We use PostgreSQL and Kubernetes."), profile(List.of("Postgres", "K8s")));
        assertThat(result.strengths()).filteredOn(finding -> finding.type().equals("SKILL")).hasSize(2);
        assertThat(result.strengths().getFirst().evidence()).extracting(MatchResponse.Evidence::source)
                .containsExactly("PROFILE_SKILL", "JOB_TEXT");
        assertThat(result.strengths().getFirst().evidence().getFirst().text()).isEqualTo("Postgres");
    }

    @Test
    void doesNotInferSkillsFromUnconfirmedOrConfirmedCareerFactText() {
        ProfileResponse profile = new ProfileResponse("", List.of(), List.of(), List.of(),
                List.of(new CareerFact("fact-1", "Interested in learning Java.", "Personal note", false),
                        new CareerFact("fact-2", "Have not used Python professionally.", "Personal confirmation", true)), 3, null);
        MatchResponse result = engine.compare(job("Use Java and Python."), profile);
        assertThat(result.strengths()).isEmpty();
        assertThat(result.gaps()).hasSize(2);
        assertThat(result.profileVersion()).isEqualTo(3);
    }

    @Test
    void emptyProfileRemainsEmptyAndGivesActionableUnknowns() {
        MatchResponse result = engine.compare(job("Build Java services."), ProfileResponse.empty());
        assertThat(result.strengths()).isEmpty();
        assertThat(result.unknowns()).extracting(MatchResponse.Finding::message)
                .contains("No skills are saved in your profile. Add only skills you can support.",
                        "No target roles are saved in your profile.", "No preferred locations are saved in your profile.");
        assertThat(result.label()).isEqualTo("Rules-based match; AI not enabled");
        assertThat(result.method()).isEqualTo("RULES_BASED");
    }

    @Test
    void escapesCustomSkillPatternsAndDoesNotMatchSubstrings() {
        MatchResponse result = engine.compare(job("Use C++ and gRPC, with NumPy."), profile(List.of("C++", "gRPC", "Num")));
        assertThat(result.strengths()).filteredOn(finding -> finding.type().equals("SKILL"))
                .extracting(MatchResponse.Finding::message)
                .containsExactly("Your profile lists C++, which appears in the job text.",
                        "Your profile lists gRPC, which appears in the job text.");
    }

    @Test
    void doesNotTurnRemoteLabelIntoGeographicEligibility() {
        ProfileResponse profile = new ProfileResponse("", List.of("Arlington"), List.of("Java"), List.of(), List.of(), 1, null);
        MatchResponse result = engine.compare(new RulesMatchEngine.JobDetails(7L, "Engineer", "Remote - Canada", "Java", "MID", "REMOTE"), profile);
        assertThat(result.strengths()).noneMatch(finding -> finding.type().equals("LOCATION"));
        assertThat(result.unknowns()).extracting(MatchResponse.Finding::message)
                .contains("The listed location does not explicitly match your preferences. Remote labels do not establish geographic eligibility.");
    }

    @Test
    void keywordMentionDoesNotClaimRequiredQualificationOrHiringProbability() {
        MatchResponse result = engine.compare(job("Java is optional; no previous experience required."), profile(List.of("Python")));
        assertThat(result.gaps()).extracting(MatchResponse.Finding::message)
                .containsExactly("Java appears in the job text but is not listed in your profile.");
        assertThat(result.unknowns()).extracting(MatchResponse.Finding::message)
                .anyMatch(message -> message.contains("do not establish mandatory requirements"))
                .anyMatch(message -> message.contains("hiring probability"));
    }

    @Test
    void outputIsDeterministicAndMissingDescriptionsStayUnknown() {
        RulesMatchEngine.JobDetails job = job(null);
        ProfileResponse profile = profile(List.of("Java"));
        assertThat(engine.compare(job, profile)).isEqualTo(engine.compare(job, profile));
        assertThat(engine.compare(job, profile).unknowns()).extracting(MatchResponse.Finding::message)
                .contains("This posting has no description available to compare.");
    }

    private static RulesMatchEngine.JobDetails job(String description) {
        return new RulesMatchEngine.JobDetails(7L, "Software Engineer", "Remote", description, "MID", "REMOTE");
    }

    private static ProfileResponse profile(List<String> skills) {
        return new ProfileResponse("", List.of(), skills, List.of(), List.of(), 1, null);
    }
}
