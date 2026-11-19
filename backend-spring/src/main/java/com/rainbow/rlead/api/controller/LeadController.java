package com.rainbow.rlead.api.controller;

import com.rainbow.rlead.api.dto.*;
import com.rainbow.rlead.domain.model.Lead;
import com.rainbow.rlead.domain.model.LeadStatus;
import com.rainbow.rlead.service.LeadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 리드 관리 API 컨트롤러.
 */
@RestController
@RequestMapping("/api/leads")
@RequiredArgsConstructor
public class LeadController {

    private final LeadService leadService;

    /**
     * 담당자 확인이 필요한 리드 목록을 조회합니다.
     */
    @GetMapping("/pending")
    public ResponseEntity<List<LeadResponse>> getPendingLeads(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset
    ) {
        List<Lead> leads = leadService.getPendingLeads(limit, offset);
        List<LeadResponse> response = leads.stream()
                .map(LeadResponse::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    /**
     * 특정 리드의 상세 정보를 조회합니다.
     */
    @GetMapping("/{leadId}")
    public ResponseEntity<LeadResponse> getLead(@PathVariable Long leadId) {
        Lead lead = leadService.getLeadById(leadId);
        return ResponseEntity.ok(LeadResponse.from(lead));
    }

    /**
     * 리드 정보를 수정합니다 (담당자).
     */
    @PatchMapping("/{leadId}")
    public ResponseEntity<LeadResponse> updateLead(
            @PathVariable Long leadId,
            @RequestBody LeadUpdateRequest request
    ) {
        Lead lead = leadService.updateLead(leadId, request);
        return ResponseEntity.ok(LeadResponse.from(lead));
    }

    /**
     * 리드를 승인 또는 거부합니다.
     */
    @PostMapping("/{leadId}/approve")
    public ResponseEntity<LeadResponse> approveLead(
            @PathVariable Long leadId,
            @RequestBody LeadApprovalRequest request
    ) {
        Lead lead = leadService.approveLead(leadId, request);
        return ResponseEntity.ok(LeadResponse.from(lead));
    }

    /**
     * 모든 리드 목록을 조회합니다 (필터링 가능).
     */
    @GetMapping({"", "/"})
    public ResponseEntity<List<LeadResponse>> getAllLeads(
            @RequestParam(required = false) LeadStatus status,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "0") int offset
    ) {
        List<Lead> leads = leadService.getAllLeads(status, limit, offset);
        List<LeadResponse> response = leads.stream()
                .map(LeadResponse::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    /**
     * 처리 대기 중인 작업 상태를 조회합니다.
     */
    @GetMapping("/queue/status")
    public ResponseEntity<Map<String, Object>> getQueueStatus() {
        Map<String, Object> status = leadService.getQueueStatus();
        return ResponseEntity.ok(status);
    }

    /**
     * 리드를 삭제합니다 (처리 중이어도 삭제 가능).
     */
    @DeleteMapping("/{leadId}")
    public ResponseEntity<Map<String, String>> deleteLead(@PathVariable Long leadId) {
        leadService.deleteLead(leadId);
        return ResponseEntity.ok(Map.of("message", "Lead " + leadId + " deleted successfully"));
    }

    /**
     * 여러 리드를 일괄 재추출합니다.
     */
    @PostMapping("/bulk-reextract")
    public ResponseEntity<BulkResponse> bulkReextractLeads(@RequestBody BulkRequest request) {
        BulkResponse response = leadService.bulkReextract(request.getLeadIds());
        return ResponseEntity.ok(response);
    }

    /**
     * 여러 리드를 일괄 삭제합니다.
     */
    @PostMapping("/bulk-delete")
    public ResponseEntity<BulkResponse> bulkDeleteLeads(@RequestBody BulkRequest request) {
        BulkResponse response = leadService.bulkDelete(request.getLeadIds());
        return ResponseEntity.ok(response);
    }
}
