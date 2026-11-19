package com.rainbow.rlead.domain.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 백그라운드 작업 모델 - 처리 작업 큐.
 */
@Entity
@Table(name = "processing_jobs", indexes = {
    @Index(name = "idx_job_status", columnList = "status"),
    @Index(name = "idx_job_lead_id", columnList = "lead_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessingJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 작업 정보
    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", length = 50, nullable = false)
    @JsonProperty("job_type")
    private JobType jobType;

    @Enumerated(EnumType.STRING)
    @Column(length = 50, nullable = false)
    @Builder.Default
    private JobStatus status = JobStatus.PENDING;

    // 연관 리드 ID
    @Column(name = "lead_id")
    @JsonProperty("lead_id")
    private Long leadId;

    // 재시도
    @Column(name = "retry_count", nullable = false)
    @JsonProperty("retry_count")
    @Builder.Default
    private Integer retryCount = 0;

    @Column(name = "max_retries", nullable = false)
    @JsonProperty("max_retries")
    @Builder.Default
    private Integer maxRetries = 3;

    // 처리 시간
    @Column(name = "started_at")
    @JsonProperty("started_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSS")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    @JsonProperty("completed_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSS")
    private LocalDateTime completedAt;

    // 에러 정보
    @Column(name = "error_message", columnDefinition = "TEXT")
    @JsonProperty("error_message")
    private String errorMessage;

    // 타임스탬프
    @Column(name = "created_at", nullable = false, updatable = false)
    @JsonProperty("created_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSS")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @JsonProperty("updated_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSS")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
