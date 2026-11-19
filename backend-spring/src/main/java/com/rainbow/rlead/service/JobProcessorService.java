package com.rainbow.rlead.service;

import com.rainbow.rlead.domain.model.JobStatus;
import com.rainbow.rlead.domain.model.JobType;
import com.rainbow.rlead.domain.model.ProcessingJob;
import com.rainbow.rlead.domain.repository.ProcessingJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 백그라운드 작업 처리 서비스.
 * 일정 간격으로 대기 중인 작업을 처리합니다.
 */
@Service
@RequiredArgsConstructor
public class JobProcessorService {

    private final ProcessingJobRepository jobRepository;
    private final ExtractionService extractionService;

    /**
     * 서버가 비정상 종료되어 PROCESSING 으로 남은 작업을 PENDING 으로 되돌립니다.
     */
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void recoverStuckJobs() {
        List<ProcessingJob> stuck = jobRepository.findByStatus(JobStatus.PROCESSING);
        for (ProcessingJob job : stuck) {
            job.setStatus(JobStatus.PENDING);
            jobRepository.save(job);
        }
        if (!stuck.isEmpty()) {
            System.out.println("♻️  Recovered " + stuck.size() + " stuck jobs");
        }
    }

    /**
     * 5초마다 대기 중인 작업을 확인하고 처리합니다.
     */
    @Scheduled(fixedDelay = 5000, initialDelay = 10000)
    public void processPendingJobs() {
        List<ProcessingJob> pendingJobs = jobRepository.findByStatus(JobStatus.PENDING);

        if (!pendingJobs.isEmpty()) {
            System.out.println("📋 Found " + pendingJobs.size() + " pending jobs");
        }

        for (ProcessingJob job : pendingJobs) {
            // 재시도 대기 중인 작업은 (재시도 횟수 × 20초) 지나야 다시 처리한다.
            if (job.getRetryCount() != null && job.getRetryCount() > 0 && job.getStartedAt() != null
                    && job.getStartedAt().plusSeconds(20L * job.getRetryCount()).isAfter(java.time.LocalDateTime.now())) {
                continue;
            }
            processJobAsync(job);
        }
    }

    /**
     * 작업을 비동기로 처리합니다.
     */
    @Async
    public void processJobAsync(ProcessingJob job) {
        try {
            System.out.println("🔄 Processing job " + job.getId() + " (type: " + job.getJobType() + ", lead: " + job.getLeadId() + ")");

            switch (job.getJobType()) {
                case EXTRACT_CARD:
                    extractionService.processExtractionJob(job);
                    break;

                case REGISTER_CRM:
                    // TODO: CRM 등록 로직
                    System.out.println("📋 CRM registration not implemented yet");
                    job.setStatus(JobStatus.COMPLETED);
                    jobRepository.save(job);
                    break;

                case SEND_EMAIL:
                    // TODO: 이메일 발송 로직
                    System.out.println("📧 Email sending not implemented yet");
                    job.setStatus(JobStatus.COMPLETED);
                    jobRepository.save(job);
                    break;

                default:
                    System.err.println("❌ Unknown job type: " + job.getJobType());
                    job.setStatus(JobStatus.FAILED);
                    job.setErrorMessage("Unknown job type");
                    jobRepository.save(job);
            }

        } catch (Exception e) {
            System.err.println("❌ Job processing failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
