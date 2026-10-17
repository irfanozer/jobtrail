package com.jobtrail.sources.adapters;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobtrail.sources.JobSource;
import com.jobtrail.sources.SourceProvider;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class GreenhouseJobBoardAdapter implements JobBoardAdapter {
    private static final int MAX_JOBS = 5_000;
    private final RestClient client;

    @Autowired
    public GreenhouseJobBoardAdapter(RestClient.Builder builder) {
        this(BoardAdapterSupport.client(builder));
    }

    GreenhouseJobBoardAdapter(RestClient client) {
        this.client = client;
    }

    @Override
    public SourceProvider provider() {
        return SourceProvider.GREENHOUSE;
    }

    @Override
    public List<ExternalJob> fetch(JobSource source) {
        String key = BoardAdapterSupport.boardKey(source, provider());
        URI uri = URI.create("https://boards-api.greenhouse.io/v1/boards/" + key + "/jobs?content=true");
        JsonNode response = BoardAdapterSupport.fetchJson(client, uri, "Greenhouse");
        JsonNode jobs = response.path("jobs");
        if (!jobs.isArray()) {
            throw new JobSourceFetchException("Greenhouse returned an invalid job list.");
        }
        if (jobs.size() > MAX_JOBS) {
            throw new JobSourceFetchException("Greenhouse returned more than 5,000 jobs. No partial import was saved.");
        }
        Map<String, ExternalJob> result = new LinkedHashMap<>();
        for (JsonNode job : jobs) {
            String id = BoardAdapterSupport.requiredText(job, "id", "Greenhouse");
            String company = BoardAdapterSupport.text(job, "company_name");
            ExternalJob mapped = new ExternalJob(
                    id,
                    BoardAdapterSupport.plainText(BoardAdapterSupport.requiredText(job, "title", "Greenhouse")),
                    BoardAdapterSupport.plainText(company.isBlank() ? source.getName() : company),
                    BoardAdapterSupport.plainText(BoardAdapterSupport.text(job.path("location"), "name")),
                    BoardAdapterSupport.plainText(BoardAdapterSupport.text(job, "content")),
                    BoardAdapterSupport.httpsUrl(BoardAdapterSupport.requiredText(job, "absolute_url", "Greenhouse"), "Greenhouse"),
                    BoardAdapterSupport.timestamp(job, "first_published"),
                    BoardAdapterSupport.timestamp(job, "updated_at"));
            result.putIfAbsent(id, mapped);
        }
        return List.copyOf(result.values());
    }
}
