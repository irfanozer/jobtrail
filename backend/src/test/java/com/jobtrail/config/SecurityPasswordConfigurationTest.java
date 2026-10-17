package com.jobtrail.config;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class SecurityPasswordConfigurationTest {
    private final SecurityConfig config = new SecurityConfig();

    @Test
    void authenticationCannotStartWithMissingOrShortPassword() {
        assertThatThrownBy(() -> config.users(true, "owner", ""))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("at least 16 characters");
        assertThatThrownBy(() -> config.users(true, "owner", "x".repeat(15)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void disabledLocalAuthenticationDoesNotRequireConfiguredPassword() {
        assertThat(config.users(false, "owner", "").loadUserByUsername("owner").getUsername()).isEqualTo("owner");
    }
}
