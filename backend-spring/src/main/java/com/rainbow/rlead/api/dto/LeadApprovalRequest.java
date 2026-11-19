package com.rainbow.rlead.api.dto;

import lombok.Data;

/**
 * 리드 승인/거부 요청 DTO.
 */
@Data
public class LeadApprovalRequest {

    private Boolean approved;
    private LeadUpdateRequest updates;
}
