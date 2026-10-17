package com.jobtrail.jobs;
import java.time.Instant;
public record JobView(Long id, Long sourceId, String sourceName, String company, String title, String location,
    Workplace workplace, JobLevel level, TrackingStatus status, String url, String description,
    Instant publishedAt, Instant firstSeenAt, Instant lastSeenAt, boolean active, int priorityScore) {
    public static JobView from(Job job, String sourceName) {
        return new JobView(job.getId(), job.getSourceId(), sourceName, job.getCompany(), job.getTitle(),
            job.getLocation(), job.getWorkplace(), job.getLevel(), job.getStatus(), job.getUrl(),
            job.getDescription(), job.getPublishedAt(), job.getFirstSeenAt(), job.getLastSeenAt(),
            job.getActive(), JobClassifier.priority(job));
    }

    public JobView withStatus(TrackingStatus trackingStatus) {
        return new JobView(id, sourceId, sourceName, company, title, location, workplace, level,
                trackingStatus, url, description, publishedAt, firstSeenAt, lastSeenAt, active, priorityScore);
    }
}

