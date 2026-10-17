package com.jobtrail.refresh;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobtrail.jobs.Job;
import com.jobtrail.jobs.JobRepository;
import com.jobtrail.jobs.JobSearchService;
import com.jobtrail.jobs.TrackingStatus;
import com.jobtrail.sources.JobSource;
import com.jobtrail.sources.JobSourceRepository;
import com.jobtrail.sources.SourceProvider;
import com.jobtrail.sources.adapters.ExternalJob;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:snapshottests;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "jobtrail.collection.startup=false",
        "jobtrail.collection.scheduled=false",
        "jobtrail.security.enabled=false"})
@ActiveProfiles("test")
@Transactional
class SnapshotServiceTest {
    @Autowired SnapshotService snapshots;
    @Autowired JobRepository jobs;
    @Autowired JobSourceRepository sources;
    @Autowired JobSearchService search;
    @Autowired EntityManager entityManager;
    @Autowired PlatformTransactionManager transactionManager;
    private Long sourceId;

    @BeforeEach
    void createSource() {
        sourceId = source("Test Employer").getId();
    }

    @AfterEach
    void cleanCommittedFixtures() {
        if (!TestTransaction.isActive() && sourceId != null) {
            jobs.deleteAll(jobs.findBySourceId(sourceId));
            sources.deleteById(sourceId);
        }
    }

