package com.jobtrail.refresh;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtrail.sources.*;
import com.jobtrail.sources.adapters.JobBoardAdapter;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RefreshCoordinator {
    private static final Logger log = LoggerFactory.getLogger(RefreshCoordinator.class);
    private final JobSourceRepository sources;
    private final RefreshRunRepository runs;
    private final SnapshotService snapshots;
    private final ObjectMapper mapper;
    private final Map<SourceProvider, JobBoardAdapter> adapters;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r,"jobtrail-refresh"); thread.setDaemon(true); return thread;
    });
    private volatile String activeRun;
    @Value("${jobtrail.collection.startup:false}") private boolean startup;
    @Value("${jobtrail.collection.scheduled:false}") private boolean scheduled;

    public RefreshCoordinator(JobSourceRepository sources, RefreshRunRepository runs, SnapshotService snapshots,
                              ObjectMapper mapper, List<JobBoardAdapter> adapters) {
        this.sources=sources; this.runs=runs; this.snapshots=snapshots; this.mapper=mapper;
        this.adapters=adapters.stream().collect(Collectors.toMap(JobBoardAdapter::provider, adapter -> adapter));
    }
    @EventListener(ApplicationReadyEvent.class)
    public void ready() {
        runs.findByStatus("RUNNING").forEach(run -> {
            run.setStatus("FAILED"); run.setFinishedAt(Instant.now()); runs.save(run);
        });
        if (startup) start();
    }
    @Scheduled(fixedDelayString="${jobtrail.collection.delay-ms:21600000}", initialDelayString="${jobtrail.collection.delay-ms:21600000}")
    public void scheduledRefresh() { if (scheduled) { try {start();} catch(ResponseStatusException ignored) { } } }

    public synchronized RunView start() {
        if (activeRun != null) return get(activeRun);
        Optional<RefreshRun> latest = runs.findFirstByOrderByStartedAtDesc();
        if (latest.isPresent() && latest.get().getStartedAt().isAfter(Instant.now().minusSeconds(30)))
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Wait 30 seconds between board refreshes.");
        List<JobSource> enabled = sources.findByEnabledTrueOrderByNameAsc();
        if (enabled.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enable an employer board first.");
        RefreshRun run = new RefreshRun();
        run.setId(UUID.randomUUID().toString()); run.setStatus("RUNNING");
        run.setStartedAt(Instant.now()); run.setResultsJson("[]"); runs.save(run);
        activeRun=run.getId();
        executor.submit(() -> collect(run.getId(), enabled));
        return view(run);
    }
    private void collect(String id, List<JobSource> enabled) {
        List<SourceResult> results=new ArrayList<>();
        try {
            for (JobSource source : enabled) {
                try {
                    JobBoardAdapter adapter=adapters.get(source.getProvider());
                    if (adapter==null) throw new IllegalStateException("Source provider unavailable.");
                    int count=snapshots.apply(source.getId(), adapter.fetch(source));
                    results.add(new SourceResult(source.getId(),source.getName(),"SUCCESS",count,null));
                } catch (Exception ex) {
                    String message=ex instanceof com.jobtrail.sources.adapters.JobSourceFetchException ? ex.getMessage() : "Collection failed validation. Previous listings were retained.";
                    message=message.substring(0,Math.min(500,message.length()));
                    snapshots.failure(source.getId(),message);
                    results.add(new SourceResult(source.getId(),source.getName(),"FAILED",0,message));
                    log.warn("Board collection failed for source {}: {}",source.getId(),ex.getClass().getSimpleName());
                }
                saveProgress(id,results,false);
            }
            saveProgress(id,results,true);
        } catch (Exception ex) {
            log.error("Refresh failed for run {}",id,ex);
            runs.findById(id).ifPresent(run -> {run.setStatus("FAILED");run.setFinishedAt(Instant.now());runs.save(run);});
        } finally {activeRun=null;}
    }
    private void saveProgress(String id,List<SourceResult> results,boolean finished) throws Exception {
        RefreshRun run=runs.findById(id).orElseThrow();
        run.setResultsJson(mapper.writeValueAsString(results));
        if (finished) {
            long failed=results.stream().filter(result -> result.status().equals("FAILED")).count();
            run.setStatus(failed==0 ? "COMPLETED" : failed==results.size() ? "FAILED" : "PARTIAL");
            run.setFinishedAt(Instant.now());
        }
        runs.save(run);
    }
    public RunView get(String id) {
        return view(runs.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Refresh run not found.")));
    }
    public Optional<RunView> latest() {return runs.findFirstByOrderByStartedAtDesc().map(this::view);}
    private RunView view(RefreshRun run) {
        try {return new RunView(run.getId(),run.getStatus(),run.getStartedAt(),run.getFinishedAt(),
                mapper.readValue(run.getResultsJson(),new TypeReference<List<SourceResult>>(){}));}
        catch(Exception ex){throw new IllegalStateException("Invalid saved refresh state.",ex);}
    }
    @PreDestroy public void close(){executor.shutdownNow();}
    public record SourceResult(Long sourceId,String name,String status,int jobCount,String error){}
    public record RunView(String id,String status,Instant startedAt,Instant finishedAt,List<SourceResult> sources){}
}

