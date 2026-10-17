package com.jobtrail.demo;

import com.jobtrail.jobs.TrackingStatus;
import com.jobtrail.profile.ProfileRequest;
import com.jobtrail.profile.ProfileResponse;
import com.jobtrail.profile.ProfileService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Fictional, session-only data. No owner profile or tracking repository is used here. */
@Component
public class DemoWorkspace {
    private static final String ATTRIBUTE = DemoWorkspace.class.getName() + ".state";
    private static final int MAX_TRACKED_JOBS = 500;

    public static boolean isDemo(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_DEMO".equals(authority.getAuthority()));
    }

    public ProfileResponse profile(HttpServletRequest request) {
        State state = state(request);
        synchronized (state) { return state.profile; }
    }

    public ProfileResponse update(HttpServletRequest servletRequest, ProfileRequest request) {
        ProfileRequest normalized = ProfileService.normalize(request);
        State state = state(servletRequest);
        synchronized (state) {
            if (request.version() != state.profile.version()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Your demo profile changed. Reload it before saving again.");
            }
            state.profile = new ProfileResponse(normalized.displayName(), normalized.preferredLocations(),
                    normalized.skills(), normalized.targetRoles(), normalized.careerFacts(),
                    state.profile.version() + 1, Instant.now());
            return state.profile;
        }
    }

    public Map<Long, TrackingStatus> tracking(HttpServletRequest request) {
        State state = state(request);
        synchronized (state) { return Map.copyOf(state.tracking); }
    }

    public TrackingStatus status(HttpServletRequest request, Long id, TrackingStatus status) {
        State state = state(request);
        synchronized (state) {
            if (status == TrackingStatus.NEW) {
                state.tracking.remove(id);
            } else {
                if (!state.tracking.containsKey(id) && state.tracking.size() >= MAX_TRACKED_JOBS) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "The temporary demo can track up to 500 jobs. Reset a job to New or start a new demo session.");
                }
                state.tracking.put(id, status);
            }
            return status;
        }
    }

    private State state(HttpServletRequest request) {
        HttpSession session = request.getSession();
        synchronized (session) {
            State state = (State) session.getAttribute(ATTRIBUTE);
            if (state == null) {
                state = new State();
                session.setAttribute(ATTRIBUTE, state);
            }
            return state;
        }
    }

    private static class State {
        private ProfileResponse profile = new ProfileResponse("Alex Morgan (fictional demo)",
                List.of("Remote", "Washington, DC"),
                List.of("Java", "Spring Boot", "Python", "PostgreSQL", "REST APIs", "Docker", "Git"),
                List.of("Junior Software Engineer", "Backend Engineer"),
                List.of(
                        new ProfileRequest.CareerFact("demo-api",
                                "Built a Java and Spring Boot task API with PostgreSQL and automated tests.",
                                "Fictional demo resume - sample project", true),
                        new ProfileRequest.CareerFact("demo-python",
                                "Created a Python script to validate CSV records and report missing fields.",
                                "Fictional demo resume - sample project", true),
                        new ProfileRequest.CareerFact("demo-docker",
                                "Containerized a sample backend with Docker and documented local setup.",
                                "Fictional demo resume - sample project", true)), 0, null);
        private final Map<Long, TrackingStatus> tracking = new HashMap<>();
    }
}
