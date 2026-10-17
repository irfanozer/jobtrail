package com.jobtrail.jobs;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "jobs")
public class Job {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long sourceId;
    @Column(nullable = false, length = 255)
    private String externalId;
    @Column(nullable = false, length = 500)
    private String title;
    @Column(nullable = false, length = 200)
    private String company;
    @Column(nullable = false, length = 1000)
    private String location;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;
    @Column(nullable = false, length = 2000)
    private String url;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private Workplace workplace;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private JobLevel level;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private TrackingStatus status;
    
    private Instant publishedAt;
    
    private Instant sourceUpdatedAt;
    @Column(nullable = false)
    private Instant firstSeenAt;
    @Column(nullable = false)
    private Instant lastSeenAt;
    @Column(nullable = false)
    private boolean active;
    @Column(nullable = false, length = 64)
    private String contentHash;
    public Job() {}
    public Long getId() { return id; }
    public Long getSourceId() { return sourceId; }
    public void setSourceId(Long value) { this.sourceId = value; }
    public String getExternalId() { return externalId; }
    public void setExternalId(String value) { this.externalId = value; }
    public String getTitle() { return title; }
    public void setTitle(String value) { this.title = value; }
    public String getCompany() { return company; }
    public void setCompany(String value) { this.company = value; }
    public String getLocation() { return location; }
    public void setLocation(String value) { this.location = value; }
    public String getDescription() { return description; }
    public void setDescription(String value) { this.description = value; }
    public String getUrl() { return url; }
    public void setUrl(String value) { this.url = value; }
    public Workplace getWorkplace() { return workplace; }
    public void setWorkplace(Workplace value) { this.workplace = value; }
    public JobLevel getLevel() { return level; }
    public void setLevel(JobLevel value) { this.level = value; }
    public TrackingStatus getStatus() { return status; }
    public void setStatus(TrackingStatus value) { this.status = value; }
    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant value) { this.publishedAt = value; }
    public Instant getSourceUpdatedAt() { return sourceUpdatedAt; }
    public void setSourceUpdatedAt(Instant value) { this.sourceUpdatedAt = value; }
    public Instant getFirstSeenAt() { return firstSeenAt; }
    public void setFirstSeenAt(Instant value) { this.firstSeenAt = value; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(Instant value) { this.lastSeenAt = value; }
    public boolean getActive() { return active; }
    public void setActive(boolean value) { this.active = value; }
    public String getContentHash() { return contentHash; }
    public void setContentHash(String value) { this.contentHash = value; }

}

