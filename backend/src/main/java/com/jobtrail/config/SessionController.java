package com.jobtrail.config;
import com.jobtrail.demo.DemoWorkspace;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
@RestController
public class SessionController {
    private final boolean authenticationEnabled;
    private final boolean demoAvailable;
    public SessionController(@Value("${jobtrail.security.enabled:false}") boolean enabled,
            @Value("${jobtrail.demo.enabled:true}") boolean demoEnabled) {
        this.authenticationEnabled = enabled;
        this.demoAvailable = enabled && demoEnabled;
    }
    @GetMapping("/api/session")
    public SessionView session(Authentication authentication) {
        return new SessionView(authenticationEnabled, DemoWorkspace.isDemo(authentication), demoAvailable);
    }
    public record SessionView(boolean authenticationEnabled, boolean demo, boolean demoAvailable) {}
}
