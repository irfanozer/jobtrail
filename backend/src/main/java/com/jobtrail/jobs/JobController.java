package com.jobtrail.jobs;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/jobs")
public class JobController {
    private final JobSearchService service;
    public JobController(JobSearchService service) { this.service = service; }
    @GetMapping
    public JobSearchService.JobPage search(@RequestParam(defaultValue="") String q,
        @RequestParam(required=false) JobLevel level, @RequestParam(required=false) Workplace workplace,
        @RequestParam(required=false) TrackingStatus status, @RequestParam(required=false) Long sourceId,
        @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="25") int size,
        @RequestParam(defaultValue="priority") String sort, @RequestParam(defaultValue="true") boolean engineeringOnly,
        @RequestParam(defaultValue="false") boolean includeInactive) {
        return service.search(q,level,workplace,status,sourceId,page,size,sort,engineeringOnly,includeInactive);
    }
    @GetMapping("/{id}") public JobView get(@PathVariable Long id) { return service.get(id); }
    @PatchMapping("/{id}/status") public JobView status(@PathVariable Long id, @Valid @RequestBody StatusRequest request) {
        return service.status(id, request.status());
    }
    public record StatusRequest(@NotNull TrackingStatus status) {}
}

