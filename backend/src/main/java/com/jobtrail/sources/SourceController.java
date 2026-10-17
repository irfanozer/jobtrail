package com.jobtrail.sources;

import com.jobtrail.demo.DemoWorkspace;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/sources")
public class SourceController {
    private final JobSourceRepository repository;
    public SourceController(JobSourceRepository repository) { this.repository = repository; }

    @GetMapping
    public List<SourceView> list(Authentication authentication) {
        return repository.findAllByOrderByNameAsc().stream().map(SourceView::from)
                .map(source -> DemoWorkspace.isDemo(authentication)
                        ? new SourceView(source.id(), source.name(), source.provider(), source.boardKey(), source.enabled(),
                                source.lastSuccessAt(), source.lastAttemptAt(),
                                source.lastError() == null ? null : "This board could not be refreshed. Please try another board.",
                                source.jobCount()) : source)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public SourceView create(@Valid @RequestBody CreateSource request) {
        String key = request.boardKey().trim();
        if (repository.existsByProviderAndBoardKey(request.provider(), key)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That employer board is already tracked.");
        }
        if (repository.count() >= 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At most 100 boards can be tracked.");
        JobSource source = new JobSource();
        source.setName(request.name().trim());
        source.setProvider(request.provider());
        source.setBoardKey(key);
        source.setEnabled(true);
        return SourceView.from(repository.save(source));
    }

    @PatchMapping("/{id}")
    @Transactional
    public SourceView update(@PathVariable Long id, @Valid @RequestBody UpdateSource request) {
        JobSource source = repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        source.setEnabled(request.enabled());
        return SourceView.from(source);
    }

    public record CreateSource(@NotBlank @Size(max=120) String name, @NotNull SourceProvider provider,
                               @NotBlank @Pattern(regexp="[a-zA-Z0-9_-]{1,100}") String boardKey) {}
    public record UpdateSource(@NotNull Boolean enabled) {}
}

