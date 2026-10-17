package com.jobtrail.matching;

import com.jobtrail.jobs.Job;
import com.jobtrail.demo.DemoWorkspace;
import jakarta.servlet.http.HttpServletRequest;
import com.jobtrail.jobs.JobRepository;
import com.jobtrail.profile.ProfileService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/jobs")
public class MatchController {
    private final JobRepository jobs;
    private final ProfileService profiles;
    private final RulesMatchEngine engine;
    private final DemoWorkspace demo;

    public MatchController(JobRepository jobs, ProfileService profiles, RulesMatchEngine engine, DemoWorkspace demo) {
        this.jobs = jobs;
        this.profiles = profiles;
        this.engine = engine;
        this.demo = demo;
    }

    @GetMapping("/{id}/match")
    public MatchResponse match(@PathVariable Long id, Authentication authentication, HttpServletRequest request) {
        Job job = jobs.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found."));
        return engine.compare(new RulesMatchEngine.JobDetails(job.getId(), job.getTitle(), job.getLocation(),
                job.getDescription(), String.valueOf(job.getLevel()), String.valueOf(job.getWorkplace())),
                DemoWorkspace.isDemo(authentication) ? demo.profile(request) : profiles.get());
    }
}
