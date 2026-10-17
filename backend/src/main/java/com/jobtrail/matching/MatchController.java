package com.jobtrail.matching;

import com.jobtrail.jobs.Job;
import com.jobtrail.jobs.JobRepository;
import com.jobtrail.profile.ProfileService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/jobs")
public class MatchController {
    private final JobRepository jobs;
    private final ProfileService profiles;
    private final RulesMatchEngine engine;

    public MatchController(JobRepository jobs, ProfileService profiles, RulesMatchEngine engine) {
        this.jobs = jobs;
        this.profiles = profiles;
        this.engine = engine;
    }

    @GetMapping("/{id}/match")
    public MatchResponse match(@PathVariable Long id) {
        Job job = jobs.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found."));
        return engine.compare(new RulesMatchEngine.JobDetails(job.getId(), job.getTitle(), job.getLocation(),
                job.getDescription(), String.valueOf(job.getLevel()), String.valueOf(job.getWorkplace())), profiles.get());
    }
}
