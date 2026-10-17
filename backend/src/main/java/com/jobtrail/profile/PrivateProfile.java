package com.jobtrail.profile;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "private_profile")
public class PrivateProfile {
    @Id
    private Long id;

    @Version
    @Column(name = "lock_version", nullable = false)
    private Long lockVersion;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PrivateProfile() {
    }

    public PrivateProfile(String payload, Instant updatedAt) {
        this.id = 1L;
        this.payload = payload;
        this.updatedAt = updatedAt;
    }

    public long publicVersion() {
        return lockVersion == null ? 0 : lockVersion + 1;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void replace(String payload, Instant updatedAt) {
        this.payload = payload;
        this.updatedAt = updatedAt;
    }
}
