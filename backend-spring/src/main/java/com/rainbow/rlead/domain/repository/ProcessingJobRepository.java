package com.rainbow.rlead.domain.repository;

import com.rainbow.rlead.domain.model.JobStatus;
import com.rainbow.rlead.domain.model.ProcessingJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 처리 작업 리포지토리.
 */
@Repository
public interface ProcessingJobRepository extends JpaRepository<ProcessingJob, Long> {

    /**
     * 상태별 작업 조회.
     */
    List<ProcessingJob> findByStatus(JobStatus status);

    /**
     * 리드 ID로 작업 조회.
     */
    List<ProcessingJob> findByLeadId(Long leadId);

    /**
     * 리드 ID로 작업 삭제.
     */
    void deleteByLeadId(Long leadId);
}
