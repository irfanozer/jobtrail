package com.jobtrail.config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
@RestController
public class SessionController {
    private final boolean authenticationEnabled;
    public SessionController(@Value("${jobtrail.security.enabled:false}") boolean enabled) { this.authenticationEnabled = enabled; }
    @GetMapping("/api/session")
    public SessionView session() { return new SessionView(authenticationEnabled); }
    public record SessionView(boolean authenticationEnabled) {}
}
