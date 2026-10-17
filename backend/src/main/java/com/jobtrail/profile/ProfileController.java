package com.jobtrail.profile;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {
    private final ProfileService service;

    public ProfileController(ProfileService service) {
        this.service = service;
    }

    @GetMapping
    public ProfileResponse get() {
        return service.get();
    }

    @PutMapping
    public ProfileResponse update(@Valid @RequestBody ProfileRequest request) {
        return service.update(request);
    }
}
