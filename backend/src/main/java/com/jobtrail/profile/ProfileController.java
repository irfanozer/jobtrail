package com.jobtrail.profile;

import com.jobtrail.demo.DemoWorkspace;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {
    private final ProfileService service;
    private final DemoWorkspace demo;

    public ProfileController(ProfileService service, DemoWorkspace demo) {
        this.service = service;
        this.demo = demo;
    }

    @GetMapping
    public ProfileResponse get(Authentication authentication, HttpServletRequest request) {
        return DemoWorkspace.isDemo(authentication) ? demo.profile(request) : service.get();
    }

    @PutMapping
    public ProfileResponse update(@Valid @RequestBody ProfileRequest request,
            Authentication authentication, HttpServletRequest servletRequest) {
        return DemoWorkspace.isDemo(authentication) ? demo.update(servletRequest, request) : service.update(request);
    }
}
