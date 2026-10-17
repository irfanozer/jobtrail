package com.jobtrail.refresh;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/refresh")
public class RefreshController {
    private final RefreshCoordinator coordinator;
    public RefreshController(RefreshCoordinator coordinator){this.coordinator=coordinator;}
    @PostMapping @ResponseStatus(HttpStatus.ACCEPTED)
    public RefreshCoordinator.RunView start(){return coordinator.start();}
    @GetMapping("/latest")
    public ResponseEntity<RefreshCoordinator.RunView> latest(){
        return coordinator.latest().map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }
    @GetMapping("/{id}")
    public RefreshCoordinator.RunView get(@PathVariable String id){return coordinator.get(id);}
}

