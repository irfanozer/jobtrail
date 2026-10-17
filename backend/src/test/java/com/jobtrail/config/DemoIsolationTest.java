package com.jobtrail.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtrail.jobs.*;
import com.jobtrail.profile.*;
import com.jobtrail.sources.*;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:demoisolation;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=true",
        "jobtrail.security.enabled=true", "jobtrail.demo.enabled=true",
        "JOBTRAIL_USERNAME=test-owner", "JOBTRAIL_PASSWORD=test-only-password-12345",
        "jobtrail.collection.startup=false", "jobtrail.collection.scheduled=false"})
@AutoConfigureMockMvc
@Transactional
class DemoIsolationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JobRepository jobs;
    @Autowired JobSourceRepository sources;
    @Autowired ProfileService profiles;
    private JobSource source;

    @BeforeEach void seedOwner() {
        profiles.update(new ProfileRequest("Private owner fixture", List.of("Private location"),
                List.of("OwnerOnlySkill"), List.of("Private target"), List.of(), 0L));
        source = new JobSource();
        source.setName("Public demo test board");
        source.setProvider(SourceProvider.GREENHOUSE);
        source.setBoardKey("demo-isolation-fixture");
        source.setEnabled(true);
        source = sources.saveAndFlush(source);
    }

    @Test void demoLoginRotatesSessionAndCreatesOnlyTemporaryDemoAuthority() throws Exception {
        MockHttpSession prior = new MockHttpSession();
        String previousId = prior.getId();
        MockHttpSession demo = loginDemo(prior);
        assertThat(demo.getId()).isNotEqualTo(previousId);
        assertThat(demo.getMaxInactiveInterval()).isEqualTo(1800);
        mvc.perform(get("/api/session").session(demo)).andExpect(status().isOk())
                .andExpect(jsonPath("$.demo").value(true))
                .andExpect(jsonPath("$.authenticationEnabled").value(true));
        mvc.perform(get("/api/profile")).andExpect(status().isUnauthorized());
    }

    @Test void profileEditsAndMatchEvidenceAreIsolatedFromOwnerAndOtherVisitors() throws Exception {
        MockHttpSession first = loginDemo(new MockHttpSession());
        MockHttpSession second = loginDemo(new MockHttpSession());
        JsonNode initial = json(mvc.perform(get("/api/profile").session(first))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(initial.get("displayName").asText()).contains("fictional");
        assertThat(initial.toString()).doesNotContain("Private owner fixture", "OwnerOnlySkill", "Private target");
        String edit = """
                {"displayName":"Demo visitor edit","preferredLocations":[],"skills":["Java"],
                 "targetRoles":[],"careerFacts":[],"version":0}
                """;
        mvc.perform(withCsrf(put("/api/profile"), first)
                        .contentType(MediaType.APPLICATION_JSON).content(edit))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        mvc.perform(get("/api/profile").session(second))
                .andExpect(jsonPath("$.displayName").value(initial.get("displayName").asText()))
                .andExpect(jsonPath("$.version").value(0));
        mvc.perform(get("/api/profile").with(user("test-owner").roles("OWNER")))
                .andExpect(jsonPath("$.displayName").value("Private owner fixture"));
        assertThat(profiles.get().displayName()).isEqualTo("Private owner fixture");
        Job job = saveJob("Java Backend Engineer", TrackingStatus.NEW);
        String match = mvc.perform(get("/api/jobs/{id}/match", job.getId()).session(first))
                .andExpect(status().isOk()).andExpect(jsonPath("$.profileVersion").value(1))
                .andReturn().getResponse().getContentAsString();
        assertThat(match).contains("Your profile lists Java").doesNotContain("OwnerOnlySkill");
        mvc.perform(withCsrf(put("/api/profile"), first)
                        .contentType(MediaType.APPLICATION_JSON).content(edit)).andExpect(status().isConflict());
    }

    @Test void searchPaginationAndEveryStatusResponseUseOnlyVisitorTracking() throws Exception {
        Job ownerHidden = saveJob("Java Software Engineer A", TrackingStatus.HIDDEN);
        Job ownerApplied = saveJob("Java Software Engineer B", TrackingStatus.APPLIED);
        Job ownerNew = saveJob("Java Software Engineer C", TrackingStatus.NEW);
        MockHttpSession first = loginDemo(new MockHttpSession());
        MockHttpSession second = loginDemo(new MockHttpSession());
        mvc.perform(get("/api/jobs").session(first).param("sourceId", source.getId().toString()))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.items[*].status").value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("NEW"))));
        track(first, ownerHidden, "SAVED");
        track(first, ownerApplied, "HIDDEN");
        track(first, ownerNew, "APPLIED");
        for (String state : List.of("SAVED", "APPLIED", "HIDDEN")) {
            mvc.perform(get("/api/jobs").session(first).param("sourceId", source.getId().toString())
                            .param("status", state).param("size", "1"))
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.items[0].status").value(state));
        }
        mvc.perform(get("/api/jobs").session(first).param("sourceId", source.getId().toString()).param("size", "1"))
                .andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.totalPages").value(2));
        mvc.perform(get("/api/jobs").session(second).param("sourceId", source.getId().toString()))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.items[*].status").value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("NEW"))));
        mvc.perform(get("/api/jobs/{id}", ownerHidden.getId()).session(first)).andExpect(jsonPath("$.status").value("SAVED"));
        mvc.perform(get("/api/jobs/{id}", ownerHidden.getId()).session(second)).andExpect(jsonPath("$.status").value("NEW"));
        assertThat(jobs.findById(ownerHidden.getId()).orElseThrow().getStatus()).isEqualTo(TrackingStatus.HIDDEN);
        assertThat(jobs.findById(ownerApplied.getId()).orElseThrow().getStatus()).isEqualTo(TrackingStatus.APPLIED);
        track(first, ownerHidden, "NEW");
        mvc.perform(get("/api/jobs").session(first).param("status", "NEW").param("sourceId", source.getId().toString()))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test void demoCannotMutateSourcesOrRefreshOrReachOwnerOnlyApis() throws Exception {
        MockHttpSession demo = loginDemo(new MockHttpSession());
        mvc.perform(withCsrf(post("/api/sources"), demo).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"No\",\"provider\":\"GREENHOUSE\",\"boardKey\":\"no\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(withCsrf(patch("/api/sources/{id}", source.getId()), demo)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isForbidden());
        mvc.perform(withCsrf(post("/api/refresh"), demo)).andExpect(status().isForbidden());
        mvc.perform(get("/api/refresh/latest").session(demo)).andExpect(status().isForbidden());
        mvc.perform(get("/api/unknown-owner-endpoint").session(demo)).andExpect(status().isForbidden());
        assertThat(sources.findById(source.getId()).orElseThrow().getEnabled()).isTrue();
    }

    @Test void csrfAndLogoutProtectDemoAndFreshVisitResetsChanges() throws Exception {
        mvc.perform(post("/demo")).andExpect(status().isForbidden());
        MockHttpSession demo = loginDemo(new MockHttpSession());
        Job job = saveJob("Java Engineer", TrackingStatus.NEW);
        mvc.perform(patch("/api/jobs/{id}/status", job.getId()).session(demo)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SAVED\"}"))
                .andExpect(status().isForbidden());
        track(demo, job, "SAVED");
        mvc.perform(withCsrf(post("/logout"), demo)).andExpect(status().is3xxRedirection());
        assertThat(demo.isInvalid()).isTrue();
        MockHttpSession fresh = loginDemo(new MockHttpSession());
        mvc.perform(get("/api/jobs/{id}", job.getId()).session(fresh)).andExpect(jsonPath("$.status").value("NEW"));
        mvc.perform(get("/api/profile").session(fresh)).andExpect(jsonPath("$.version").value(0));
    }

    private MockHttpSession loginDemo(MockHttpSession prior) throws Exception {
        Cookie token = mvc.perform(get("/login").session(prior)).andExpect(status().isOk())
                .andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertThat(token).isNotNull();
        return (MockHttpSession) mvc.perform(post("/demo").session(prior).cookie(token)
                        .param("_csrf", token.getValue()))
                .andExpect(authenticated().withRoles("DEMO")).andExpect(redirectedUrl("/"))
                .andReturn().getRequest().getSession(false);
    }

    private void track(MockHttpSession session, Job job, String value) throws Exception {
        mvc.perform(withCsrf(patch("/api/jobs/{id}/status", job.getId()), session)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + value + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(value));
    }

    private MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder request, MockHttpSession session)
            throws Exception {
        Cookie token = mvc.perform(get("/api/session").session(session)).andExpect(status().isOk())
                .andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertThat(token).isNotNull();
        return request.session(session).cookie(token).header("X-XSRF-TOKEN", token.getValue());
    }

    private JsonNode json(String value) throws Exception { return mapper.readTree(value); }

    private Job saveJob(String title, TrackingStatus state) {
        Job job = new Job(); job.setSourceId(source.getId()); job.setExternalId(title);
        job.setTitle(title); job.setCompany("Fictional test employer"); job.setLocation("Remote");
        job.setDescription("Java, Spring Boot and PostgreSQL."); job.setUrl("https://example.com/jobs/test");
        job.setWorkplace(Workplace.REMOTE); job.setLevel(JobLevel.MID); job.setStatus(state);
        job.setFirstSeenAt(Instant.parse("2026-01-01T00:00:00Z")); job.setLastSeenAt(Instant.now());
        job.setActive(true); job.setContentHash("b".repeat(64));
        return jobs.saveAndFlush(job);
    }
}
