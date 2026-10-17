package com.jobtrail.sources.adapters;

import com.jobtrail.sources.JobSource;
import com.jobtrail.sources.SourceProvider;
import java.util.List;

public interface JobBoardAdapter {
    SourceProvider provider();

    List<ExternalJob> fetch(JobSource source);
}
