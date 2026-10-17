package com.jobtrail.sources.adapters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BoardAdapterSupportTest {
    @ParameterizedTest
    @ValueSource(strings = {"http://jobs.example/job", "javascript:alert(1)", "https://name:pass@jobs.example/job",
            "https://jobs.example:8080/job", "//jobs.example/job", "/job", "https://jobs.example/job\n"})
    void rejectsUnsafeLinks(String url) {
        assertThatThrownBy(() -> BoardAdapterSupport.httpsUrl(url, "Board"))
                .isInstanceOf(JobSourceFetchException.class).hasMessageContaining("safe HTTPS link");
    }

    @Test
    void acceptsOrdinaryHttpsLinksWithQueryParameters() {
        assertThat(BoardAdapterSupport.httpsUrl("https://careers.example/job?gh_jid=123&source=board", "Board"))
                .isEqualTo("https://careers.example/job?gh_jid=123&source=board");
    }

    @Test
    void stripsActiveMarkupAndDecodesEntitiesWithoutExecutingAnything() {
        assertThat(BoardAdapterSupport.plainText("<style>.x{color:red}</style><script>hidden()</script><p>Java &amp; SQL</p><ul><li>Testing</li><li>APIs</li></ul>"))
                .isEqualTo("Java & SQL Testing APIs");
    }
}
