package com.jobtrail.refresh;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="refresh_runs")
public class RefreshRun {
    @Id @Column(length=36) private String id;
    @Column(nullable=false,length=30) private String status;
    @Column(nullable=false) private Instant startedAt;
    private Instant finishedAt;
    @Column(nullable=false,columnDefinition="TEXT") private String resultsJson;
    public RefreshRun() {}
    public String getId(){return id;} public void setId(String id){this.id=id;}
    public String getStatus(){return status;} public void setStatus(String status){this.status=status;}
    public Instant getStartedAt(){return startedAt;} public void setStartedAt(Instant value){this.startedAt=value;}
    public Instant getFinishedAt(){return finishedAt;} public void setFinishedAt(Instant value){this.finishedAt=value;}
    public String getResultsJson(){return resultsJson;} public void setResultsJson(String value){this.resultsJson=value;}
}

