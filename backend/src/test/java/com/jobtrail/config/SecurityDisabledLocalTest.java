package com.jobtrail.config;

import com.jobtrail.jobs.*;
import com.jobtrail.sources.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:securitydisabledtests;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=true",
        "jobtrail.security.enabled=false", "JOBTRAIL_PASSWORD=",
        "jobtrail.collection.startup=false", "jobtrail.collection.scheduled=false"})
@ActiveProfiles("local")
@AutoConfigureMockMvc
@Transactional
class SecurityDisabledLocalTest {
    @Autowired MockMvc mvc;
    @Autowired Environment environment;
    @Autowired JobRepository jobs;
    @Autowired JobSourceRepository sources;

    @Test
    void localConfigurationBindsLoopbackAndAllowsProfileEditingWithoutLoginOrCsrf() throws Exception {
        assertThat(environment.getProperty("server.address")).isEqualTo("127.0.0.1");
        mvc.perform(get("/api/profile")).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(0));
        mvc.perform(put("/api/profile").contentType(MediaType.APPLICATION_JSON).content("""
                        {"displayName":"","preferredLocations":[],"skills":["Java"],"targetRoles":[],"careerFacts":[],"version":0}
                        """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"?page=-1", "?size=0", "?size=101", "?sort=unsupported", "?level=INVALID",
            "?workplace=INVALID", "?status=INVALID", "?page=not-a-number"})
    void invalidJobQueryParametersReturnBadRequest(String query) throws Exception {
        mvc.perform(get("/api/jobs" + query)).andExpect(status().isBadRequest());
    }

    @Test
    void oversizedSearchIsRejected() throws Exception {
        mvc.perform(get("/api/jobs").param("q", "x".repeat(201))).andExpect(status().isBadRequest());
    }

    @Test
    void engineeringOnlyIsTheHttpDefaultAndCanBeDisabledExplicitly() throws Exception {
        JobSource source = new JobSource();
        source.setName("Example Test Board");
        source.setBoardKey("test-default-engineering");
        source.setProvider(SourceProvider.GREENHOUSE);
        source.setEnabled(true);
        sources.saveAndFlush(source);
        save(source.getId(), "engineering", "Software Engineer", "London");
        save(source.getId(), "commercial", "Account Executive", "Arlington");
        mvc.perform(get("/api/jobs"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].title").value("Software Engineer"));
        mvc.perform(get("/api/jobs").param("engineeringOnly", "false"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void statusBodyValidationRejectsMissingOrInvalidState() throws Exception {
        mvc.perform(patch("/api/jobs/1/status").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/jobs/1/status").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INVALID\"}"))
                .andExpect(status().isBadRequest());
    }

    private void save(Long sourceId, String externalId, String title, String location) {
        Job job = new Job();
        job.setSourceId(sourceId);
        job.setExternalId(externalId);
        job.setTitle(title);
        job.setCompany("Example Company");
        job.setLocation(location);
        job.setDescription("Example job description.");
        job.setUrl("https://example.com/jobs/" + externalId);
        job.setLevel(JobLevel.UNKNOWN);
        job.setWorkplace(Workplace.UNKNOWN);
        job.setStatus(TrackingStatus.NEW);
        job.setFirstSeenAt(Instant.parse("2026-01-01T00:00:00Z"));
        job.setLastSeenAt(Instant.parse("2026-01-01T00:00:00Z"));
        job.setActive(true);
        job.setContentHash("b".repeat(64));
        jobs.saveAndFlush(job);
    }
}
