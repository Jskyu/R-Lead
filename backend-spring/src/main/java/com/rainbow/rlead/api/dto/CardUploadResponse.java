package com.rainbow.rlead.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 명함 업로드 응답 DTO.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardUploadResponse {

    @JsonProperty("lead_id")
    private Long leadId;

    private String message;

    private Boolean duplicate;

    @JsonProperty("duplicate_of_lead_id")
    private Long duplicateOfLeadId;
}
