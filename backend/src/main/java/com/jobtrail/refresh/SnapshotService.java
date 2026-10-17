package com.jobtrail.refresh;

import com.jobtrail.jobs.*;
import com.jobtrail.sources.*;
import com.jobtrail.sources.adapters.ExternalJob;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SnapshotService {
    private final JobRepository jobs;
    private final JobSourceRepository sources;
    public SnapshotService(JobRepository jobs, JobSourceRepository sources) { this.jobs=jobs; this.sources=sources; }

    @Transactional
    public int apply(Long sourceId, List<ExternalJob> snapshot) {
        JobSource source = sources.findById(sourceId).orElseThrow();
        Instant now = Instant.now();
        Map<String,Job> existing = jobs.findBySourceId(sourceId).stream()
            .collect(Collectors.toMap(Job::getExternalId, job -> job));
        Set<String> seen = new HashSet<>();
        for (ExternalJob external : snapshot) {
            validate(external);
            if (!seen.add(external.externalId())) throw new IllegalArgumentException("Duplicate posting in source snapshot.");
            Job job = existing.get(external.externalId());
            if (job == null) {
                job = new Job(); job.setSourceId(sourceId); job.setExternalId(external.externalId());
                job.setFirstSeenAt(now); job.setStatus(TrackingStatus.NEW);
            }
            job.setTitle(external.title()); job.setCompany(external.company());
            job.setLocation(external.location()); job.setDescription(external.description()); job.setUrl(external.url());
            job.setPublishedAt(external.publishedAt()); job.setSourceUpdatedAt(external.sourceUpdatedAt());
            job.setLastSeenAt(now); job.setActive(true);
            job.setWorkplace(JobClassifier.workplace(external.location())); job.setLevel(JobClassifier.level(external.title()));
            job.setContentHash(hash(external.title()+"\n"+external.location()+"\n"+external.description()+"\n"+external.url()));
            jobs.save(job);
        }
        for (Job old : existing.values()) if (!seen.contains(old.getExternalId())) old.setActive(false);
        source.setLastAttemptAt(now); source.setLastSuccessAt(now); source.setLastError(null); source.setJobCount(snapshot.size());
        return snapshot.size();
    }

    @Transactional
    public void failure(Long sourceId, String error) {
        sources.findById(sourceId).ifPresent(source -> {
            source.setLastAttemptAt(Instant.now());
            source.setLastError(error.substring(0,Math.min(500,error.length())));
        });
    }

    private void validate(ExternalJob job) {
        if (job.externalId()==null || job.externalId().isBlank() || job.externalId().length()>255 ||
            job.title()==null || job.title().isBlank() || job.title().length()>500 ||
            job.company()==null || job.company().length()>200 || job.location()==null || job.location().length()>1000 ||
            job.description()==null || job.url()==null || job.url().length()>2000) {
            throw new IllegalArgumentException("Source returned invalid posting fields.");
        }
        URI url = URI.create(job.url());
        if (!"https".equalsIgnoreCase(url.getScheme()) || url.getHost()==null || url.getUserInfo()!=null)
            throw new IllegalArgumentException("Source returned an unsafe application link.");
    }
    private String hash(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}

