package com.jobtrail.jobs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;

class JobClassifierTest {
    @ParameterizedTest
    @CsvSource({
            "Junior Software Engineer, JUNIOR", "Software Engineer I, JUNIOR",
            "Software Engineer 1, JUNIOR", "Entry-level Backend Engineer, JUNIOR",
            "New Grad Software Engineer, JUNIOR", "Software Engineer II, MID",
            "Software Engineer 2, MID", "Intermediate Developer, MID",
            "Sr. Software Engineer, SENIOR", "Software Engineer III, SENIOR",
            "Staff Engineer, SENIOR", "Associate Director of Software, SENIOR",
            "Software Engineer, UNKNOWN", "Software Engineer Intern, UNKNOWN",
            "Juniorish Software Developer, UNKNOWN", "Midland Software Developer, UNKNOWN"})
    void classifiesExplicitLevelsWithWordBoundaries(String title, JobLevel expected) {
        assertThat(JobClassifier.level(title)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"Remote - US, REMOTE", "Hybrid - London, HYBRID", "Remote / hybrid, HYBRID",
            "On-site, ONSITE", "Onsite, ONSITE", "New York, UNKNOWN", "Remotely located office, UNKNOWN"})
    void locationDoesNotImplyWorkplaceUnlessExplicit(String location, Workplace expected) {
        assertThat(JobClassifier.workplace(location)).isEqualTo(expected);
    }

    @Test
    void missingLocationStaysUnknown() {
        assertThat(JobClassifier.workplace("")).isEqualTo(Workplace.UNKNOWN);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Software Engineer", "Backend Engineer", "Full Stack Developer", "Data Engineer",
            "Machine Learning Engineer", "SRE", "Cloud Engineer", "Test Engineer", "Front-end Developer"})
    void includesEngineeringTitles(String title) {
        assertThat(JobClassifier.engineeringRole(title)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Software Sales Account Executive", "Platform Marketing Manager",
            "Software Product Manager", "Account Executive", "Office Administrator", "Business Development Representative"})
    void excludesCommercialAndNonEngineeringTitles(String title) {
        assertThat(JobClassifier.engineeringRole(title)).isFalse();
    }

    @Test
    void preferencePriorityDoesNotExcludeNonPreferredLocationsOrSeniorTitles() {
        Job localJunior = job("Junior Software Engineer", "Arlington, VA", JobLevel.JUNIOR, Workplace.UNKNOWN);
        Job remoteMid = job("Software Engineer II", "Remote", JobLevel.MID, Workplace.REMOTE);
        Job elsewhere = job("Senior Software Engineer", "London", JobLevel.SENIOR, Workplace.UNKNOWN);
        assertThat(JobClassifier.priority(localJunior)).isGreaterThan(JobClassifier.priority(elsewhere));
        assertThat(JobClassifier.priority(remoteMid)).isGreaterThan(JobClassifier.priority(elsewhere));
        assertThat(JobClassifier.engineeringRole(elsewhere.getTitle())).isTrue();
    }

    private static Job job(String title, String location, JobLevel level, Workplace workplace) {
        Job job = new Job();
        job.setTitle(title);
        job.setLocation(location);
        job.setLevel(level);
        job.setWorkplace(workplace);
        return job;
    }
}
