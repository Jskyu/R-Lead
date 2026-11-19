package com.rainbow.rlead.service;

import com.rainbow.rlead.api.LeadNotFoundException;
import com.rainbow.rlead.api.dto.BulkResponse;
import com.rainbow.rlead.api.dto.LeadApprovalRequest;
import com.rainbow.rlead.api.dto.LeadUpdateRequest;
import com.rainbow.rlead.domain.model.*;
import com.rainbow.rlead.domain.repository.LeadRepository;
import com.rainbow.rlead.domain.repository.ProcessingJobRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 리드 서비스.
 */
@Service
@RequiredArgsConstructor
public class LeadService {

    private final LeadRepository leadRepository;
    private final ProcessingJobRepository jobRepository;
    private final StorageService storageService;
    private final ValidationService validationService;

    /**
     * 담당자 확인이 필요한 리드 목록을 조회합니다.
     */
    public List<Lead> getPendingLeads(int limit, int offset) {
        List<Lead> leads = leadRepository.findByStatusOrderByCreatedAtDesc(LeadStatus.EXTRACTED);

        // 각 리드의 이미지에 대해 임시 URL 생성
        leads.forEach(lead -> {
            if (lead.getCardImageKey() != null) {
                try {
                    String presignedUrl = storageService.generatePresignedUrl(lead.getCardImageKey(), 3600);
                    lead.setCardImageUrl(presignedUrl);
                } catch (Exception e) {
                    System.err.println("Failed to generate presigned URL: " + e.getMessage());
                }
            }
        });

        return leads.stream()
                .skip(offset)
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * 특정 리드의 상세 정보를 조회합니다.
     */
    public Lead getLeadById(Long leadId) {
        Lead lead = leadRepository.findById(leadId)
                .orElseThrow(() -> new LeadNotFoundException(leadId));

        // 임시 URL 생성
        if (lead.getCardImageKey() != null) {
            try {
                String presignedUrl = storageService.generatePresignedUrl(lead.getCardImageKey(), 3600);
                lead.setCardImageUrl(presignedUrl);
            } catch (Exception e) {
                System.err.println("Failed to generate presigned URL: " + e.getMessage());
            }
        }

        return lead;
    }

    /**
     * 리드 정보를 수정합니다.
     */
    @Transactional
    public Lead updateLead(Long leadId, LeadUpdateRequest request) {
        Lead lead = leadRepository.findById(leadId)
                .orElseThrow(() -> new LeadNotFoundException(leadId));

        // 수정된 필드 추적
        List<String> modifiedFields = lead.getModifiedFields() != null
                ? new ArrayList<>(lead.getModifiedFields())
                : new ArrayList<>();

        // 업데이트
        if (request.getName() != null && !request.getName().equals(lead.getName())) {
            lead.setName(request.getName());
            if (!modifiedFields.contains("name")) modifiedFields.add("name");
        }
        if (request.getCompany() != null && !request.getCompany().equals(lead.getCompany())) {
            lead.setCompany(request.getCompany());
            if (!modifiedFields.contains("company")) modifiedFields.add("company");
        }
        if (request.getDepartment() != null && !request.getDepartment().equals(lead.getDepartment())) {
            lead.setDepartment(request.getDepartment());
            if (!modifiedFields.contains("department")) modifiedFields.add("department");
        }
        if (request.getJobTitle() != null && !request.getJobTitle().equals(lead.getJobTitle())) {
            lead.setJobTitle(request.getJobTitle());
            if (!modifiedFields.contains("job_title")) modifiedFields.add("job_title");
        }
        if (request.getEmail() != null && !request.getEmail().equals(lead.getEmail())) {
            lead.setEmail(request.getEmail());
            if (!modifiedFields.contains("email")) modifiedFields.add("email");
        }
        if (request.getPhone() != null && !request.getPhone().equals(lead.getPhone())) {
            lead.setPhone(request.getPhone());
            if (!modifiedFields.contains("phone")) modifiedFields.add("phone");
        }
        if (request.getAddress() != null && !request.getAddress().equals(lead.getAddress())) {
            lead.setAddress(request.getAddress());
            if (!modifiedFields.contains("address")) modifiedFields.add("address");
        }
        if (request.getWebsite() != null && !request.getWebsite().equals(lead.getWebsite())) {
            lead.setWebsite(request.getWebsite());
            if (!modifiedFields.contains("website")) modifiedFields.add("website");
        }
        if (request.getInterestedProducts() != null) {
            lead.setInterestedProducts(request.getInterestedProducts());
        }
        if (request.getMarketingConsent() != null) {
            lead.setMarketingConsent(request.getMarketingConsent());
        }

        lead.setModifiedFields(modifiedFields);
        // 담당자가 값을 고쳤으면 현재 값 기준으로 규칙 검증과 신뢰도를 다시 계산한다.
        // (고치기 전의 오류 표시가 남아 있으면 확인 화면이 오해를 준다)
        if (lead.getAiExtractedData() != null) {
            revalidate(lead);
        }
        return leadRepository.save(lead);
    }

    private void revalidate(Lead lead) {
        Map<String, String> values = new HashMap<>();
        values.put("name", lead.getName());
        values.put("company", lead.getCompany());
        values.put("department", lead.getDepartment());
        values.put("job_title", lead.getJobTitle());
        values.put("email", lead.getEmail());
        values.put("phone", lead.getPhone());
        values.put("address", lead.getAddress());
        values.put("website", lead.getWebsite());

        List<ValidationIssue> issues = validationService.validateExtractedData(values, List.of("name", "company"));
        lead.setFieldConfidences(validationService.calculateAllConfidences(values, issues));

        List<Map<String, Object>> issueMaps = new ArrayList<>();
        for (ValidationIssue issue : issues) {
            Map<String, Object> m = new HashMap<>();
            m.put("field", issue.getField());
            m.put("issue_type", issue.getIssueType());
            m.put("message", issue.getMessage());
            issueMaps.add(m);
        }
        lead.setValidationIssues(issueMaps);
    }

    /**
     * 리드를 승인 또는 거부합니다.
     */
    @Transactional
    public Lead approveLead(Long leadId, LeadApprovalRequest request) {
        Lead lead = leadRepository.findById(leadId)
                .orElseThrow(() -> new LeadNotFoundException(leadId));

        // 업데이트 적용 (있는 경우)
        if (request.getUpdates() != null) {
            lead = updateLead(leadId, request.getUpdates());
        }

        // 상태 변경
        if (Boolean.TRUE.equals(request.getApproved())) {
            lead.setStatus(LeadStatus.APPROVED);

            // CRM 등록 작업 생성
            ProcessingJob crmJob = ProcessingJob.builder()
                    .jobType(JobType.REGISTER_CRM)
                    .leadId(lead.getId())
                    .build();
            jobRepository.save(crmJob);
            System.out.println("📋 CRM registration job created for lead " + lead.getId());
        } else {
            lead.setStatus(LeadStatus.REJECTED);
        }

        return leadRepository.save(lead);
    }

    /**
     * 모든 리드 목록을 조회합니다.
     */
    public List<Lead> getAllLeads(LeadStatus status, int limit, int offset) {
        List<Lead> leads;

        if (status != null) {
            leads = leadRepository.findByStatusOrderByCreatedAtDesc(status);
        } else {
            leads = leadRepository.findAllByOrderByCreatedAtDesc();
        }

        // 각 리드의 이미지에 대해 임시 URL 생성
        leads.forEach(lead -> {
            if (lead.getCardImageKey() != null) {
                try {
                    String presignedUrl = storageService.generatePresignedUrl(lead.getCardImageKey(), 3600);
                    lead.setCardImageUrl(presignedUrl);
                } catch (Exception e) {
                    System.err.println("Failed to generate presigned URL: " + e.getMessage());
                }
            }
        });

        return leads.stream()
                .skip(offset)
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * 처리 대기 중인 작업 상태를 조회합니다.
     */
    public Map<String, Object> getQueueStatus() {
        List<ProcessingJob> pendingJobs = jobRepository.findByStatus(JobStatus.PENDING);
        List<ProcessingJob> processingJobs = jobRepository.findByStatus(JobStatus.PROCESSING);
        List<ProcessingJob> failedJobs = jobRepository.findByStatus(JobStatus.FAILED);

        // 대기 중인 리드 정보 가져오기
        List<Long> pendingLeadIds = pendingJobs.stream()
                .map(ProcessingJob::getLeadId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        List<Long> processingLeadIds = processingJobs.stream()
                .map(ProcessingJob::getLeadId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        List<Map<String, Object>> pendingLeads = new ArrayList<>();
        if (!pendingLeadIds.isEmpty()) {
            List<Lead> leads = leadRepository.findAllById(pendingLeadIds);
            pendingLeads = leads.stream()
                    .map(lead -> Map.of(
                            "id", (Object) lead.getId(),
                            "event_name", lead.getEventName() != null ? lead.getEventName() : "",
                            "created_at", lead.getCreatedAt().toString()
                    ))
                    .collect(Collectors.toList());
        }

        List<Map<String, Object>> processingLeads = new ArrayList<>();
        if (!processingLeadIds.isEmpty()) {
            List<Lead> leads = leadRepository.findAllById(processingLeadIds);
            processingLeads = leads.stream()
                    .map(lead -> Map.of(
                            "id", (Object) lead.getId(),
                            "event_name", lead.getEventName() != null ? lead.getEventName() : "",
                            "created_at", lead.getCreatedAt().toString()
                    ))
                    .collect(Collectors.toList());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("pending_count", pendingJobs.size());
        result.put("processing_count", processingJobs.size());
        result.put("failed_count", failedJobs.size());

        // 실패한 추출 작업의 사유 (리드별 가장 최근 것). 사진/개인정보는 포함하지 않는다.
        Map<Long, Map<String, Object>> failedByLead = new LinkedHashMap<>();
        failedJobs.stream()
                .filter(j -> j.getLeadId() != null && j.getJobType() == JobType.EXTRACT_CARD)
                .sorted(Comparator.comparing(ProcessingJob::getId))
                .forEach(j -> {
                    String msg = j.getErrorMessage() == null ? "" : j.getErrorMessage();
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", j.getLeadId());
                    m.put("error", msg.length() > 200 ? msg.substring(0, 200) + "…" : msg);
                    failedByLead.put(j.getLeadId(), m);
                });
        // 이미 재추출에 성공했거나 삭제된 리드의 과거 실패 이력은 제외하고, 지금도 실패 상태(UPLOADED)인 리드만 센다.
        Set<Long> stillUploaded = leadRepository.findAllById(failedByLead.keySet()).stream()
                .filter(l -> l.getStatus() == LeadStatus.UPLOADED)
                .map(Lead::getId)
                .collect(Collectors.toSet());
        failedByLead.keySet().retainAll(stillUploaded);
        result.put("failed_count", failedByLead.size());
        result.put("failed_leads", new ArrayList<>(failedByLead.values()));
        result.put("pending_leads", pendingLeads);
        result.put("processing_leads", processingLeads);

        return result;
    }

    /**
     * 리드를 삭제합니다.
     */
    @Transactional
    public void deleteLead(Long leadId) {
        Lead lead = leadRepository.findById(leadId)
                .orElseThrow(() -> new LeadNotFoundException(leadId));

        // S3에서 이미지 삭제
        if (lead.getCardImageKey() != null
                && leadRepository.countByCardImageKeyAndIdNot(lead.getCardImageKey(), lead.getId()) == 0) {
            // 한 사진을 여러 리드가 공유하므로, 마지막 리드를 지울 때만 사진을 삭제한다.
            try {
                storageService.deleteImage(lead.getCardImageKey());
                System.out.println("🗑️ Deleted image from S3: " + lead.getCardImageKey());
            } catch (Exception e) {
                System.err.println("⚠️ Failed to delete image from S3: " + e.getMessage());
            }
        }

        // 관련 처리 작업 삭제
        jobRepository.deleteByLeadId(leadId);

        // 리드 삭제
        leadRepository.delete(lead);
    }

    /**
     * 여러 리드를 일괄 재추출합니다.
     */
    @Transactional
    public BulkResponse bulkReextract(List<Long> leadIds) {
        if (leadIds == null || leadIds.isEmpty()) {
            throw new IllegalArgumentException("No lead IDs provided");
        }

        List<Lead> leads = leadRepository.findAllById(leadIds);
        if (leads.isEmpty()) {
            throw new LeadNotFoundException(null);
        }

        int jobCount = 0;
        for (Lead lead : leads) {
            // 상태를 UPLOADED로 되돌림
            lead.setStatus(LeadStatus.UPLOADED);

            // 기존 데이터 초기화
            lead.setAiExtractedData(null);
            lead.setValidationIssues(null);
            lead.setFieldConfidences(null);

            // 재추출 작업 생성
            ProcessingJob job = ProcessingJob.builder()
                    .jobType(JobType.EXTRACT_CARD)
                    .leadId(lead.getId())
                    .build();
            jobRepository.save(job);
            jobCount++;
        }

        leadRepository.saveAll(leads);

        System.out.println("📋 Created " + jobCount + " reextraction jobs");

        return BulkResponse.builder()
                .message("Created " + jobCount + " reextraction jobs")
                .jobCount(jobCount)
                .leadIds(leadIds)
                .build();
    }

    /**
     * 여러 리드를 일괄 삭제합니다.
     */
    @Transactional
    public BulkResponse bulkDelete(List<Long> leadIds) {
        if (leadIds == null || leadIds.isEmpty()) {
            throw new IllegalArgumentException("No lead IDs provided");
        }

        List<Lead> leads = leadRepository.findAllById(leadIds);
        if (leads.isEmpty()) {
            throw new LeadNotFoundException(null);
        }

        int deletedCount = 0;
        for (Lead lead : leads) {
            // S3에서 이미지 삭제
            if (lead.getCardImageKey() != null
                    && leadRepository.countByCardImageKeyAndIdNot(lead.getCardImageKey(), lead.getId()) == 0) {
                try {
                    storageService.deleteImage(lead.getCardImageKey());
                    System.out.println("🗑️ Deleted image from S3: " + lead.getCardImageKey());
                } catch (Exception e) {
                    System.err.println("⚠️ Failed to delete image from S3: " + e.getMessage());
                }
            }

            // 관련 처리 작업 삭제
            jobRepository.deleteByLeadId(lead.getId());

            // 리드 삭제
            leadRepository.delete(lead);
            deletedCount++;
        }

        System.out.println("🗑️ Deleted " + deletedCount + " leads");

        return BulkResponse.builder()
                .message("Deleted " + deletedCount + " leads")
                .deletedCount(deletedCount)
                .leadIds(leadIds)
                .build();
    }
}
