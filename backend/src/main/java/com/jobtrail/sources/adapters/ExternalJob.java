package com.jobtrail.sources.adapters;

import java.time.Instant;

public record ExternalJob(
        String externalId,
        String title,
        String company,
        String location,
        String description,
        String url,
        Instant publishedAt,
        Instant sourceUpdatedAt) {
}
