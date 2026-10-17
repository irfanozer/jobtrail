package com.jobtrail.jobs;

import com.jobtrail.sources.JobSourceRepository;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import jakarta.persistence.criteria.Predicate;

@Service
public class JobSearchService {
    private final JobRepository jobs;
    private final JobSourceRepository sources;
    public JobSearchService(JobRepository jobs, JobSourceRepository sources) { this.jobs = jobs; this.sources = sources; }

    @Transactional(readOnly=true)
    public JobPage search(String q, JobLevel level, Workplace workplace, TrackingStatus status,
                          Long sourceId, int page, int size, String sort, boolean engineeringOnly, boolean includeInactive) {
        return search(q, level, workplace, status, sourceId, page, size, sort, engineeringOnly, includeInactive, null);
    }

    @Transactional(readOnly=true)
    public JobPage search(String q, JobLevel level, Workplace workplace, TrackingStatus status,
                          Long sourceId, int page, int size, String sort, boolean engineeringOnly, boolean includeInactive,
                          Map<Long, TrackingStatus> demoTracking) {
        if (page < 0 || size < 1 || size > 100 || q.length() > 200 || !Set.of("priority","newest").contains(sort))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid pagination or search options.");
        Specification<Job> spec = (root, query, cb) -> {
            List<Predicate> filters = new ArrayList<>();
            if (!includeInactive) filters.add(cb.isTrue(root.get("active")));
            if (engineeringOnly) filters.add(cb.isTrue(root.get("engineeringRole")));
            if (demoTracking == null) {
                if (status != null) filters.add(cb.equal(root.get("status"), status));
                else filters.add(cb.notEqual(root.get("status"), TrackingStatus.HIDDEN));
            } else if (status == null || status == TrackingStatus.NEW) {
                Set<Long> excluded = demoTracking.entrySet().stream()
                        .filter(entry -> status == TrackingStatus.NEW || entry.getValue() == TrackingStatus.HIDDEN)
                        .map(Map.Entry::getKey).collect(Collectors.toSet());
                if (!excluded.isEmpty()) filters.add(cb.not(root.get("id").in(excluded)));
            } else {
                Set<Long> included = demoTracking.entrySet().stream()
                        .filter(entry -> entry.getValue() == status).map(Map.Entry::getKey).collect(Collectors.toSet());
                filters.add(included.isEmpty() ? cb.disjunction() : root.get("id").in(included));
            }
            if (level != null) filters.add(cb.equal(root.get("level"), level));
            if (workplace != null) filters.add(cb.equal(root.get("workplace"), workplace));
            if (sourceId != null) filters.add(cb.equal(root.get("sourceId"), sourceId));
            for (String term : q.trim().toLowerCase(Locale.ROOT).split("\\s+")) {
                if (term.isBlank()) continue;
                String value = "%" + term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                filters.add(cb.or(cb.like(cb.lower(root.get("title")), value, '\\'),
                    cb.like(cb.lower(root.get("company")), value, '\\'),
                    cb.like(cb.lower(root.get("description")), value, '\\'),
                    cb.like(cb.lower(root.get("location")), value, '\\')));
            }
            return cb.and(filters.toArray(Predicate[]::new));
        };
        Sort order = Sort.by(Sort.Order.desc("firstSeenAt"), Sort.Order.asc("id"));
        if ("priority".equals(sort)) order = Sort.by(Sort.Order.desc("priorityScore")).and(order);
        if ((long) page * size > Integer.MAX_VALUE) {
            long total = jobs.count(spec);
            return new JobPage(List.of(), total, (int) Math.min(Integer.MAX_VALUE, (total + size - 1) / size), page, size);
        }
        var results = jobs.findAll(spec, PageRequest.of(page, size, order));
        Map<Long,String> names = sourceNames();
        List<JobView> items = results.stream()
            .map(job -> JobView.from(job, names.getOrDefault(job.getSourceId(), job.getCompany())))
            .map(view -> demoTracking == null ? view : view.withStatus(demoTracking.getOrDefault(view.id(), TrackingStatus.NEW)))
            .toList();
        return new JobPage(items, results.getTotalElements(), results.getTotalPages(), page, size);
    }

    @Transactional(readOnly=true)
    public JobView get(Long id) {
        Job job = find(id);
        String sourceName = sources.findById(job.getSourceId()).map(source -> source.getName()).orElse(job.getCompany());
        return JobView.from(job, sourceName);
    }
    @Transactional(readOnly=true)
    public JobView get(Long id, TrackingStatus demoStatus) {
        return get(id).withStatus(demoStatus);
    }
    @Transactional
    public JobView status(Long id, TrackingStatus status) {
        Job job = find(id); job.setStatus(status);
        return JobView.from(job, sources.findById(job.getSourceId()).map(source -> source.getName()).orElse(job.getCompany()));
    }
    private Job find(Long id) { return jobs.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found.")); }
    private Map<Long,String> sourceNames() {
        return sources.findAll().stream().collect(Collectors.toMap(source -> source.getId(), source -> source.getName()));
    }
    public record JobPage(List<JobView> items, long totalElements, int totalPages, int page, int size) {}
}

