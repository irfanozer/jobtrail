package com.jobtrail.jobs;
import com.jobtrail.demo.DemoWorkspace;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
@RestController
@RequestMapping("/api/jobs")
public class JobController {
    private final JobSearchService service;
    private final DemoWorkspace demo;
    public JobController(JobSearchService service, DemoWorkspace demo) { this.service = service; this.demo = demo; }
    @GetMapping
    public JobSearchService.JobPage search(@RequestParam(defaultValue="") String q,
        @RequestParam(required=false) JobLevel level, @RequestParam(required=false) Workplace workplace,
        @RequestParam(required=false) TrackingStatus status, @RequestParam(required=false) Long sourceId,
        @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="25") int size,
        @RequestParam(defaultValue="priority") String sort, @RequestParam(defaultValue="true") boolean engineeringOnly,
        @RequestParam(defaultValue="false") boolean includeInactive,
        Authentication authentication, HttpServletRequest request) {
        return service.search(q,level,workplace,status,sourceId,page,size,sort,engineeringOnly,includeInactive,
                DemoWorkspace.isDemo(authentication) ? demo.tracking(request) : null);
    }
    @GetMapping("/{id}") public JobView get(@PathVariable Long id, Authentication authentication, HttpServletRequest request) {
        return DemoWorkspace.isDemo(authentication)
                ? service.get(id, demo.tracking(request).getOrDefault(id, TrackingStatus.NEW)) : service.get(id);
    }
    @PatchMapping("/{id}/status") public JobView status(@PathVariable Long id, @Valid @RequestBody StatusRequest request,
            Authentication authentication, HttpServletRequest servletRequest) {
        if (DemoWorkspace.isDemo(authentication)) {
            JobView job = service.get(id, TrackingStatus.NEW);
            return job.withStatus(demo.status(servletRequest, id, request.status()));
        }
        return service.status(id, request.status());
    }
    public record StatusRequest(@NotNull TrackingStatus status) {}
}

