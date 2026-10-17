package com.jobtrail.sources.adapters;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobtrail.sources.JobSource;
import com.jobtrail.sources.SourceProvider;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class LeverJobBoardAdapter implements JobBoardAdapter {
    private static final int PAGE_SIZE = 100;
    private static final int MAX_PAGES = 50;
    private final RestClient client;
    private final int maxPages;

    @Autowired
    public LeverJobBoardAdapter(RestClient.Builder builder) {
        this(BoardAdapterSupport.client(builder), MAX_PAGES);
    }

    LeverJobBoardAdapter(RestClient client, int maxPages) {
        if (maxPages < 1 || maxPages > MAX_PAGES) {
            throw new IllegalArgumentException("The page limit must be between 1 and 50.");
        }
        this.client = client;
        this.maxPages = maxPages;
    }

    @Override
    public SourceProvider provider() {
        return SourceProvider.LEVER;
    }

    @Override
    public List<ExternalJob> fetch(JobSource source) {
        String key = BoardAdapterSupport.boardKey(source, provider());
        Map<String, ExternalJob> result = new LinkedHashMap<>();
        for (int page = 0; page < maxPages; page++) {
            URI uri = URI.create("https://api.lever.co/v0/postings/" + key
                    + "?mode=json&limit=" + PAGE_SIZE + "&skip=" + page * PAGE_SIZE);
            JsonNode jobs = BoardAdapterSupport.fetchJson(client, uri, "Lever");
            if (!jobs.isArray() || jobs.size() > PAGE_SIZE) {
                throw new JobSourceFetchException("Lever returned an invalid job page.");
            }
            int previousSize = result.size();
            for (JsonNode job : jobs) {
                String id = BoardAdapterSupport.requiredText(job, "id", "Lever");
                ExternalJob mapped = new ExternalJob(
                        id,
                        BoardAdapterSupport.plainText(BoardAdapterSupport.requiredText(job, "text", "Lever")),
                        BoardAdapterSupport.plainText(source.getName()),
                        BoardAdapterSupport.plainText(BoardAdapterSupport.text(job.path("categories"), "location")),
                        description(job),
                        BoardAdapterSupport.httpsUrl(BoardAdapterSupport.requiredText(job, "hostedUrl", "Lever"), "Lever"),
                        null,
                        null);
                result.putIfAbsent(id, mapped);
            }
            if (jobs.size() < PAGE_SIZE) {
                return List.copyOf(result.values());
            }
            if (result.size() == previousSize) {
                throw new JobSourceFetchException("Lever repeated a job page. No partial import was saved; try again later.");
            }
        }
        throw new JobSourceFetchException("Lever exceeded the import page limit. No partial import was saved.");
    }

    private static String description(JsonNode job) {
        List<String> sections = new ArrayList<>();
        sections.add(preferredText(job, "descriptionPlain", "description"));
        JsonNode lists = job.path("lists");
        if (lists.isArray()) {
            for (JsonNode list : lists) {
                sections.add(BoardAdapterSupport.plainText(BoardAdapterSupport.text(list, "text")));
                sections.add(BoardAdapterSupport.plainText(BoardAdapterSupport.text(list, "content")));
            }
        }
        sections.add(preferredText(job, "additionalPlain", "additional"));
        sections.add(preferredText(job, "salaryDescriptionPlain", "salaryDescription"));
        return String.join("\n\n", sections.stream().filter(section -> !section.isBlank()).toList());
    }

    private static String preferredText(JsonNode job, String plain, String html) {
        String text = BoardAdapterSupport.text(job, plain);
        return BoardAdapterSupport.plainText(text.isBlank() ? BoardAdapterSupport.text(job, html) : text);
    }
}
