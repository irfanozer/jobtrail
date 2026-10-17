package com.jobtrail.sources;
import java.time.Instant;
public record SourceView(Long id, String name, SourceProvider provider, String boardKey, boolean enabled,
                         Instant lastSuccessAt, Instant lastAttemptAt, String lastError, int jobCount) {
    public static SourceView from(JobSource source) {
        return new SourceView(source.getId(), source.getName(), source.getProvider(), source.getBoardKey(),
            source.getEnabled(), source.getLastSuccessAt(), source.getLastAttemptAt(), source.getLastError(), source.getJobCount());
    }
}

