package com.jobtrail.sources;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface JobSourceRepository extends JpaRepository<JobSource, Long> {
    List<JobSource> findByEnabledTrueOrderByNameAsc();
    List<JobSource> findAllByOrderByNameAsc();
    boolean existsByProviderAndBoardKey(SourceProvider provider, String boardKey);
}

