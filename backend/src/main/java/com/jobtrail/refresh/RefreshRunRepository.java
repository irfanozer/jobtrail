package com.jobtrail.refresh;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface RefreshRunRepository extends JpaRepository<RefreshRun,String> {
    Optional<RefreshRun> findFirstByOrderByStartedAtDesc();
    List<RefreshRun> findByStatus(String status);
}