    @Test
    void repeatedSnapshotIsIdempotentAndKeepsFirstSeenTime() {
        ExternalJob posting = posting("one", "Backend Engineer", "Build Java services.");
        snapshots.apply(sourceId, List.of(posting));
        flushAndClear();
        Job initial = onlyJob();
        Long jobId = initial.getId();
        Instant firstSeen = initial.getFirstSeenAt();
        String hash = initial.getContentHash();

        assertThat(snapshots.apply(sourceId, List.of(posting))).isEqualTo(1);
        flushAndClear();

        Job updated = onlyJob();
        assertThat(updated.getId()).isEqualTo(jobId);
        assertThat(updated.getFirstSeenAt()).isEqualTo(firstSeen);
        assertThat(updated.getLastSeenAt()).isAfterOrEqualTo(firstSeen);
        assertThat(updated.getContentHash()).isEqualTo(hash);
        assertThat(updated.getStatus()).isEqualTo(TrackingStatus.NEW);
        assertThat(updated.getActive()).isTrue();
        assertThat(sources.findById(sourceId).orElseThrow().getJobCount()).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(TrackingStatus.class)
    void preservesTrackingStatusWhenPostingContentChanges(TrackingStatus status) {
        snapshots.apply(sourceId, List.of(posting("one", "Backend Engineer", "Original description.")));
        flushAndClear();
        Job initial = onlyJob();
        Long jobId = initial.getId();
        Instant firstSeen = initial.getFirstSeenAt();
        String oldHash = initial.getContentHash();
        search.status(jobId, status);
        flushAndClear();

        snapshots.apply(sourceId, List.of(posting("one", "Backend Engineer II", "Updated Java and SQL requirements.")));
        flushAndClear();

        Job updated = onlyJob();
        assertThat(updated.getId()).isEqualTo(jobId);
        assertThat(updated.getStatus()).isEqualTo(status);
        assertThat(updated.getFirstSeenAt()).isEqualTo(firstSeen);
        assertThat(updated.getTitle()).isEqualTo("Backend Engineer II");
        assertThat(updated.getContentHash()).isNotEqualTo(oldHash);
    }

    @Test
    void successfulCompleteSnapshotMarksOnlyMissingJobsInactive() {
        Long otherSourceId = source("Other Employer").getId();
        snapshots.apply(sourceId, List.of(posting("one", "Backend Engineer", "Java"), posting("two", "Platform Engineer", "Linux")));
        snapshots.apply(otherSourceId, List.of(posting("other", "Software Engineer", "SQL")));
        flushAndClear();
        Job removed = jobs.findBySourceId(sourceId).stream().filter(job -> job.getExternalId().equals("two")).findFirst().orElseThrow();
        search.status(removed.getId(), TrackingStatus.APPLIED);
        flushAndClear();

        snapshots.apply(sourceId, List.of(posting("one", "Backend Engineer", "Java")));
        flushAndClear();

        assertThat(jobs.findBySourceId(sourceId)).hasSize(2);
        assertThat(jobs.findBySourceId(sourceId)).filteredOn(Job::getActive).extracting(Job::getExternalId).containsExactly("one");
        Job inactive = jobs.findById(removed.getId()).orElseThrow();
        assertThat(inactive.getActive()).isFalse();
        assertThat(inactive.getStatus()).isEqualTo(TrackingStatus.APPLIED);
        assertThat(jobs.findBySourceId(otherSourceId)).allMatch(Job::getActive);
        assertThat(sources.findById(sourceId).orElseThrow().getJobCount()).isEqualTo(1);
    }

    @Test
    void aReappearingJobRetainsItsIdentityAndTrackingStatus() {
        ExternalJob posting = posting("one", "Backend Engineer", "Java");
        snapshots.apply(sourceId, List.of(posting));
        flushAndClear();
        Job initial = onlyJob();
        Long jobId = initial.getId();
        Instant firstSeen = initial.getFirstSeenAt();
        search.status(jobId, TrackingStatus.SAVED);
        snapshots.apply(sourceId, List.of());
        flushAndClear();
        assertThat(onlyJob().getActive()).isFalse();
        assertThat(sources.findById(sourceId).orElseThrow().getJobCount()).isZero();

        snapshots.apply(sourceId, List.of(posting));
        flushAndClear();

        Job returned = onlyJob();
        assertThat(returned.getId()).isEqualTo(jobId);
        assertThat(returned.getFirstSeenAt()).isEqualTo(firstSeen);
        assertThat(returned.getStatus()).isEqualTo(TrackingStatus.SAVED);
        assertThat(returned.getActive()).isTrue();
    }

    @Test
    void failedCollectionPreservesListingsAndLastSuccessfulSnapshot() {
        snapshots.apply(sourceId, List.of(posting("one", "Backend Engineer", "Java")));
        flushAndClear();
        Instant lastSuccess = sources.findById(sourceId).orElseThrow().getLastSuccessAt();

        snapshots.failure(sourceId, "The board is temporarily unavailable.");
        flushAndClear();

        assertThat(onlyJob().getActive()).isTrue();
        JobSource source = sources.findById(sourceId).orElseThrow();
        assertThat(source.getJobCount()).isEqualTo(1);
        assertThat(source.getLastSuccessAt()).isEqualTo(lastSuccess);
        assertThat(source.getLastError()).isEqualTo("The board is temporarily unavailable.");
        assertThat(source.getLastAttemptAt()).isAfterOrEqualTo(lastSuccess);

        snapshots.apply(sourceId, List.of(posting("one", "Backend Engineer", "Java")));
        flushAndClear();
        assertThat(sources.findById(sourceId).orElseThrow().getLastError()).isNull();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void invalidLaterPostingRollsBackEarlierUpdatesAndInserts() {
        snapshots.apply(sourceId, List.of(posting("one", "Backend Engineer", "Original description.")));
        Job initial = onlyJob();
        Long jobId = initial.getId();
        String initialHash = initial.getContentHash();
        Instant lastSuccess = sources.findById(sourceId).orElseThrow().getLastSuccessAt();
        ExternalJob invalid = new ExternalJob("bad", "Engineer", "Test Employer", "Remote", "Invalid link",
                "javascript:alert(1)", null, null);

        assertThatThrownBy(() -> snapshots.apply(sourceId, List.of(
                posting("one", "Changed title", "Changed description."),
                posting("inserted", "New Engineer", "New description."), invalid)))
                .isInstanceOf(IllegalArgumentException.class);

        Job retained = onlyJob();
        assertThat(retained.getId()).isEqualTo(jobId);
        assertThat(retained.getTitle()).isEqualTo("Backend Engineer");
        assertThat(retained.getDescription()).isEqualTo("Original description.");
        assertThat(retained.getContentHash()).isEqualTo(initialHash);
        assertThat(retained.getActive()).isTrue();
        JobSource source = sources.findById(sourceId).orElseThrow();
        assertThat(source.getLastSuccessAt()).isEqualTo(lastSuccess);
        assertThat(source.getJobCount()).isEqualTo(1);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void duplicatePostingsRejectTheWholeSnapshot() {
        snapshots.apply(sourceId, List.of(posting("one", "Backend Engineer", "Original description.")));

        assertThatThrownBy(() -> snapshots.apply(sourceId, List.of(
                posting("one", "Changed title", "Changed description."),
                posting("one", "Another title", "Other description."))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Duplicate");

        assertThat(onlyJob().getTitle()).isEqualTo("Backend Engineer");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentTrackingChangeSurvivesARefreshWithStaleEntityState() {
        snapshots.apply(sourceId, List.of(posting("one", "Backend Engineer", "Original description.")));
        Long jobId = onlyJob().getId();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            transaction.executeWithoutResult(ignored -> {
                assertThat(jobs.findById(jobId).orElseThrow().getStatus()).isEqualTo(TrackingStatus.NEW);
                assertThatCode(() -> executor.submit(() -> search.status(jobId, TrackingStatus.APPLIED))
                        .get(5, TimeUnit.SECONDS)).doesNotThrowAnyException();
                snapshots.apply(sourceId, List.of(posting("one", "Backend Engineer II", "Updated requirements.")));
            });
        }

        Job job = onlyJob();
        assertThat(job.getStatus()).isEqualTo(TrackingStatus.APPLIED);
        assertThat(job.getTitle()).isEqualTo("Backend Engineer II");
        assertThat(job.getDescription()).isEqualTo("Updated requirements.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"javascript:alert(1)", "http://jobs.example/job", "file:///tmp/job", "https://user:password@jobs.example/job", "//jobs.example/job"})
    void rejectsUnsafeLinksBeforeSaving(String url) {
        ExternalJob invalid = new ExternalJob("unsafe", "Engineer", "Test Employer", "Remote", "Description", url, null, null);

        assertThatThrownBy(() -> snapshots.apply(sourceId, List.of(invalid)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(jobs.findBySourceId(sourceId)).isEmpty();
    }

    private JobSource source(String name) {
        JobSource source = new JobSource();
        source.setName(name);
        source.setProvider(SourceProvider.GREENHOUSE);
        source.setBoardKey("test-" + UUID.randomUUID());
        source.setEnabled(false);
        return sources.saveAndFlush(source);
    }

    private ExternalJob posting(String id, String title, String description) {
        return new ExternalJob(id, title, "Test Employer", "Remote - United States", description,
                "https://jobs.example/" + id, null, Instant.parse("2026-10-01T12:00:00Z"));
    }

    private Job onlyJob() {
        List<Job> found = jobs.findBySourceId(sourceId);
        assertThat(found).hasSize(1);
        return found.getFirst();
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
