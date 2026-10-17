package com.jobtrail.jobs;

import com.jobtrail.sources.JobSource;
import com.jobtrail.sources.JobSourceRepository;
import com.jobtrail.sources.SourceProvider;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:jobsearchtests;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=true"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JobSearchService.class)
class JobSearchServiceTest {
    @Autowired JobRepository jobs;
    @Autowired JobSourceRepository sources;
    @Autowired JobSearchService service;
    private JobSource firstSource;
    private int sequence;

    @BeforeEach
    void createSource() {
        firstSource = source("Example Board", "example-board");
    }

    @Test
    void treatsPercentUnderscoreAndBackslashAsLiteralSearchCharacters() {
        Job percent = save("Software Engineer", "Remote", "Maintains 100% availability.", TrackingStatus.NEW, true);
        Job underscore = save("Software Engineer", "Remote", "Works on an internal_api.", TrackingStatus.NEW, true);
        Job backslash = save("Software Engineer", "Remote", "Supports C:\\tools on Windows.", TrackingStatus.NEW, true);
        save("Software Engineer", "Remote", "Maintains internal-api services and C:/tools.", TrackingStatus.NEW, true);
        assertThat(search("%", true, false).items()).extracting(JobView::id).containsExactly(percent.getId());
        assertThat(search("_", true, false).items()).extracting(JobView::id).containsExactly(underscore.getId());
        assertThat(search("\\", true, false).items()).extracting(JobView::id).containsExactly(backslash.getId());
    }

    @Test
    void requiresEverySearchTermButAllowsTermsAcrossFieldsAndIgnoresCase() {
        Job match = save("Backend Software Engineer", "Remote", "Build JAVA services.", TrackingStatus.NEW, true);
        save("Backend Software Engineer", "London", "Build Java services.", TrackingStatus.NEW, true);
        save("Backend Software Engineer", "Remote", "Build Python services.", TrackingStatus.NEW, true);
        assertThat(search("  java   REMOTE ", true, false).items()).extracting(JobView::id).containsExactly(match.getId());
    }

    @Test
    void keepsLocationsBroadWhileDefaultEngineeringFilterExcludesOtherWork() {
        Job london = save("Software Engineer", "London", "Python", TrackingStatus.NEW, true);
        Job canada = save("Backend Engineer", "Toronto", "Java", TrackingStatus.NEW, true);
        Job unspecified = save("Full Stack Developer", "", "TypeScript", TrackingStatus.NEW, true);
        Job sales = save("Account Executive", "Arlington", "Sales", TrackingStatus.NEW, true);
        assertThat(search("", true, false).items()).extracting(JobView::id)
                .containsExactlyInAnyOrder(london.getId(), canada.getId(), unspecified.getId());
        assertThat(search("", false, false).items()).extracting(JobView::id).contains(sales.getId());
    }

    @Test
    void hidesHiddenAndInactiveJobsByDefaultButPreservesAppliedRecords() {
        Job fresh = save("Software Engineer", "Remote", "Java", TrackingStatus.NEW, true);
        Job applied = save("Software Engineer", "Remote", "Java", TrackingStatus.APPLIED, true);
        Job hidden = save("Software Engineer", "Remote", "Java", TrackingStatus.HIDDEN, true);
        Job closed = save("Software Engineer", "Remote", "Java", TrackingStatus.NEW, false);
        assertThat(search("", true, false).items()).extracting(JobView::id)
                .containsExactlyInAnyOrder(fresh.getId(), applied.getId());
        assertThat(service.search("", null, null, TrackingStatus.HIDDEN, null, 0, 25, "priority", true, false).items())
                .extracting(JobView::id).containsExactly(hidden.getId());
        assertThat(service.search("", null, null, TrackingStatus.APPLIED, null, 0, 25, "priority", true, false).items())
                .extracting(JobView::id).containsExactly(applied.getId());
        assertThat(search("", true, true).items()).extracting(JobView::id).contains(closed.getId()).doesNotContain(hidden.getId());
    }

