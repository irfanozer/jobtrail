package com.jobtrail.sources;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "job_sources")
public class JobSource {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 120)
    private String name;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private SourceProvider provider;
    @Column(nullable = false, length = 100)
    private String boardKey;
    @Column(nullable = false)
    private boolean enabled;
    
    private Instant lastAttemptAt;
    
    private Instant lastSuccessAt;
    @Column(length = 500)
    private String lastError;
    @Column(nullable = false)
    private int jobCount;
    public JobSource() {}
    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String value) { this.name = value; }
    public SourceProvider getProvider() { return provider; }
    public void setProvider(SourceProvider value) { this.provider = value; }
    public String getBoardKey() { return boardKey; }
    public void setBoardKey(String value) { this.boardKey = value; }
    public boolean getEnabled() { return enabled; }
    public void setEnabled(boolean value) { this.enabled = value; }
    public Instant getLastAttemptAt() { return lastAttemptAt; }
    public void setLastAttemptAt(Instant value) { this.lastAttemptAt = value; }
    public Instant getLastSuccessAt() { return lastSuccessAt; }
    public void setLastSuccessAt(Instant value) { this.lastSuccessAt = value; }
    public String getLastError() { return lastError; }
    public void setLastError(String value) { this.lastError = value; }
    public int getJobCount() { return jobCount; }
    public void setJobCount(int value) { this.jobCount = value; }

}

