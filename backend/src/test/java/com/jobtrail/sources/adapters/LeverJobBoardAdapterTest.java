package com.jobtrail.sources.adapters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.jobtrail.sources.JobSource;
import com.jobtrail.sources.SourceProvider;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class LeverJobBoardAdapterTest {
    private MockRestServiceServer server;
    private RestClient client;
    private JobSource source;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = builder.build();
        source = mock(JobSource.class);
        when(source.getProvider()).thenReturn(SourceProvider.LEVER);
        when(source.getBoardKey()).thenReturn("example");
        when(source.getName()).thenReturn("Example Company");
    }

    @Test
    void readsEveryPageAndPreservesAllDescriptionSections() {
        server.expect(requestTo(url(0))).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(page(0, 100), MediaType.APPLICATION_JSON));
        server.expect(requestTo(url(100))).andRespond(withSuccess("""
                [{"id":"job-100","text":"Backend Engineer","categories":{"location":"Remote"},
                "descriptionPlain":"Build Java services.","description":"<p>Duplicate opening</p>",
                "lists":[{"text":"Requirements","content":"<ul><li>PostgreSQL</li><li>JUnit</li></ul>"}],
                "additional":"<p>Inclusive team.</p><script>bad()</script>",
                "salaryDescriptionPlain":"Published range: $100,000 - $120,000",
                "createdAt":1720000000000,"hostedUrl":"https://jobs.lever.co/example/job-100"}]
                """, MediaType.APPLICATION_JSON));

        List<ExternalJob> jobs = new LeverJobBoardAdapter(client, 50).fetch(source);

        assertThat(jobs).hasSize(101);
        ExternalJob job = jobs.getLast();
        assertThat(job.description()).isEqualTo("Build Java services.\n\nRequirements\n\nPostgreSQL JUnit\n\nInclusive team.\n\nPublished range: $100,000 - $120,000");
        assertThat(job.company()).isEqualTo("Example Company");
        assertThat(job.location()).isEqualTo("Remote");
        assertThat(job.publishedAt()).isNull();
        assertThat(job.sourceUpdatedAt()).isNull();
        server.verify();
    }

    @Test
    void checksTheNextPageWhenTheLastPageIsExactlyFull() {
        server.expect(requestTo(url(0))).andRespond(withSuccess(page(0, 100), MediaType.APPLICATION_JSON));
        server.expect(requestTo(url(100))).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(new LeverJobBoardAdapter(client, 50).fetch(source)).hasSize(100);
        server.verify();
    }

    @Test
    void failsInsteadOfSavingPartialResultsWhenPageLimitIsReached() {
        server.expect(requestTo(url(0))).andRespond(withSuccess(page(0, 100), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> new LeverJobBoardAdapter(client, 1).fetch(source))
                .isInstanceOf(JobSourceFetchException.class).hasMessageContaining("page limit");
        server.verify();
    }

    @Test
    void detectsARepeatedFullPage() {
        server.expect(requestTo(url(0))).andRespond(withSuccess(page(0, 100), MediaType.APPLICATION_JSON));
        server.expect(requestTo(url(100))).andRespond(withSuccess(page(0, 100), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> new LeverJobBoardAdapter(client, 50).fetch(source))
                .isInstanceOf(JobSourceFetchException.class).hasMessageContaining("repeated a job page");
        server.verify();
    }

    @Test
    void doesNotReturnPartialResultsIfALaterPageFails() {
        server.expect(requestTo(url(0))).andRespond(withSuccess(page(0, 100), MediaType.APPLICATION_JSON));
        server.expect(requestTo(url(100))).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> new LeverJobBoardAdapter(client, 50).fetch(source))
                .isInstanceOf(JobSourceFetchException.class).hasMessageContaining("HTTP 503");
        server.verify();
    }

    @Test
    void treatsAnEmptyBoardAsAValidCompleteResult() {
        server.expect(requestTo(url(0))).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(new LeverJobBoardAdapter(client, 50).fetch(source)).isEmpty();
        server.verify();
    }

    @Test
    void refusesMalformedResponses() {
        server.expect(requestTo(url(0))).andRespond(withSuccess("{\"jobs\":[]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> new LeverJobBoardAdapter(client, 50).fetch(source))
                .isInstanceOf(JobSourceFetchException.class).hasMessage("Lever returned an invalid job page.");
        server.verify();
    }

    @Test
    void refusesAnUnsafeBoardKeyBeforeMakingARequest() {
        when(source.getBoardKey()).thenReturn("https://unrelated.example");

        assertThatThrownBy(() -> new LeverJobBoardAdapter(client, 50).fetch(source))
                .isInstanceOf(IllegalArgumentException.class);
        server.verify();
    }

    private static String url(int skip) {
        return "https://api.lever.co/v0/postings/example?mode=json&limit=100&skip=" + skip;
    }

    private static String page(int start, int count) {
        ArrayNode jobs = new ObjectMapper().createArrayNode();
        for (int index = start; index < start + count; index++) {
            jobs.addObject().put("id", "job-" + index).put("text", "Engineer " + index)
                    .put("hostedUrl", "https://jobs.lever.co/example/job-" + index);
        }
        return jobs.toString();
    }
}
