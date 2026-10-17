package com.jobtrail.config;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:securityenabledtests;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=true",
        "jobtrail.security.enabled=true", "JOBTRAIL_USERNAME=test-owner",
        "JOBTRAIL_PASSWORD=test-only-password-12345",
        "jobtrail.collection.startup=false", "jobtrail.collection.scheduled=false"})
@AutoConfigureMockMvc
@Transactional
class SecurityEnabledTest {
    private static final String PROFILE = """
            {"displayName":"","preferredLocations":[],"skills":[],"targetRoles":[],"careerFacts":[],"version":0}
            """;
    @Autowired MockMvc mvc;

    @Test
    void healthIsPublicWhileProfileJobsAndOtherActuatorEndpointsAreProtected() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/api/profile")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/jobs")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/info")).andExpect(status().is3xxRedirection());
    }

    @Test
    void loginPageIsPublicAndRejectsWrongCredentials() throws Exception {
        Cookie token = loginToken();
        mvc.perform(post("/login").cookie(token).param("_csrf", token.getValue())
                        .param("username", "test-owner").param("password", "wrong-password"))
                .andExpect(unauthenticated()).andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void realLoginSessionCanReadProfileAndCookieCsrfTokenProtectsUpdates() throws Exception {
        Cookie loginToken = loginToken();
        MvcResult login = mvc.perform(post("/login").cookie(loginToken).param("_csrf", loginToken.getValue())
                        .param("username", "test-owner").param("password", "test-only-password-12345"))
                .andExpect(authenticated().withUsername("test-owner"))
                .andExpect(redirectedUrl("/")).andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        assertThat(session).isNotNull();
        MvcResult profile = mvc.perform(get("/api/profile").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(0))
                .andExpect(cookie().exists("XSRF-TOKEN")).andReturn();
        Cookie token = profile.getResponse().getCookie("XSRF-TOKEN");
        assertThat(token).isNotNull();
        mvc.perform(put("/api/profile").session(session).contentType(MediaType.APPLICATION_JSON).content(PROFILE))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/profile").session(session).cookie(token).header("X-XSRF-TOKEN", "invalid")
                        .contentType(MediaType.APPLICATION_JSON).content(PROFILE))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/profile").session(session).cookie(token).header("X-XSRF-TOKEN", token.getValue())
                        .contentType(MediaType.APPLICATION_JSON).content(PROFILE))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
    }

    private Cookie loginToken() throws Exception {
        return mvc.perform(get("/login")).andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN")).andReturn().getResponse().getCookie("XSRF-TOKEN");
    }

    @Test
    void authenticatedMutationWithoutCsrfFailsBeforeTheController() throws Exception {
        mvc.perform(patch("/api/jobs/999999/status").with(user("test-owner").roles("OWNER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"APPLIED\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/logout").with(user("test-owner").roles("OWNER")))
                .andExpect(status().isForbidden());
    }
}
