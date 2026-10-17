package com.jobtrail.sources.adapters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.jobtrail.sources.JobSource;
import com.jobtrail.sources.SourceProvider;
import java.net.SocketTimeoutException;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GreenhouseJobBoardAdapterTest {
    private static final String URL = "https://boards-api.greenhouse.io/v1/boards/example/jobs?content=true";
    private MockRestServiceServer server;
    private GreenhouseJobBoardAdapter adapter;
    private JobSource source;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new GreenhouseJobBoardAdapter(builder.build());
        source = mock(JobSource.class);
        when(source.getProvider()).thenReturn(SourceProvider.GREENHOUSE);
        when(source.getBoardKey()).thenReturn("example");
        when(source.getName()).thenReturn("Example Company");
    }

    @Test
    void mapsJobsAndKeepsUpdateTimeSeparateFromPublication() {
        server.expect(requestTo(URL)).andExpect(method(HttpMethod.GET)).andRespond(withSuccess("""
                {"jobs":[{"id":42,"title":"Backend Engineer","location":{"name":"Arlington, VA"},
                "content":"&amp;lt;p&amp;gt;Build Java &amp;amp; SQL services.&amp;lt;/p&amp;gt;&lt;script&gt;bad()&lt;/script&gt;",
                "updated_at":"2026-10-15T15:30:00-04:00",
                "absolute_url":"https://boards.greenhouse.io/example/jobs/42"}]}
                """, MediaType.APPLICATION_JSON));

        ExternalJob job = adapter.fetch(source).getFirst();

        assertThat(job.externalId()).isEqualTo("42");
        assertThat(job.title()).isEqualTo("Backend Engineer");
        assertThat(job.company()).isEqualTo("Example Company");
        assertThat(job.location()).isEqualTo("Arlington, VA");
        assertThat(job.description()).isEqualTo("Build Java & SQL services.");
        assertThat(job.publishedAt()).isNull();
        assertThat(job.sourceUpdatedAt()).isEqualTo(Instant.parse("2026-10-15T19:30:00Z"));
        server.verify();
    }

    @Test
    void usesOnlyAnExplicitPublicationDate() {
        server.expect(requestTo(URL)).andRespond(withSuccess("""
                {"jobs":[{"id":43,"title":"Engineer","company_name":"Actual Employer",
                "first_published":"2026-10-01T12:00:00Z","updated_at":"not-a-date",
                "absolute_url":"https://boards.greenhouse.io/example/jobs/43"}]}
                """, MediaType.APPLICATION_JSON));

        ExternalJob job = adapter.fetch(source).getFirst();

        assertThat(job.publishedAt()).isEqualTo(Instant.parse("2026-10-01T12:00:00Z"));
        assertThat(job.sourceUpdatedAt()).isNull();
        assertThat(job.company()).isEqualTo("Actual Employer");
        assertThat(job.description()).isEmpty();
        server.verify();
    }

    @Test
    void refusesAnUnsafeBoardKeyBeforeMakingARequest() {
        when(source.getBoardKey()).thenReturn("example/../../private?secret=x");

        assertThatThrownBy(() -> adapter.fetch(source)).isInstanceOf(IllegalArgumentException.class);
        server.verify();
    }

    @Test
    void rejectsMalformedListsRatherThanInterpretingThemAsNoJobs() {
        server.expect(requestTo(URL)).andRespond(withSuccess("{\"error\":\"invalid board\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> adapter.fetch(source)).isInstanceOf(JobSourceFetchException.class)
                .hasMessage("Greenhouse returned an invalid job list.");
        server.verify();
    }

    @Test
    void rejectsUnsafeJobLinks() {
        server.expect(requestTo(URL)).andRespond(withSuccess("""
                {"jobs":[{"id":1,"title":"Engineer","absolute_url":"javascript:alert(1)"}]}
                """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> adapter.fetch(source)).isInstanceOf(JobSourceFetchException.class)
                .hasMessageContaining("safe HTTPS link");
        server.verify();
    }

    @Test
    void exposesStatusWithoutTheUpstreamResponseBody() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .body("sensitive upstream details").contentType(MediaType.TEXT_PLAIN));

        assertThatThrownBy(() -> adapter.fetch(source)).isInstanceOf(JobSourceFetchException.class)
                .hasMessageContaining("HTTP 429").hasMessageNotContaining("sensitive").hasNoCause();
        server.verify();
    }

    @Test
    void reportsNetworkTimeoutWithoutLeakingTheNetworkException() {
        server.expect(requestTo(URL)).andRespond(withException(new SocketTimeoutException("private connection detail")));

        assertThatThrownBy(() -> adapter.fetch(source)).isInstanceOf(JobSourceFetchException.class)
                .hasMessageContaining("connection or response limit").hasMessageNotContaining("private").hasNoCause();
        server.verify();
    }

    @Test
    void rejectsRedirectResponses() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.FOUND)
                .header("Location", "https://unrelated.example/private"));

        assertThatThrownBy(() -> adapter.fetch(source)).isInstanceOf(JobSourceFetchException.class)
                .hasMessageContaining("HTTP 302");
        server.verify();
    }
}
