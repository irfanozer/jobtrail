package com.jobtrail.profile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProfileService {
    private final PrivateProfileRepository repository;
    private final ObjectMapper mapper;

    public ProfileService(PrivateProfileRepository repository, ObjectMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public ProfileResponse get() {
        return repository.findById(1L).map(this::response).orElseGet(ProfileResponse::empty);
    }

    @Transactional
    public ProfileResponse update(ProfileRequest request) {
        ProfileRequest normalized = normalize(request);
        PrivateProfile existing = repository.findById(1L).orElse(null);
        long currentVersion = existing == null ? 0 : existing.publicVersion();
        if (request.version() != currentVersion) {
            throw conflict();
        }
        String payload;
        try {
            payload = mapper.writeValueAsString(normalized);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to save the private profile.", ex);
        }
        if (existing == null) {
            existing = new PrivateProfile(payload, Instant.now());
        } else {
            existing.replace(payload, Instant.now());
        }
        try {
            return response(repository.saveAndFlush(existing));
        } catch (OptimisticLockingFailureException | DataIntegrityViolationException ex) {
            throw conflict();
        }
    }

    private ProfileResponse response(PrivateProfile entity) {
        try {
            ProfileRequest stored = mapper.readValue(entity.getPayload(), ProfileRequest.class);
            return new ProfileResponse(stored.displayName(), stored.preferredLocations(), stored.skills(),
                    stored.targetRoles(), stored.careerFacts(), entity.publicVersion(), entity.getUpdatedAt());
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to read the private profile.", ex);
        }
    }

    public static ProfileRequest normalize(ProfileRequest request) {
        List<String> locations = uniqueTrimmed(request.preferredLocations());
        List<String> skills = uniqueTrimmed(request.skills());
        List<String> roles = uniqueTrimmed(request.targetRoles());
        Set<String> factIds = new HashSet<>();
        List<ProfileRequest.CareerFact> facts = request.careerFacts().stream().map(fact -> {
            String id = fact.id().strip();
            if (!factIds.add(id)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Career fact IDs must be unique.");
            }
            if (Boolean.TRUE.equals(fact.confirmed()) && fact.source().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Confirmed career facts need a source, such as a resume section or your own confirmation.");
            }
            return new ProfileRequest.CareerFact(id, fact.statement().strip(), fact.source().strip(), fact.confirmed());
        }).toList();
        return new ProfileRequest(request.displayName().strip(), locations, skills, roles, facts, request.version());
    }

    private static List<String> uniqueTrimmed(List<String> values) {
        Set<String> seen = new HashSet<>();
        return values.stream().map(String::strip)
                .filter(value -> seen.add(value.toLowerCase(Locale.ROOT))).toList();
    }

    private static ResponseStatusException conflict() {
        return new ResponseStatusException(HttpStatus.CONFLICT,
                "Your profile changed. Reload it before saving again.");
    }
}