    @Test
    void combinesSourceLevelAndWorkplaceFilters() {
        Job wanted = save("Software Engineer II", "Remote", "Java", TrackingStatus.NEW, true);
        save("Junior Software Engineer", "Remote", "Java", TrackingStatus.NEW, true);
        save("Software Engineer II", "Hybrid - London", "Java", TrackingStatus.NEW, true);
        Job other = save("Software Engineer II", "Remote", "Java", TrackingStatus.NEW, true);
        other.setSourceId(source("Other Board", "other-board").getId());
        jobs.saveAndFlush(other);
        assertThat(service.search("", JobLevel.MID, Workplace.REMOTE, null, firstSource.getId(), 0, 25,
                "priority", true, false).items()).extracting(JobView::id).containsExactly(wanted.getId());
    }

    @Test
    void statusChangesPersistAndDoNotChangePublicationDate() {
        Job saved = save("Software Engineer", "Remote", "Java", TrackingStatus.NEW, true);
        assertThat(service.status(saved.getId(), TrackingStatus.APPLIED).status()).isEqualTo(TrackingStatus.APPLIED);
        jobs.flush();
        JobView loaded = service.get(saved.getId());
        assertThat(loaded.status()).isEqualTo(TrackingStatus.APPLIED);
        assertThat(loaded.publishedAt()).isNull();
        assertThat(loaded.firstSeenAt()).isNotNull();
        assertThat(loaded.sourceName()).isEqualTo("Example Board");
    }

    @Test
    void paginationUsesDeterministicOrderAndReturnsEmptyPagesBeyondTheEnd() {
        Job first = save("Software Engineer", "London", "Java", TrackingStatus.NEW, true);
        Job second = save("Software Engineer", "London", "Java", TrackingStatus.NEW, true);
        JobSearchService.JobPage page = service.search("", null, null, null, null, 0, 1, "newest", true, false);
        assertThat(page.items()).extracting(JobView::id).containsExactly(first.getId());
        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.totalPages()).isEqualTo(2);
        assertThat(service.search("", null, null, null, null, 1, 1, "newest", true, false).items())
                .extracting(JobView::id).containsExactly(second.getId());
        assertThat(service.search("", null, null, null, null, Integer.MAX_VALUE, 100, "newest", true, false).items()).isEmpty();
    }

    @Test
    void rejectsInvalidPaginationQueryLengthAndSort() {
        assertBadRequest(() -> service.search("", null, null, null, null, -1, 25, "priority", true, false));
        assertBadRequest(() -> service.search("", null, null, null, null, 0, 0, "priority", true, false));
        assertBadRequest(() -> service.search("", null, null, null, null, 0, 101, "priority", true, false));
        assertBadRequest(() -> service.search("x".repeat(201), null, null, null, null, 0, 25, "priority", true, false));
        assertBadRequest(() -> service.search("", null, null, null, null, 0, 25, "invalid", true, false));
    }

    @Test
    void unknownJobIdsReturnNotFound() {
        assertThatThrownBy(() -> service.get(Long.MAX_VALUE)).isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode().value()).isEqualTo(404));
        assertThatThrownBy(() -> service.status(Long.MAX_VALUE, TrackingStatus.SAVED))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode().value()).isEqualTo(404));
    }

    private JobSearchService.JobPage search(String query, boolean engineeringOnly, boolean includeInactive) {
        return service.search(query, null, null, null, null, 0, 25, "priority", engineeringOnly, includeInactive);
    }

    private JobSource source(String name, String key) {
        JobSource source = new JobSource();
        source.setName(name);
        source.setProvider(SourceProvider.GREENHOUSE);
        source.setBoardKey(key);
        source.setEnabled(true);
        return sources.saveAndFlush(source);
    }

    private Job save(String title, String location, String description, TrackingStatus status, boolean active) {
        Job job = new Job();
        job.setSourceId(firstSource.getId());
        job.setExternalId("example-" + sequence++);
        job.setTitle(title);
        job.setCompany("Example Company");
        job.setLocation(location);
        job.setDescription(description);
        job.setUrl("https://example.com/jobs/" + sequence);
        job.setWorkplace(JobClassifier.workplace(location));
        job.setLevel(JobClassifier.level(title));
        job.setStatus(status);
        job.setFirstSeenAt(Instant.parse("2026-01-01T00:00:00Z"));
        job.setLastSeenAt(Instant.parse("2026-01-01T00:00:00Z"));
        job.setActive(active);
        job.setContentHash("a".repeat(64));
        return jobs.saveAndFlush(job);
    }

    private void assertBadRequest(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode().value()).isEqualTo(400));
    }
}
