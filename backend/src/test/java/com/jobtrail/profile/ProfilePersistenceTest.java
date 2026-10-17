package com.jobtrail.profile;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:profiletests;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ProfileService.class, ProfilePersistenceTest.JsonConfiguration.class})
class ProfilePersistenceTest {
    @Autowired ProfileService service;
    @Autowired PrivateProfileRepository repository;

    @Test
    void readingEmptyProfileDoesNotPersistFictionalData() {
        assertThat(service.get()).isEqualTo(ProfileResponse.empty());
        assertThat(repository.count()).isZero();
    }

    @Test
    void persistsOnlyEnteredDataAndIncrementsVersions() {
        ProfileRequest initial = new ProfileRequest("Example Person", List.of("Example City"),
                List.of("Java"), List.of("Software Engineer"),
                List.of(new ProfileRequest.CareerFact("fact-1", "Built an example service.", "Test fixture", true)), 0L);
        ProfileResponse created = service.update(initial);
        assertThat(created.version()).isEqualTo(1);
        assertThat(created.updatedAt()).isNotNull();
        assertThat(service.get()).isEqualTo(created);
        ProfileResponse updated = service.update(new ProfileRequest("Example Person", List.of(),
                List.of("Java", "Python"), List.of(), initial.careerFacts(), created.version()));
        assertThat(updated.version()).isEqualTo(2);
        assertThat(repository.count()).isEqualTo(1);
        assertThat(service.get().skills()).containsExactly("Java", "Python");
    }

    @Test
    void rejectsStaleEditsWithoutOverwritingTheCurrentProfile() {
        service.update(new ProfileRequest("", List.of(), List.of("Java"), List.of(), List.of(), 0L));
        assertThatThrownBy(() -> service.update(new ProfileRequest("", List.of(), List.of("Python"), List.of(), List.of(), 0L)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");
        assertThat(service.get().skills()).containsExactly("Java");
    }

    @TestConfiguration
    static class JsonConfiguration {
        @Bean ObjectMapper profileTestObjectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }
    }
}
